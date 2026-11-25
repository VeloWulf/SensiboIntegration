/**
 *  Sensibo Event Stream
 *
 *  Holds the Sensibo real-time event stream (GET /api/v2/stream, Server-Sent Events) for the Sensibo Integration app.
 *  Created and removed by the app when "Real-time updates" is turned on/off; one per account (Sensibo allows 10).
 *  Events are passed to the app, which updates its cache and the pod devices:
 *    hello        -> stream is open: app does one full refresh and slows routine polling to 15+ minutes
 *    measurements -> app updates that pod's temperature/humidity (no API call)
 *    changed      -> app re-reads that pod (acState / connectionStatus / anything)
 *    resync       -> Sensibo is closing the stream: reconnect
 *  Reconnects with an increasing, randomized delay (max 5 minutes) and reconnects if the stream goes silent.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Date		Comments
 *  2026-11-25	nh.schottfam - Initial version; fixed reconnect loop from Hubitat's asynchronous close; raw stream data option;
 *				a STOP while events keep arriving isn't a drop; connection flags kept out of `state` (concurrent writes)
 *
 */

import groovy.transform.Field

@Field static final String devVersionFLD	= '1.0.0'
@Field static final String sSTREAMURL		= 'https://home.sensibo.com/api/v2/stream'
@Field static final Integer iDEFHEARTBEAT	= 120		// seconds, until "hello" says otherwise
@Field static final Integer iMAXDELAY		= 300		// reconnect back-off cap (Sensibo: max 5 minutes)

metadata{
	definition(name: "Sensibo Event Stream", namespace: "velowulf", author: "nh.schottfam"){
		capability "Initialize"		// initialize() runs at hub start-up, so the stream reconnects after a reboot

		attribute "streamStatus", "string"	// connected, connecting, disconnected
		attribute "lastEvent", "string"

		command "connect"
		command "disconnect"
	}
	preferences{
		input "logInfo", "bool", title: "Show Info Logs?", required: false, defaultValue: true
		input "logDebug", "bool", title: "Show Debug Logs?", required: false, defaultValue: false
		input "rawData", "bool", title: "Raw stream data", required: false, defaultValue: false,
			description: "Pass the stream through unprocessed by Hubitat (try if the stream keeps stopping). Takes effect on the next connect."
	}
}

// Connection flags change on every event and on Hubitat's status callbacks, which run at the same time; the
// device's `state` is saved per execution, so they overwrote each other. They live in this in-memory map instead
// (per device; lost on driver code save / reboot, which reconnect anyway). wantConnected is kept as a device data
// value so it survives those.
@Field static java.util.concurrent.ConcurrentHashMap<String,Map> stFLD = new java.util.concurrent.ConcurrentHashMap()

private Map S(){
	String k = device.id.toString()
	Map m = stFLD[k]
	if(m == null){
		m = new java.util.concurrent.ConcurrentHashMap()
		stFLD[k] = m
	}
	return m
}

private Boolean wantConnected(){ return device.getDataValue('wantConnected') == 'true' }

void installed(){ initialize() }

void updated(){ initialize() }

void initialize(){
	if(parent?.streamEnabled()) connect()
	else disconnect()
}

// Hubitat closes a stream asynchronously: closing and immediately reconnecting let the late close stop the NEW
// stream. So only an open stream is closed, the new one is opened a few seconds later, and the STOP from our own
// close is ignored.
void connect(){
	device.updateDataValue('wantConnected', 'true')
	unschedule('connect')
	unschedule('openStream')
	unschedule('checkAfterStop')
	S().remove('stopAt')
	if(S().streamOpen){
		closeStream()
		runIn(3, 'openStream')
	}else{
		openStream()
	}
}

private void closeStream(){
	S().streamOpen = false
	S().selfCloseAt = now()	// the STOP this causes isn't a drop
	try{ interfaces.eventStream.close() }catch(ignored){ }
}

void openStream(){
	if(!wantConnected()) return
	String key = (String)parent?.getApiKey()
	if(!key){
		log.warn "Sensibo Event Stream: no API key in the Sensibo Integration app"
		return
	}
	sendEvent(name: "streamStatus", value: "connecting")
	logDebug("connecting to ${sSTREAMURL}")
	Integer hb = (S().heartbeat ?: iDEFHEARTBEAT) as Integer
	interfaces.eventStream.connect(sSTREAMURL, [
		headers: ["X-Api-Key": key, "Accept": "text/event-stream"],
		rawData: settings.rawData == true,
		// Sensibo: if nothing (not even a heartbeat) arrives for two heartbeat intervals, reconnect
		readTimeout: hb * 2 + 30
	])
	resetWatchdog(true)
}

void disconnect(){
	device.updateDataValue('wantConnected', 'false')
	unschedule()
	closeStream()
	sendEvent(name: "streamStatus", value: "disconnected")
	parent?.streamDown(true)	// stopped on purpose: switch back to normal polling now
}

// called by Hubitat with START:, STOP: or ERROR: messages
void eventStreamStatus(String message){
	logDebug("eventStreamStatus: ${message}")
	Map st = S()
	if(message?.startsWith("START")){
		st.streamOpen = true
		st.remove('stopAt')
		unschedule('checkAfterStop')
		sendEvent(name: "streamStatus", value: "connected")
		resetWatchdog(true)
		return
	}
	Long sc = st.selfCloseAt as Long
	if(message?.startsWith("STOP") && sc && now() - sc < 30000L){
		st.remove('selfCloseAt')
		logDebug("stream closed by this driver (reconnect/disconnect); not a drop")
		return
	}
	if(message?.startsWith("ERROR")) log.warn "Sensibo Event Stream: ${message}"
	else logDebug("Hubitat reported ${message}; checking whether events still arrive")
	// Hubitat reports "STOP: EventStream Stopped" while Sensibo's events keep arriving, so a STOP alone isn't
	// treated as a drop: if no data arrives within 45s (Sensibo's retry is 30s), reconnect.
	st.streamOpen = false
	st.stopAt = now()
	runIn(45, 'checkAfterStop')
}

void checkAfterStop(){
	Map st = S()
	if(!st.stopAt || !wantConnected()) return	// data arrived since (or stopped on purpose)
	st.remove('stopAt')
	logInfo("connection ended and no events since; reconnecting")
	sendEvent(name: "streamStatus", value: "disconnected")
	parent?.streamDown()
	scheduleReconnect()
}

// Each event's data is a JSON object. Hubitat normally passes just the data. With "Raw stream data" it passes each
// event both as the bare JSON and as the "data: ..." line, so then only "data:" / "event:" lines are used.
void parse(String message){
	Map st = S()
	Long t = now()
	st.lastEventAt = t
	Long stopAt = st.stopAt as Long
	if(stopAt && t - stopAt > 2000L){	// data well after a STOP (not just in flight): the stream is alive
		st.remove('stopAt')
		st.streamOpen = true
		unschedule('checkAfterStop')
		logDebug("events still arriving after Hubitat's STOP; stream is alive")
		if(device.currentValue("streamStatus") != "connected") sendEvent(name: "streamStatus", value: "connected")
	}
	resetWatchdog(false)
	Boolean raw = settings.rawData == true
	if(raw) logDebug("raw: ${message?.replace('\n', '\\n')}")
	if(!message) return
	String evName = null
	String data = null
	message.readLines().each{ String line ->
		if(line.startsWith(":")) return						// heartbeat / comment
		if(line.startsWith("event:")) evName = line.substring(6).trim()
		else if(line.startsWith("data:")) data = (data ?: "") + line.substring(5).trim()
		else if(!raw && line.trim()) data = (data ?: "") + line.trim()	// "retry:" etc. fail the JSON parse below
	}
	if(!data) return

	Map d
	try{ d = (Map)parseJson(data) }catch(e){
		logDebug("ignoring non-JSON stream data: ${data}")
		return
	}
	// identify the event by name if given, otherwise by its contents
	String type = evName ?: (d.containsKey('heartbeatSeconds') ? 'hello' :
			d.containsKey('fields') ? 'changed' :
			d.containsKey('measurements') ? 'measurements' :
			d.isEmpty() ? 'resync' : 'unknown')
	logDebug("event ${type}: ${data}")
	sendEvent(name: "lastEvent", value: type)

	switch(type){
		case 'hello':
			st.heartbeat = (d.heartbeatSeconds ?: iDEFHEARTBEAT) as Integer
			st.failures = 0
			logInfo("stream open (${d.pods} devices, heartbeat ${st.heartbeat}s)")
			parent?.streamHello()
			break
		case 'measurements':
			if(!d.podId) parent?.streamMeasurements((String)d.id, (Map)d.measurements)	// podId is set for room/window sensors, which have no device here
			break
		case 'changed':
			parent?.streamChanged((String)d.id, (List)d.fields)
			break
		case 'resync':
			logInfo("Sensibo asked to resync; reconnecting")
			runIn(2, 'connect')
			break
		default:
			logDebug("unhandled stream event ${type}")
	}
}

// for the app's status line: when anything last arrived on the stream (ms), or null
Long lastEventAt(){ return S().lastEventAt as Long }

// reconnect after 5, 10, 20... seconds (max 5 minutes) plus a random part, as Sensibo asks
private void scheduleReconnect(){
	if(!wantConnected()) return
	Integer n = ((S().failures ?: 0) as Integer) + 1
	S().failures = n
	Integer base = Math.min(5 * (1 << Math.min(n - 1, 6)), iMAXDELAY)
	Integer delay = base + (Math.round(Math.random() * base / 4) as Integer)
	logInfo("reconnecting in ${delay}s (attempt ${n})")
	runIn(delay, 'connect')
}

// Backstop in case the connection dies without Hubitat saying so: 15 minutes without any event -> reconnect.
// (rssi is in every measurement, so a live stream with several pods is rarely quiet that long.)
// Rescheduled at most once a minute, not on every event.
private void resetWatchdog(Boolean force){
	Long t = now()
	Long last = S().watchdogSetAt as Long
	if(force || !last || t - last > 60000L){
		S().watchdogSetAt = t
		runIn(900, 'watchdog')
	}
}

void watchdog(){
	if(!wantConnected()) return
	logInfo("no events for 15 minutes; reconnecting")
	parent?.streamDown()
	scheduleReconnect()
}

private void logDebug(String msg){ if(settings.logDebug) log.debug "Sensibo Event Stream (v${devVersionFLD}) | ${msg}" }
private void logInfo(String msg){ if(settings.logInfo != false) log.info "Sensibo Event Stream (v${devVersionFLD}) | ${msg}" }
