/**
 *  Sensibo Integration for Hubitat
 *
 *  Copyright 2021 Paul Hutton
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
 *  Date		  Comments
 *  2021-02-15	Forked from Bryan Li's port from ST
 *  2021-02-28	Resolved old namespace issue (thanks benmek)
 *  2024-03-27	Significant updates, support thermostat capabilities
 *  2026-11-25	nh.schottfam - Sensibo API:
 *				  - API key sent in the X-Api-Key header (was in the URL); key no longer written to logs
 *				  - routine polls request only the fields used; capabilities fetched at most every 6 hours
 *				  - HTTP 429 (rate limit) pauses polling with an increasing back-off (5 to 30 minutes)
 *				  - single-value changes use PATCH acStates/{property}, so other settings aren't overwritten
 *				  - refresh interval options 5/10/15/30 minutes (1 minute removed), default 10
 *				Real-time updates (optional): Sensibo event stream via the new "Sensibo Event Stream" driver;
 *				  measurements and changes update devices within seconds, full polls every 15+ minutes
 *				Fixes:
 *				  - Climate React on/off and configuration no longer reported as failed when they succeeded;
 *				    "on" before Climate React is configured reports failure instead of doing nothing
 *				  - pod list cached for 5 minutes (was 300 ms); no crash setting up a pod that isn't found
 *				  - null checks in capabilities and Climate React; async responses checked before use
 *				  - fallback setpoint converted to F; missing measurements left out (were reported as 0)
 *				  - an offline pod is reported as unavailable (was stale data) without a full fetch every poll
 *				  - shared caches thread-safe; deselected or deleted pods dropped from the caches
 *				  - refresh() only replaces the poll schedule; ping() only succeeds on HTTP 200; battery thresholds
 */

//file:noinspection GroovySillyAssignment
//file:noinspection GrDeprecatedAPIUsage
//file:noinspection GroovyDoubleNegation
//file:noinspection GroovyUnusedAssignment
//file:noinspection unused
//file:noinspection SpellCheckingInspection
//file:noinspection GroovyFallthrough
//file:noinspection GrMethodMayBeStatic
//file:noinspection GroovyAssignabilityCheck
//file:noinspection UnnecessaryQualifiedReference


import groovy.json.*
import groovy.transform.Field
import groovy.transform.CompileStatic

definition(
	name: "Sensibo Integration",
	namespace: "velowulf",
	author: "Paul Hutton",
	description: "Connect your Sensibo Pod to Hubitat.",
	menu: "Integrations",
	category: "Green Living",
	iconUrl: "", // empty string - not implemented in Hubitat
	iconX2Url: "", // empty string - not implemented in Hubitat
	iconX3Url: "", // empty string - not implemented in Hubitat
	singleInstance: true)

preferences{
	page(name: "SelectAPIKey", title: "API Key", content: "setAPIKey", nextPage: "deviceList", install: false, uninstall: true)
	page(name: "deviceList", title: "Sensibo", content:"SensiboPodList", install:true, uninstall: true)
	page(name: "timePage")
	page(name: "timePageEvent")
	page((sNM):sPDPC)
}

// Initial variable declarations
static String getServerUrl(){ return 'https://home.sensibo.com' }
static String getChildNamespace(){ return 'velowulf' }
static String getChildTypeName(){ return 'SensiboPod' }
@Field static final String sSTREAMDNI = 'sensibo-event-stream'
@Field static final String sSTREAMTYPE = 'Sensibo Event Stream'
Boolean streamEnabled(){ return (Boolean)settings.useStream == true }
String getApiKey(){ return (String)settings.apiKey }
//static Long getPollRateMillis(){ return 45000L }
//static Long getCapabilitiesRateMillis(){ return 600000L }

static String version(){ return 'Hubitat' }

@Field static final String devVersionFLD = '2.0.0.0'

@Field static final String sNULL         = (String)null
@Field static final String sSPACE        = ' '
@Field static final String sLINEBR       = '<br>'
@Field static final String sTRUE         = 'true'
@Field static final String sFALSE        = 'false'
@Field static final String sCLRRED       = 'red'
@Field static final String sCLRGRY       = 'gray'
@Field static final String sCLRORG       = 'orange'
@Field static final String sAPPJSON      = 'application/json'
@Field static final String sPDPC         = 'pageDumpPCache'

@Field static final String sBLK=''
@Field static final Integer iZ=0
@Field static final Integer i1=1
@Field static final String sNM='name'
@Field static final String sTIT='title'


// Capture APIkey and logging preference
def setAPIKey(){
	logTrace( "setAPIKey()")

	String keystring= getApiKey() ?: ''

	dynamicPage(name: "SelectAPIKey", title: "Enter your API Key", uninstall: true){
		section("API Key"){
			paragraph "Please enter your API Key provided by Sensibo \n\nAvailable at: \nhttps://home.sensibo.com/me/api"
			input(name: "apiKey", title:"", type: "text", required:true, multiple:false, description: "", defaultValue: keystring)
		}
		section("Logging"){
			paragraph "Application Logging Level"
			input "logInfo", "bool", title: "Show Info Logs?",  required: false, defaultValue: true
			input "logWarn", "bool", title: "Show Warning Logs?", required: false, defaultValue: true
			input "logError", "bool", title: "Show Error Logs?",  required: false, defaultValue: true
			input "logDebug", "bool", title: "Show Debug Logs?", description: "Only leave on when required", required: false, defaultValue: false
			input "logTrace", "bool", title: "Show Detailed Logs?", description: "Only Enabled when asked by the developer", required: false, defaultValue: false
		}
		if((Boolean)settings.logTrace){
			section('Debug'){
				href sPDPC,(sTIT):'Dump Caches',description:sBLK
			}
		}
	}
}

def SensiboPodList()
{
	logTrace( "SensiboPodList()")
	//logDebug( "apiKey: "+getApiKey() )

	Map<String,String> stats= getSensiboPodList()
	//logDebug( "device list: $stats")

	dynamicPage(name: "deviceList", title: "Select Your Sensibo Pod", uninstall: true){
		section(""){
			paragraph "Tap below to see the list of Sensibo Pods available in your Sensibo account and select the ones you want to connect to Hubitat."
			input(name: "SelectedSensiboPods", title:"Pods", type: "enum", required:true, multiple:true, description: "Tap to choose", options: stats)
		}

		section("Refresh"){
			input(name:"refreshinminutes", title: "Refresh rates in minutes (Sensibo rate-limits frequent polling)", type: "enum", required:false, multiple: false, options: ["5", "10","15","30"], defaultValue: "10")
			input(name:"useStream", type: "bool", required: false, defaultValue: false,
				title: "Real-time updates (Sensibo event stream)",
				description: "Changes show up within seconds and full polls drop to every 15+ minutes. Needs the 'Sensibo Event Stream' driver installed.")
			if(streamEnabled()) paragraph streamStatusText()
		}

		/*
		section("Receive Pod sensors infos"){
			input "boolnotifevery", "bool",submitOnChange: true, required: false, title: "Receive temperature, humidity and battery level notification every hour?"
			href(name: "toTimePageEvent", page: "timePageEvent", title:"Only during a certain time", require: false)
		}

		section("Alert on sensors (threshold)"){
			input "sendPushNotif", "bool",submitOnChange: true, required: false, title: "Receive alert on Sensibo Pod sensors based on threshold?"
		}

		if(sendPushNotif){
			section("Select the temperature threshold",hideable: true){
				input "minTemperature", "decimal", title: "Min Temperature",required:false
				input "maxTemperature", "decimal", title: "Max Temperature",required:false
			}
			section("Select the humidity threshold",hideable: true){
				input "minHumidity", "decimal", title: "Min Humidity level",required:false
				input "maxHumidity", "decimal", title: "Max Humidity level",required:false
			}

			section("How frequently?"){
				input(name:"days", title: "Only on certain days of the week", type: "enum", required:false, multiple: true, options: ["Monday", "Tuesday", "Wednesday","Thursday","Friday","Saturday","Sunday"])
			}
			section(""){
				href(name: "toTimePage", page: "timePage", title:"Only during a certain time", require: false)
			}
		}
		 */
	}
}
/*
// page def must include a parameter for the params map!
def timePage(){
	dynamicPage(name: "timePage", uninstall: false, install: false, title: "Only during a certain time"){
		section(""){
			input(name: "startTime", title: "Starting at : ", required:false, multiple: false, type:"time",)
			input(name: "endTime", title: "Ending at : ", required:false, multiple: false, type:"time")
		}
	}
}

// page def must include a parameter for the params map!
def timePageEvent(){
	dynamicPage(name: "timePageEvent", uninstall: false, install: false, title: "Only during a certain time"){
		section(""){
			input(name: "startTimeEvent", title: "Starting at : ", required:false, multiple: false, type:"time",)
			input(name: "endTimeEvent", title: "Ending at : ", required:false, multiple: false, type:"time")
		}
	}
}
*/

// settings without the API key, for logging
Map safeSettings(){ return ((Map)settings).findAll{ it.key != 'apiKey' } }

// Function that runs when the app is first installed
def installed(){
	logTrace( "Installed() called with settings: ${safeSettings()}")

	initialize()

	/*def d= getChildDevices() // Can't find that this actually does anything - probably remove (d is returned by the initiatize function and is then overwritten by this definition

	if(boolnotifevery){
		//runEvery1Hour("hournotification")
		schedule("0 0 * * * ?", "hournotification")
	}

	logDebug( "Configured health checkInterval when installed()")
	sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60)*/

	//subscribe(d,"temperatureUnit",eTempUnitHandler)

	/*if(sendPushNotif){
		subscribe(d, "temperature", eTemperatureHandler)
		subscribe(d, "humidity", eHumidityHandler)
	}*/ // Can't find the sendPushNotif variable anywhere - probably remove code
}

// Function that runs when the app is updated
def updated(){
	logTrace( "Updated() called with settings: ${safeSettings()}")

	unschedule() // Remove any scheduled tasks. If method is called without parameters, all schedules will be removed.
	unsubscribe() // Unsubscribe from events sent from a device or all event subscriptions.

	//state.lastTemperaturePush= null
	//state.lastHumidityPush= null

	initialize()

	/*def d= getChildDevices() // Can't find that this actually does anything - probably remove (d is returned by the initiatize function and is then overwritten by this definition

	if(boolnotifevery){
		//runEvery1Hour("hournotification")
		schedule("0 0 * * * ?", "hournotification")
	}

	logDebug( "Configured health checkInterval when installed()")
	sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 2 * 60)*/

	//subscribe(d,"temperatureUnit",eTempUnitHandler)

	/*if(sendPushNotif){
		subscribe(d, "temperature", eTemperatureHandler)
		subscribe(d, "humidity", eHumidityHandler)
	}*/ // Can't find the sendPushNotif variable anywhere - probably remove code
}

// Function that runs when the app is removed (uninstalled)
def uninstalled(){
	// remove child devices
	logTrace "Uninstalled called with settings: ${safeSettings()}"
}


def initialize(){
	logTrace( "initialize() called")
	//logTrace( "key "+ getApiKey())

    // Tidy up states left behind by previous version
    if(state.apikey) { state.remove("apikey") }
    if(state.lastPollCapabilitiesMillis) { state.remove("lastPollCapabilitiesMillis") }
    if(state.lastPollMillis) { state.remove("lastPollMillis") }
    if(state.capabilities) { state.remove("capabilities") }
    if(state.sensibo) { state.remove("sensibo") }

	Map<String,String> podList; podList= null	// fetched once, only if a device needs creating
	List devices= ((List)SelectedSensiboPods).collect{ dni ->
		logDebug("Initializing " + dni)
		def d
		d= getChildDevice(dni) // grab child devices with the current collection entry

		if(!d){ // the return is empty so no device exists in which case add it
			if(podList == null) podList= getSensiboPodList() ?: [:]
			Map.Entry<String,String> name= podList.find( {key,value -> key == dni })
			if(!name){
				logWarn("Pod $dni not found in the Sensibo account (or the API call failed); not creating its device")
				return null
			}

			logDebug( "Pod : ${name.value} - Hub : ${(String)((List)location.hubs)[iZ].name} - Type : " + getChildTypeName() + " - Namespace : " + getChildNamespace())

			d= addChildDevice(getChildNamespace(), getChildTypeName(), dni, ((List)location.hubs)[iZ].id, [
					"label" : "${name.value} Pod",
					"name" : "${name.value} Pod"
				]
			)

			logTrace( "created ${d.displayName} with id $dni")
		}else{ // Tell the user that the device already exists
			logTrace( "found ${d.displayName} with id $dni already exists")
		}

		return d
	}.findAll{ it != null }

	logTrace( "found / created ${devices.size()} Sensibo Pod")

	List delete
	// Delete any that are no longer selected
	List<String> keep= ((List)SelectedSensiboPods ?: []).collect{ it as String }
	delete= getChildDevices().findAll { it.deviceNetworkId != sSTREAMDNI && !keep.contains(it.deviceNetworkId) }	// the stream device isn't a pod

	logTrace( "deleting ${delete.size()} Sensibo")
	delete.each { deleteChildDevice(it.deviceNetworkId) }

	manageStreamDevice()
	resetSchedule()
	//if(advLogsActive()){ runIn(1800, "logsOff") }
	if(advLogsActive()){ runIn(28800, "logsOff") }

	asyncReq('finishRefresh',true) // devices refresh
}

Boolean advLogsActive(){ return ((Boolean)settings.logDebug || (Boolean)settings.logTrace) }
void logsOff(){
	app.updateSetting("logDebug",[value:sFALSE,type:"bool"])
	app.updateSetting("logTrace",[value:sFALSE,type:"bool"])
	log.debug "Disabling debug logs"
}

void resetSchedule(){
	logTrace("resetSchedule()")
	unschedule('refreshDevices')	// replace, not add to, the current schedule
	//Set up schedule for refreshing devices based on app preferences
	String rfmins=(String)settings.refreshinminutes
	logTrace( "refresh() called with rate of " + rfmins + " minutes")
	if(streamUpFLD){
		// with the event stream open, Sensibo asks for a full poll only every 15 minutes or more
		if(rfmins == "30") runEvery30Minutes("refreshDevices")
		else runEvery15Minutes("refreshDevices")
		return
	}
	if(rfmins == "1"){	// no longer offered: Sensibo rate-limits polling
		logWarn("1 minute refresh is no longer supported; using 5 minutes")
		runEvery5Minutes("refreshDevices")
	}else if(rfmins == "5")
		runEvery5Minutes("refreshDevices")
	else if(rfmins == "10")
		runEvery10Minutes("refreshDevices")
	else if(rfmins == "15")
		runEvery15Minutes("refreshDevices")
	else if(rfmins == "30")
		runEvery30Minutes("refreshDevices")
	else
		runEvery10Minutes("refreshDevices")
}


Map<String,Object> restParams(String path, Map queryMap, Boolean intr=true, String jsonBody=sNULL, Boolean headers=false){
	Map<String,Object> res
	res=[
		uri: "${getServerUrl()}",
		path: path,
		query: [type:"json"] + (intr ? [integration: version()] : [:]) + queryMap,
		// Sensibo recommends the header over ?apiKey=; it also keeps the key out of request URLs
		headers: ["X-Api-Key": getApiKey()] + (headers ? ["Content-Type": sAPPJSON] : [:])
	]
	if(!headers) res= res + [requestContentType: sAPPJSON]
	if(jsonBody) res= res + [body: jsonBody]
	res
}


private Long wnow(){ return (Long)now() }

// Polling: routine polls ask only for the fields used; the large remoteCapabilities is fetched at most every 6 hours
// (or when missing / when the pod list is wanted). HTTP 429 pauses polling with an increasing back-off.
@Field static final String sPOLLFIELDS = 'id,room,temperatureUnit,connectionStatus,acState,measurements,smartMode,productModel,firmwareVersion,location,filtersCleaning'
@Field static final Long lCAPSMAXAGE = 21600000L		// 6 hours
@Field static final Long lBACKOFFMIN = 300000L		// 5 minutes
@Field static final Long lBACKOFFMAX = 1800000L		// 30 minutes
@Field volatile static Long sensiboCapsLongFLD= 0L
@Field volatile static Long sensiboBackoffUntilFLD= 0L
@Field volatile static Long sensiboBackoffMsFLD= 0L

private String pollFields(Boolean full){
	Boolean capsStale= sensiboCAPMapFLD.isEmpty() || wnow() > (sensiboCapsLongFLD + lCAPSMAXAGE)
	return (full || capsStale) ? "*" : sPOLLFIELDS
}

private Boolean inBackoff(){ return wnow() < sensiboBackoffUntilFLD }

private void noteRateLimited(){
	Long b= sensiboBackoffMsFLD ? Math.min(sensiboBackoffMsFLD * 2L, lBACKOFFMAX) : lBACKOFFMIN
	sensiboBackoffMsFLD= b
	sensiboBackoffUntilFLD= wnow() + b
	logWarn("Sensibo rate limit reached (HTTP 429); pausing polls for ${(b / 60000L)} minutes")
}

private void noteRequestOk(){ sensiboBackoffMsFLD= 0L }

// status code of a failed synchronous request (Hubitat throws for non-2xx responses), or null
private static Integer errStatus(e){
	try{ return e?.statusCode as Integer }catch(ignored){ return null }
}

private void noteError(e){ if(errStatus(e) == 429) noteRateLimited() }

void asyncReq(String callBack, Boolean doPod=false){
	logTrace( "asyncReq")
	if(inBackoff()){
		logDebug("asyncReq skipped: backing off after a Sensibo rate limit")
		return
	}
	Map deviceListParams= restParams('/api/v2/users/me/pods', [fields: pollFields(doPod)],true)
	logDebug( "HTTP REQUEST ASYNC asyncReq")
	try {
		asynchttpGet('ahttpReqResp', deviceListParams, [command: callBack, doPod: doPod])
	}catch(e){
		logError("async exception",e)
	}
}

@Field static final Integer i200=200
@Field static final Integer i300=300
@Field static final Integer i400=400

void ahttpReqResp(resp, Map callbackData){
	logTrace( "attpReqResp")
	String callBackC= (String)callbackData?.command
	Boolean doPod= (Boolean)callbackData?.doPod
	//logWarn( "callback: $callbackData")
	Integer rCode; rCode=(Integer)resp.status
	String erMsg; erMsg=''
	if(resp.hasError()){
		erMsg= " Response Status: ${resp.status} error Message: ${resp.getErrorMessage()}".toString()
		logWarn(erMsg)
	}
	Boolean respOk=(rCode>=i200 && rCode<i300)
	Boolean respRedir=(rCode>=i300 && rCode<i400)
	if(rCode == 429) noteRateLimited()
	if(respOk){
		noteRequestOk()
		try{
			String sdata; sdata= resp.data
			if(!sdata){
				logWarn("ahttpReqResp: empty response from Sensibo")
				return
			}
			Map data= (Map)new JsonSlurper().parseText(sdata)
			if(data?.status && (String)data.status != "success"){
				logWarn("ahttpReqResp: Sensibo returned status ${data.status}")
				return
			}
			List<Map> res= (List<Map>)data?.result
			if(res == null){
				logWarn("ahttpReqResp: no pod list in Sensibo response")
				return
			}
			fillFields(res,doPod)
			sensiboLastFullFLD= wnow()

			if(callBackC) "$callBackC"()
		}catch(Exception e){
			logError("ahttpReqResp: could not process Sensibo response", e)	// e.g. HTML error page or truncated JSON
		}
	}else if(!respRedir){
		logWarn("ahttpReqResp: refresh failed (HTTP ${rCode}); devices keep their last values")
	}
	logTrace( "attpReqResp done")
}

List<Map> syncReq(String fields="*"){
	logTrace( "syncReq($fields)")
	if(inBackoff()){
		logDebug("syncReq skipped: backing off after a Sensibo rate limit")
		return null
	}

	Map deviceListParams= restParams('/api/v2/users/me/pods', [fields: fields],true)

	List<Map> res; res=null
	try {
		logDebug("HTTP REQUEST SYNC syncReq")
		httpGet(deviceListParams){ resp ->
			if(resp.status == i200){
				res= (List<Map>)resp.data.result
				noteRequestOk()
			}
		}
		return res
	}catch(e){
		noteError(e)
		logError( "Exception syncReq: ", e)
	}
	return null
}

@Field volatile static Long sensiboPodsLongFLD= 0L

// Shared caches, keyed by pod id. They are read and written by the async poll handler and by device commands on
// other threads, so the outer maps are ConcurrentHashMaps (no ConcurrentModificationException; note they can't hold
// null values) and cached entries are never changed in place: updateDeviceCache() swaps in an updated copy.
@Field volatile static Map<String,Map> sensiboMapFLD     = new java.util.concurrent.ConcurrentHashMap()
@Field volatile static Map<String, Map<String,Map>> sensiboDeviceMapFLD     = new java.util.concurrent.ConcurrentHashMap()
@Field volatile static Map<String, Long> sensiboDeviceLongFLD     = new java.util.concurrent.ConcurrentHashMap()
@Field volatile static Map<String, Map<String,Map>> sensiboClimateMapFLD = new java.util.concurrent.ConcurrentHashMap()
@Field volatile static Map<String, Map<String,Map>> sensiboCAPMapFLD = new java.util.concurrent.ConcurrentHashMap()

// replace a device's cached entry with an updated copy, so a map another thread is reading is never changed
private void updateDeviceCache(String dni, Closure upd){
	Map old= sensiboDeviceMapFLD[dni]
	if(old == null) return
	Map t= new LinkedHashMap(old)
	upd(t)
	sensiboDeviceMapFLD[dni]= t
}

void fillFields(List<Map> res, Boolean doPod=false){
	logDebug("fillFields($doPod) ")
	Map<String, String> pods= [:]
	Map<String, Map> fullPods= new java.util.concurrent.ConcurrentHashMap()
	res.each { pod ->
		String key= (String)pod.id
		String value= (String) ((Map)pod.room).name
		pods[key]= value
		fullPods[key]= pod
	}
	if(doPod && pods){
		state.pods= pods
		sensiboPodsLongFLD= wnow()
	}
	sensiboMapFLD= fullPods

	loadFLD()
}

void loadFLD(){
	Map newFLD; newFLD= new java.util.concurrent.ConcurrentHashMap()
	Map<String,Map> pods= sensiboMapFLD	// snapshot: another thread may replace the field while this runs
	List<String> selected= ((List)settings.SelectedSensiboPods ?: []).collect{ it as String }

	// drop cached data for pods that were deselected or are no longer in the account
	[sensiboDeviceMapFLD, sensiboDeviceLongFLD, sensiboClimateMapFLD, sensiboCAPMapFLD].each{ Map m ->
		(m.keySet() as List).each{ k -> if(!(k in selected) || !pods.containsKey(k)) m.remove(k) }
	}

	// fill in pollChild data
	pods.each {
		String PodUid= it.key

		Map stat
		stat= (Map)it.value
		if(!(PodUid in selected)) return	// only selected pods have devices; skip the rest

		Map newMap; newMap=[:]
		for (String k in ['id','temperatureUnit','connectionStatus','room','acState','location','firmwareVersion',
						  'productModel','remoteCapabilities','smartMode','measurements','filtersCleaning']){
			newMap[k]= stat[k]
		}
		newFLD[PodUid]=newMap

		stat= newMap

		if(stat.remoteCapabilities != null){	// routine polls leave remoteCapabilities out; keep the cached copy
			sensiboCAPMapFLD[PodUid]= [
					remoteCapabilities: stat.remoteCapabilities,
					productModel:  (String)stat.productModel ]
			sensiboCAPMapFLD= sensiboCAPMapFLD
			sensiboCapsLongFLD= wnow()
		}

		Map data; data= [:]
		if(stat?.acState){
			Map acState= stat.acState
//			logDebug("fillACState PodUID : $PodUid : " + acState)

			String OnOff= acState.on ? "on" : "off"
			String tempUnit= acState.temperatureUnit == null ? stat.temperatureUnit : acState.temperatureUnit
			def rawTemp; rawTemp= acState.targetTemperature
			if(rawTemp == null && stat.measurements?.temperature != null){
				// no target (e.g. fan mode): use room temperature, which Sensibo always reports in C
				rawTemp= tempUnit == "F" ? cToF(stat.measurements.temperature as Double).round() : stat.measurements.temperature
			}
			def stemp= rawTemp != null ? rawTemp.toInteger() : "--"
			String sMode= acState.swing == null ? "stopped" : acState.swing
			String battery= stat.productModel == "skyv1" ? "battery" : "mains"

			data= [
					//order is important here
					temperatureUnit  : tempUnit,
					on               : OnOff.toString(),
					switch           : OnOff.toString(),
					currentmode      : acState.mode,
					targetTemperature: stemp,
					fanLevel         : acState.fanLevel,
					swing            : sMode,
					//end order is important here
					powerSource      : battery,
					productModel     : stat.productModel,
					firmwareVersion  : stat.firmwareVersion,
					Error            : "Success"
			]
		} else {
			data= [
					temperatureUnit  : "",
					on               : "--",
					switch           : "--",
					currentmode      : "--",
					targetTemperature: "--",
					fanLevel         : "--",
					swing            : "--",
					powerSource      : "",
					productModel     : "",
					firmwareVersion  : "",
					Error            : "Failed"
			]
			logWarn("fillACState Failed")
		}

		sensiboClimateMapFLD[PodUid]= fillClimateReact(PodUid,stat)
		sensiboClimateMapFLD= sensiboClimateMapFLD

		Map acState= data
		Map ClimateReact= getClimateReact(PodUid)

		Map measure; measure= stat?.measurements

		String dni= PodUid
		if((String)acState.Error == "Failed"){
			// replace any old Success entry so the device is told the pod is unavailable (it keeps its last values and warns)
			sensiboDeviceMapFLD[dni]= data
			sensiboDeviceLongFLD[dni]= wnow()
			sensiboDeviceMapFLD= sensiboDeviceMapFLD
		}else if(measure){
			//logDebug( "fillFields: updating dni $dni")

			Map mdata= measureData(measure, (String)acState.temperatureUnit == "F")

			data= data + mdata + [ Climate: ClimateReact.Climate ]

			if((String)acState.powerSource=='battery'){
				Integer battpourcentage; battpourcentage= 100
				Integer battVoltage; battVoltage= measure.batteryVoltage
				if(battVoltage == null){
					battVoltage= 3000
				}
				if(battVoltage < 2850) battpourcentage= 10
				else if(battVoltage < 2950) battpourcentage= 50
				data= data + [
						battery: battpourcentage,
						voltage: battVoltage,
				]
			}
			//sensiboDeviceMapFLD[dni].Climate= on/off

			//logDebug( "fillFields: $dni Event Data= ${data}")

			sensiboDeviceMapFLD[dni]= data
			sensiboDeviceLongFLD[dni]= wnow()
			sensiboDeviceMapFLD= sensiboDeviceMapFLD
		}else{
			// AC state but no measurements: still update the AC state instead of keeping stale data
			sensiboDeviceMapFLD[dni]= data + [Climate: ClimateReact?.Climate]
			sensiboDeviceLongFLD[dni]= wnow()
			sensiboDeviceMapFLD= sensiboDeviceMapFLD
		}
	}
	sensiboMapFLD= newFLD
}

// Sensibo measurements (temperatures in C) -> device values in the pod's unit. A missing measurement is left out
// (the device keeps its last value) instead of being reported as 0; 0 itself is a valid reading.
Map measureData(Map measure, Boolean toF){
	Map mdata= [:]
	if(measure?.temperature != null){
		Double stemp= (measure.temperature as Double).round(1)
		mdata.temperature= toF ? cToF(stemp).round(1) : stemp
	}
	if(measure?.feelsLike != null){
		Double ftemp= (measure.feelsLike as Double).round(1)
		mdata.feelsLike= toF ? cToF(ftemp).round(1) : ftemp
	}
	if(measure?.humidity != null) mdata.humidity= Math.round(measure.humidity as Double)
	return mdata
}

// ---------- Real-time updates (Sensibo event stream, held by the "Sensibo Event Stream" child device) ----------

@Field volatile static Boolean streamUpFLD= false
@Field volatile static Long sensiboLastFullFLD= 0L	// last successful full refresh
@Field volatile static Map<String,Boolean> streamPendingFLD= new java.util.concurrent.ConcurrentHashMap()

private void manageStreamDevice(){
	def sd= getChildDevice(sSTREAMDNI)
	if(streamEnabled()){
		if(!sd){
			try{
				sd= addChildDevice(getChildNamespace(), sSTREAMTYPE, sSTREAMDNI, ((List)location.hubs)[iZ].id,
						[label: sSTREAMTYPE, name: sSTREAMTYPE])
			}catch(e){
				logError("Could not create the event stream device; is the '${sSTREAMTYPE}' driver installed?", e)
				return
			}
		}
		sd.connect()
	}else if(sd){
		sd.disconnect()
		deleteChildDevice(sSTREAMDNI)
		streamDown(true)
	}
}

// status line for the app page
String streamStatusText(){
	def sd= getChildDevice(sSTREAMDNI)
	if(!sd) return "Real-time updates: <b>not set up</b> (click Done to create the stream device)"
	String st= (String)sd.currentValue('streamStatus') ?: 'unknown'
	String last= (String)sd.currentValue('lastEvent')
	Long at; at= null
	try{ at= sd.lastEventAt() as Long }catch(ignored){ }	// older driver code without lastEventAt()
	String ago= at ? fmtAgo(wnow() - at) : 'none yet'
	String rf= (String)settings.refreshinminutes ?: "10"
	String poll= streamUpFLD ? (rf == "30" ? "30" : "15") : (rf == "1" ? "5" : rf)
	return "Real-time updates: <b>${st}</b><br>Last event: ${last ?: 'none'} (${ago})<br>Full refresh every ${poll} minutes" +
			(streamUpFLD ? "" : " (normal polling until the stream connects)")
}

private static String fmtAgo(Long ms){
	Long s= (ms ?: 0L).intdiv(1000L) as Long	// intdiv: "/" on whole numbers gives a decimal in Groovy
	if(s < 60L) return "${s}s ago"
	if(s < 3600L) return "${s.intdiv(60L)} min ago"
	return "${s.intdiv(3600L)} h ago"
}

// stream opened: fetch everything once (changes may have happened while it was closed), poll less often
void streamHello(){
	logDebug("event stream hello")
	markStreamUp()
	// catch up on changes missed while the stream was closed, unless a full refresh ran recently
	if(wnow() > sensiboLastFullFLD + 300000L) asyncReq('finishRefresh')
}

// any stream event means it's up (the flag resets when this app's code is saved, while the stream stays open)
private void markStreamUp(){
	unschedule('streamDownConfirm')	// reconnected in time: the drop doesn't count
	if(!streamUpFLD){
		logInfo("Real-time updates connected")
		streamUpFLD= true
		resetSchedule()
	}
}

// stream closed or failed: back to the configured polling, unless it reconnects within 2 minutes
// (now: stopped on purpose, switch immediately)
void streamDown(Boolean now=false){
	if(now){
		unschedule('streamDownConfirm')
		streamDownConfirm()
	}else{
		runIn(120, 'streamDownConfirm')
	}
}

void streamDownConfirm(){
	if(streamUpFLD){
		logInfo("Real-time updates disconnected; using normal polling")
		streamUpFLD= false
		resetSchedule()
	}
}

// new measurements for a pod: update the cache and the device directly, no API call
void streamMeasurements(String id, Map m){
	markStreamUp()
	if(!id || !m || sensiboDeviceMapFLD[id] == null) return	// not a selected pod
	Map md= measureData(m, (String)sensiboDeviceMapFLD[id]?.temperatureUnit == "F")
	if(!md) return
	updateDeviceCache(id){ Map t -> t.putAll(md) }
	getChildDevice(id)?.parseEventData(md)
}

// a pod changed (AC state, connection, or anything when fields is empty): re-read it. Bursts are grouped.
void streamChanged(String id, List fields){
	markStreamUp()
	if(!id || sensiboDeviceMapFLD[id] == null) return	// not a selected pod
	if(fields && fields.every{ it == 'measurements' }) return	// the "measurements" event carries those
	streamPendingFLD[id]= true
	runIn(3, 'streamFetchPending')
}

void streamFetchPending(){
	List<String> ids= (streamPendingFLD.keySet() as List).collect{ it as String }
	ids.each{ streamPendingFLD.remove(it) }
	ids.each{ String id ->
		Map pod= syncPod(id)
		if(pod){
			Map all= new java.util.concurrent.ConcurrentHashMap(sensiboMapFLD)
			all[id]= pod
			sensiboMapFLD= all
			loadFLD()
			getChildDevice(id)?.poll()
		}
	}
}

// GET one pod with the light poll fields
Map syncPod(String id){
	if(inBackoff()) return null
	Map params= restParams("/api/v2/pods/${id}", [fields: sPOLLFIELDS], true)
	Map res; res= null
	try{
		httpGet(params){ resp ->
			if(resp.status == i200){
				res= (Map)resp.data?.result
				noteRequestOk()
			}
		}
	}catch(e){
		noteError(e)
		logError("Exception syncPod: ", e)
	}
	return res
}

// Get Climate React settings
Map fillClimateReact(String PodUid, Map stats){
	logTrace( "fillClimateReact($PodUid)")
	Map data; data= [:]

	Map ClimateReact= stats?.smartMode
	//logDebug( "fillClimateReact " + ClimateReact)
	if(!ClimateReact){
		data= [ Climate : "notdefined", Error : "Success" ]
	}else{
		ClimateReact.any{ stat ->
			String OnOff; OnOff= "off"
			if(ClimateReact.enabled != null){
				OnOff= ClimateReact.enabled ? "on" : "off"
			}
			data= [ Climate : OnOff.toString(), Error : "Success" ]
		}
	}
	logTrace( "Returning Climate React : ${data.Climate}")
	return data
}

// Get the latest state for the Sensibo Pod
Map getACState(String PodUid){
	logTrace("getACState($PodUid)")
	Map stat; stat= sensiboDeviceMapFLD[PodUid]
	if(!stat){
		List<Map> res= syncReq()
		if(res){
			fillFields(res, false)
			stat= sensiboDeviceMapFLD[PodUid]
		}
	}
	return stat
}

// Get Climate React settings
Map getClimateReact(String PodUid){
	logTrace("getClimateReact($PodUid)")
	Map stat; stat= sensiboClimateMapFLD[PodUid]
	if(!stat){
		List<Map> res= syncReq()
		if(res){
			fillFields(res, false)
			stat= sensiboClimateMapFLD[PodUid]
		}
	}
	return stat
}

Map<String,String> getSensiboPodList(){
	logTrace( "getSensiboPodList()")

	Map<String,String> pods; pods= [:]

	if(!state.pods || wnow() > (sensiboPodsLongFLD + 300000L)){	// cache the pod list for 5 minutes
		List<Map> res= syncReq()
		if(res){
			fillFields(res,true)
			pods= (Map<String,String>)state.pods
		}
	}else
		pods= (Map<String,String>)state.pods

	logDebug( "Sensibo Pods: $pods" )
	return pods
}

Boolean ping(){
	logTrace( "ping()")
	Boolean returnStatus; returnStatus= false	// only a 200 response counts as success

	Map deviceListParams= restParams('/api/v2/users/me/pods', [fields:"id,room"],true)

	try{
		logDebug( "HTTP REQUEST SYNC ping")
		httpGet(deviceListParams){ resp ->
			if(resp.status == i200){
				returnStatus= true
			}
		}
	}catch(Exception e){
		logError( "Exception ping: ", e)
		returnStatus= false
	}
	return returnStatus
}

/*
def hournotification(){
	logTrace( "hournotification() called")

	Date hour= new Date()
	String curHour= hour.format("HH:mm",(TimeZone)location.timeZone)
	String curDay= hour.format("EEEE",(TimeZone)location.timeZone)
	// Check the time Threshold
	String stext; stext= ""
	if(startTimeEvent && endTimeEvent){
		Date minHour= new Date().parse(sDateFormat(), startTimeEvent)
		Date endHour= new Date().parse(sDateFormat(), endTimeEvent)

		String minHourstr= minHour.format("HH:mm",(TimeZone)location.timeZone)
		String maxHourstr= endHour.format("HH:mm",(TimeZone)location.timeZone)

		if(curHour >= minHourstr && curHour <= maxHourstr){
			List devices= getChildDevices()
			devices.each{ d ->
				logTrace( "Notification every hour for device: ${d.id}")
				String currentPod= d.displayName
				def currentTemperature= d.currentState("temperature").value
				def currentHumidity= d.currentState("humidity").value
				def currentBattery= d.currentState("voltage").value
				String sunit= d.currentState("temperatureUnit").value
				stext= "${currentPod} - Temperature: ${currentTemperature} ${sunit} Humidity: ${currentHumidity}% Battery: ${currentBattery}"

				sendPush(stext)
			}
		}
	}else{
			List devices= getChildDevices()
			devices.each{ d ->
				logTrace( "Notification every hour for device: ${d.id}")
				String currentPod= d.displayName
				def currentTemperature= d.currentState("temperature").value
				def currentHumidity= d.currentState("humidity").value
				def currentBattery= d.currentState("voltage").value
				String sunit= d.currentState("temperatureUnit").value
				stext= "${currentPod} - Temperature: ${currentTemperature} ${sunit} Humidity: ${currentHumidity}% Battery: ${currentBattery}"

				sendPush(stext)
			}
	}
}

def switchesHandler(evt){
  if(evt.value == "on"){
		logDebug( "switch turned on!")
	}else if(evt.value == "off"){
		logDebug( "switch turned off!")
	}
}

def eTempUnitHandler(evt){
	//refreshOneDevice(evt.device.displayName)
}
 */

/*
def eTemperatureHandler(evt){
	def currentTemperature= evt.device.currentState("temperature").value
	String currentPod= evt.device.displayName
	Date hour= new Date()

	if(inDateThreshold(evt,"temperature") == true){
		if(maxTemperature != null){
			if(currentTemperature.toDouble() > maxTemperature){
				String stext= "Temperature level is too high at ${currentPod} : ${currentTemperature}"
				sendEvent(name: "lastTemperaturePush", value: "${stext}", descriptionText:"${stext}")
				sendPush(stext)

				state.lastTemperaturePush= hour
			}
		}
		if(minTemperature != null){
			if(currentTemperature.toDouble() < minTemperature){
				String stext= "Temperature level is too low at ${currentPod} : ${currentTemperature}"
				sendEvent(name: "lastTemperaturePush", value: "${stext}", descriptionText:"${stext}")
				sendPush(stext)

				state.lastTemperaturePush= hour
			}
		}
	}
}

def eHumidityHandler(evt){
	def currentHumidity= evt.device.currentState("humidity").value
	String currentPod= evt.device.displayName
	Date hour= new Date()
	if(inDateThreshold(evt,"humidity") == true){
		if(maxHumidity != null){
			if(currentHumidity.toDouble() > maxHumidity){
				String stext= "Humidity level is too high at ${currentPod} : ${currentHumidity}"
				sendEvent(name: "lastHumidityPush", value: "${stext}", descriptionText:"${stext}")
				sendPush(stext)

				state.lastHumidityPush= hour
			}
		}
		if(minHumidity != null){
			if(currentHumidity.toDouble() < minHumidity){
				String stext= "Humidity level is too low at ${currentPod} : ${currentHumidity}"
				sendEvent(name: "lastHumidityPush", value: "${stext}", descriptionText:"${stext}")
				sendPush(stext)

				state.lastHumidityPush= hour
			}
		}
	}
} */

/*
static String sDateFormat(){ "yyyy-MM-dd'T'HH:mm:ss.SSSZ" }
static String sDateFormatNoMilli(){ "yyyy-MM-dd'T'HH:mm:ssZ" }

Boolean canPushNotification(String currentPod, Date hour,String sType){
	// Check if the client already received a push
	if(sType == "temperature"){
		if(sfrequency.toString().isInteger()){
			if(state.lastTemperaturePush != null){
				Long unxNow; unxNow= hour.getTime()

				Date before= new Date().parse(sDateFormatNoMilli(),state.lastTemperaturePush)
				Long unxEnd; unxEnd= before.getTime()

				unxNow= Math.round(unxNow/1000L)
				unxEnd= Math.round(unxEnd/1000L)
				Long timeDiff; timeDiff= Math.abs(unxNow-unxEnd)
				timeDiff= Math.round(timeDiff/60L)
				if(timeDiff <= sfrequency){
					return false
				}
			}
		}
	}else{
		if(sfrequency.toString().isInteger()){
			if(state.lastHumidityPush != null){
				Long unxNow; unxNow= hour.getTime()

				Date before= new Date().parse(sDateFormatNoMilli(),state.lastHumidityPush)
				Long unxEnd; unxEnd= before.getTime()

				unxNow= Math.round(unxNow/1000L)
				unxEnd= Math.round(unxEnd/1000L)
				Long timeDiff; timeDiff= Math.abs(unxNow-unxEnd)
				timeDiff= Math.round(timeDiff/60L)

				if(timeDiff <= sfrequency){
					return false
				}
			}
		}
	}
	return true
}

Boolean inDateThreshold(evt,String sType){
	Date hour= new Date()
	String curHour= hour.format("HH:mm",(TimeZone)location.timeZone)
	String curDay= hour.format("EEEE",(TimeZone)location.timeZone)
	String currentPod= evt.device.displayName

	// Check if the client already received a push

	Boolean result= canPushNotification(currentPod,hour, sType)
	if(!result){
		return false
	}

	// Check the day of the week
	if(days != null && !days.contains(curDay)){
		return false
	}

	// Check the time Threshold
	if(startTime && endTime){
		Date minHour= new Date().parse(sDateFormat(), startTime)
		Date endHour= new Date().parse(sDateFormat(), endTime)

		String minHourstr= minHour.format("HH:mm",(TimeZone)location.timeZone)
		String maxHourstr= endHour.format("HH:mm",(TimeZone)location.timeZone)

		return curHour >= minHourstr && curHour < maxHourstr
	}
	return true
}
*/
def refresh(){
	logTrace( "refresh()")
	unschedule('refreshDevices')	// only the poll; keep logsOff and the post-command cmdPoll
	refreshDevices()
	resetSchedule()
}

/*
def refreshOneDevice(dni){
	logTrace( "refreshOneDevice() called")
	def d= getChildDevice(dni)
	d.cmdRefresh()
}*/

def refreshDevices(){
	logTrace("refreshDevices()")
	asyncReq('finishRefresh')
}

void finishRefresh(){
	logTrace("finishRefresh()")
	List devices= getChildDevices().findAll{ it.deviceNetworkId != sSTREAMDNI }
	devices.each{ d ->
		logDebug( "Calling poll() on device: ${d.id}")
		d.poll()	// update from the data just fetched; the device's refresh() asks for a new fetch, so calling it here would loop
	}
}


//@CompileStatic
void pollChildren(String PodUid){
	logTrace( "pollChildren($PodUid)")

	try{
		List<Map> res= syncReq(pollFields(false))	// light fields unless capabilities are missing or old
		if(res){
			fillFields(res,false)
		}
	}catch(Exception e){
		logError( "___exception polling children: ", e)
	}
}

// called by child after command execution to force data gather
void afterCmdRefresh(){
	if(streamUpFLD) return	// the stream sends a "changed" event for the command, which re-reads the pod
	runIn(7,'cmdPoll') // allow commands to finish
}

void cmdPoll(){ pollChild(null,true) }

// Poll Child is invoked from the Child Device itself as part of the Poll Capability
@Field volatile static Long sensiboLastSyncFLD= 0L

Map pollChild( child, Boolean frc=false){
	if(frc){
		logDebug( "pollChild(): frc: $frc")
		asyncReq('finishRefresh')
		return null
	}

	String dni= "${child.device.deviceNetworkId}".toString()
	logDebug( "pollChild( ${dni} )")
	Map<String,Object> tData; tData= sensiboDeviceMapFLD[dni]

	// Fetch synchronously only when there is no cached entry at all (e.g. after a hub reboot or code save), and at
	// most once a minute. A cached "Failed" entry means the last fetch already found the pod unavailable; fetching
	// again for every device poll would repeat a full request each time while the pod is offline.
	if(tData == null && wnow() > sensiboLastSyncFLD + 60000L){
		sensiboLastSyncFLD= wnow()
		pollChildren(dni)
		tData= sensiboDeviceMapFLD[dni]
		if(tData != null){
			Long currentTime= wnow()
			sensiboDeviceLongFLD[dni]= currentTime
			updateDeviceCache(dni){ Map t -> t.updated= currentTime }
			tData= sensiboDeviceMapFLD[dni]
		}
	}

	if(tData == null){
		logWarn("pollChild: no data for ${dni} (pod offline, not selected, or Sensibo unreachable)")
		return null
	}
	// a copy, since the device iterates it while the cache may be updated; a "Failed" entry is passed on so the
	// device keeps its last values and logs that the pod is unavailable
	return new LinkedHashMap(tData)
}


Boolean setClimateReactConfiguration(child,String PodUid,String JsonString){
	logTrace( "setClimateReactConfiguration() called for $PodUid with settings : $JsonString" )

	Boolean result= sendPostJsonClimate(PodUid, JsonString)
	if(result){
		// cached Climate React settings (e.g. "notdefined") are now stale; re-read on next use
		sensiboClimateMapFLD.remove(PodUid)
		sensiboClimateMapFLD= sensiboClimateMapFLD
	}

	/*
	if(result){ // update cache
		String dni= child.device.deviceNetworkId
		Map tData= state.sensibo[child.device.deviceNetworkId]

		if(tData == null){
			pollChildren(child.device.deviceNetworkId)
			tData= state.sensibo[child.device.deviceNetworkId]
		}

		//tData.Climate= ClimateState
		tData.Error= "Success"
	}else{
		def tData= state.sensibo[child.device.deviceNetworkId]
		if(tData == null) return false

		tData.Error= "Failed"
	} */

	return result
}

Boolean setClimateReact(child,String PodUid, String ClimateState){
	logTrace( "setClimateReact() called for $PodUid Climate React: $ClimateState" )

	String dni= child.device.deviceNetworkId

	Map ClimateReact= getClimateReact(PodUid)
	if(!ClimateReact){
		logWarn("setClimateReact: could not read Climate React settings for $PodUid")
		return false
	}

	logDebug( "DEBUG " + PodUid + " " + dni + " SAME?")
	logDebug( "DEBUG " + ClimateReact.Climate + " " + ClimateState)
	if((String)ClimateReact.Climate == "notdefined"){
		updateDeviceCache(dni){ Map t -> t.Climate= ClimateReact.Climate }
		logWarn("setClimateReact: Climate React is not configured for $PodUid; use setClimateReactConfiguration first")
		return false	// nothing was sent, so don't report the change as done
	}

	String jsonRequestBody
	if(ClimateState == "on"){
		jsonRequestBody= '{"enabled": true}'
	}else{
		jsonRequestBody= '{"enabled": false}'
	}

	logDebug( "Mode Request Body= ${jsonRequestBody}")

	Boolean result= sendPutJson(PodUid, jsonRequestBody)

	if(result){ // try to update cache
		updateDeviceCache(dni){ Map t ->
			t.Climate= ClimateState
			t.Error= "Success"
		}
	}else{
		updateDeviceCache(dni){ Map t -> t.Error= "Failed" }
	}

	return result
}

// Change one acState property (on, targetTemperature, fanLevel, swing) with PATCH, so other values aren't
// overwritten with possibly stale ones. Falls back to a full acState POST if the hub has no httpPatch.
@Field volatile static Boolean patchUnsupportedFLD= false

Boolean setACProperty(child, String PodUid, String property, value){
	logTrace( "setACProperty() called for $PodUid : $property = $value")
	String dni= child.device.deviceNetworkId
	Map<String,Object> tData; tData= sensiboDeviceMapFLD[dni]

	Boolean result; result= patchUnsupportedFLD ? null : sendPatch(PodUid, property, value)
	if(result == null){	// httpPatch not available: send the full state, built from the cache with this one value changed
		if(tData == null){
			logWarn("setACProperty: no cached state for $dni; cannot fall back to a full update")
			return false
		}
		String on= property == 'on' ? (value ? "on" : "off") : (String)tData.on
		return setACStates(child, PodUid, on, (String)tData.currentmode,
				property == 'targetTemperature' ? value : tData.targetTemperature,
				property == 'fanLevel' ? (String)value : (String)tData.fanLevel,
				property == 'swing' ? (String)value : (String)tData.swing,
				(String)tData.temperatureUnit)
	}

	if(result){ // keep the cache in step until the next poll
		updateDeviceCache(dni){ Map t ->
			if(property == 'on'){
				t.on= value ? "on" : "off"
				t.switch= t.on
			}else{
				t[property]= value
			}
		}
	}
	return result
}

// returns true/false, or null when the hub has no httpPatch
Boolean sendPatch(String PodUid, String property, value){
	String jsonBody= JsonOutput.toJson([newValue: value])
	Map cmdParams= restParams( "/api/v2/pods/${PodUid}/acStates/${property}", [:], true, jsonBody, true)
	Boolean ok; ok= false
	try{
		logDebug( "HTTP PATCH ${cmdParams.path} body: ${jsonBody}")
		httpPatch(cmdParams){ resp ->
			logDebug("sendPatch response status: ${resp.status}")
			ok= (resp.status == i200)
		}
	}catch(Exception e){
		String es= e.toString()	// includes the exception class name; getClass() isn't allowed in the Hubitat sandbox
		if(es.contains('MissingMethodException') && es.contains('httpPatch')){
			logWarn("httpPatch is not available on this hub; using full acState updates instead")
			patchUnsupportedFLD= true
			return null
		}
		noteError(e)
		logError( "Exception sendPatch: ", e)
		ok= false
	}
	if(ok) noteRequestOk()
	return ok
}

Boolean setACStates(child,String PodUid, String on, String mode, targetTemperature, String fanLevel, String swingM, String sUnit){
	logTrace( "setACStates() called for $PodUid ON: $on - MODE: $mode - Temp : $targetTemperature - FAN : $fanLevel - SWING MODE : $swingM - UNIT : $sUnit")

	//Return false if no values was read from Sensibo API
	if(on == "--"){ return false }

	String dni= child.device.deviceNetworkId
	Boolean isOn= (on == "on")
	//if(swingM == null) swingM= "stopped"

	Map cmd; cmd =[
			on: isOn,
			mode: mode
	]

	logDebug("isOn: " + isOn)
	logDebug("Mode: " + mode)

	String jsonRequestBody //; jsonRequestBody= '{"acState":{"on": ' + isOn.toString() + ',"mode": "' + mode + '"'

	//logDebug( "Mode Request Body= ${jsonRequestBody}")

	if(isOn){
		logDebug( "Fan Level is :$fanLevel")
		logDebug( "Swing is :$swingM")
		logDebug( "Target Temperature is :$targetTemperature")

		if(fanLevel != null){
			logDebug( "Fan Level info is present")
			cmd= cmd + [fanLevel: fanLevel]
			//jsonRequestBody += ',"fanLevel": "' + fanLevel + '"'
		}

		if(targetTemperature != 0 && targetTemperature != null){
			cmd= cmd + [ targetTemperature: targetTemperature,
						  temperatureUnit:  sUnit ]
			//jsonRequestBody += ',"targetTemperature": '+ targetTemperature + ',"temperatureUnit": "' + sUnit + '"'
		}
		if(swingM){
			cmd= cmd + [swing: swingM]
			//jsonRequestBody += ',"swing": "' + swingM + '"'
		}
	}

	//jsonRequestBody += '}}'
	Map cmd1= [acState: cmd]
	jsonRequestBody= JsonOutput.toJson(cmd1)

	logDebug( "Command Request Body= ${JsonOutput.prettyPrint(jsonRequestBody)}")

	Boolean result; result= true
	if(!sendJson(PodUid, jsonRequestBody)){
		result= false
	}

	if(result){ // try to update cache
		updateDeviceCache(dni){ Map t ->
			t.on= on
			t.switch= on
			t.currentmode= mode
			if(isOn){
				t.fanLevel= fanLevel !=null ? fanLevel : t.fanLevel
				if(targetTemperature !=0 && targetTemperature != null){
					t.targetTemperature= targetTemperature
					t.temperatureUnit= sUnit
				}
				t.swing= swingM ?: t.swing
			}
			t.Error= "Success"
		}
		logDebug( "setACStates after Device : " + dni + " state : " + sensiboDeviceMapFLD[dni])
	}else{
		updateDeviceCache(dni){ Map t -> t.Error= "Failed" }
	}
	return result
}

//Get the capabilities of the A/C Unit
Map getCapabilities(String PodUid, String mode){
	logTrace( "getCapabilities($PodUid) $mode")

	Map data; data= [:]

	//sensiboCAPMapFLD[PodUid]= fillCap(PodUid,stat)
	Map<String,Map> svData; svData= (Map)sensiboCAPMapFLD[PodUid]?.remoteCapabilities
	if(!svData){
		List<Map> res= syncReq()
		if(res){
			fillFields(res,false)
			svData= (Map)sensiboCAPMapFLD[PodUid]?.remoteCapabilities
		}
	}
	if(svData){
		logDebug("getCapabilities ${svData}")

		data.productModel= (String)sensiboCAPMapFLD[PodUid]?.productModel
		switch (mode){
			case "dry":
				data.remoteCapabilities= (Map)svData.modes?.dry
				break
			case "cool":
				data.remoteCapabilities= (Map)svData.modes?.cool
				break
			case "heat":
				data.remoteCapabilities= (Map)svData.modes?.heat
				break
			case "fan":
				data.remoteCapabilities= (Map)svData.modes?.fan
				break
			case "auto":
				data.remoteCapabilities= (Map)svData.modes?.auto
				break
			case "modes":
				data.remoteCapabilities= (Map)svData.modes
				break
			default:
				data.remoteCapabilities= [:]
				logWarn( "getCapabilities Failed - no mode $mode")
		}
	}else{
		logWarn( "getCapabilities Failed")
		data= [
				remoteCapabilities : [:],
				productModel : ""
		]
	}
	logDebug("getCapabilities $PodUid $mode ${data}")
	return data
}

Boolean sendPutJson(String PodUid, String jsonBody){
	logTrace( "sendPutJson() called - Request sent to Sensibo API(smartmode) for PODUid : $PodUid - ${version()} - $jsonBody")
	Map foo= new JsonSlurper().parseText(jsonBody) // check the json string
	Map cmdParams= restParams( "/api/v2/pods/${PodUid}/smartmode",
			[:],
			true,
			jsonBody)
/*	Map cmdParams= [
		uri: "${getServerUrl()}",
		path: "/api/v2/pods/${PodUid}/smartmode",
		requestContentType: sAPPJSON,
		// headers: ["Content-Type": sAPPJSON],
		query: [apiKey: getApiKey(), integration:"${version()}", type:"json"],
		body: jsonBody
	] */

	Boolean ok; ok= false
	try{
		logDebug( "HTTP PUT REQUEST SYNC sendPutJson")
		httpPut(cmdParams){ resp ->	// a return inside this closure would not return from sendPutJson
			if(resp.status == i200){
				logDebug( "sendPutJson updated ${resp.data}")
				logTrace( "Successful call to Sensibo API.")
				ok= true
			}else{
				logTrace( "Failed call to Sensibo API.")
			}
		}
	}catch(Exception e){
		noteError(e)
		logError( "Exception Sending Json: ", e)
		ok= false
	}
	return ok
}

Boolean sendPostJsonClimate(String PodUid, String jsonBody){
	logTrace( "sendPostJsonClimate() called - Request sent to Sensibo API(smartmode) for PODUid : $PodUid - ${version()} - $jsonBody")
	Map foo= new JsonSlurper().parseText(jsonBody) // check the json string
	Map cmdParams= restParams( "/api/v2/pods/${PodUid}/smartmode",
			[:],
			true,
			jsonBody, true)
/*	Map cmdParams= [
		uri: "${getServerUrl()}",
		path: "/api/v2/pods/${PodUid}/smartmode",
		headers: ["Content-Type": sAPPJSON],
		query: [apiKey: getApiKey(), integration:"${version()}", type:"json"],
		body: jsonBody
	] */

	Boolean ok; ok= false
	try{
		logDebug( "HTTP POST REQUEST SYNC sendPostJson")
		httpPost(cmdParams){ resp ->	// a return inside this closure would not return from sendPostJsonClimate
			if(resp.status == i200){
				logDebug( "sendPostJsonClimate updated ${resp.data}")
				logTrace( "Successful call to Sensibo API.")
				ok= true
			}else{
				logTrace( "Failed call to Sensibo API.")
			}
		}
	}catch(Exception e){
		noteError(e)
		logError( "Exception Sending Json: ",e)
		ok= false
	}
	return ok
}

// Send state to the Sensibo Pod
Boolean sendJson(String PodUid, String jsonBody){
	logTrace( "sendJson() called - Request sent to Sensibo API(acStates) for PODUid : $PodUid - ${version()} - $jsonBody")
	Map foo= new JsonSlurper().parseText(jsonBody) // check the json string
	Map cmdParams= restParams( "/api/v2/pods/${PodUid}/acStates",
			//[fields : "acState"],
			[:],
			true,
			jsonBody, true)
/*	def cmdParams= [
		uri: "${getServerUrl()}",
		path: "/api/v2/pods/${PodUid}/acStates",
		headers: ["Content-Type": sAPPJSON],
		query: [apiKey: getApiKey(), integration:"${version()}", type:"json", fields:"acState"],
		body: jsonBody
	] */

	Boolean returnStatus; returnStatus= false

	try{
		logDebug( "HTTP POST REQUEST SYNC sendJson")
		logDebug( "sendJson path: ${cmdParams.path} body: ${cmdParams.body}")	// not cmdParams: its query holds the API key
		httpPost(cmdParams){ resp ->
			logDebug("sendJson response status: ${resp.status}")
			if(resp.status == i200){
				logDebug( "sendJson updated ${resp.data}")
				logTrace( "sendJson Successful call to Sensibo API.")

				//returnStatus= resp.status

				returnStatus= true
			}else{
				logTrace( "Failed call to Sensibo API.")
				returnStatus= false
			}
		}
	}catch(Exception e){
		noteError(e)
		logError( "Exception sendJson: ",e)
		returnStatus= false
	}

	logDebug( "sendJson Return Status: ${returnStatus}")
	return returnStatus
}


static Double cToF(temp){
	return (temp * 1.8D + 32.0D).toDouble()
}

static Double fToC(temp){
	return ((temp - 32.0D) / 1.8D).toDouble()
}

/*
// Subscribe functions
def OnOffHandler(evt){
	logTrace( "on off handler activated $evt.value")

	//def name= evt.device.displayName

	if(sendPush){
		if(evt.value == "on"){
			//sendPush("The ${name} is turned on!")
		}else if(evt.value == "off"){
			//sendPush("The ${name} is turned off!")
		}
	}
} */

/*
public void enableDebugLog(){ app.updateSetting("logDebug",[value:sTRUE,type:"bool"]); logInfo("Debug Logs Enabled From Main App...") }
public void disableDebugLog(){ app.updateSetting("logDebug",[value:sFALSE,type:"bool"]); logInfo("Debug Logs Disabled From Main App...") }
public void enableTraceLog(){ app.updateSetting("logTrace",[value:sTRUE,type:"bool"]); logInfo("Trace Logs Enabled From Main App...") }
public void disableTraceLog(){ app.updateSetting("logTrace",[value:sFALSE,type:"bool"]); logInfo("Trace Logs Disabled From Main App...") }
*/

private void logDebug(String msg){ if((Boolean)settings.logDebug) { log.debug logPrefix(msg, "purple") } }
private void logInfo(String msg){ if((Boolean)settings.logInfo != false) { log.info logPrefix(msg, "#0299b1") } }
private void logTrace(String msg){ if((Boolean)settings.logTrace) { log.trace logPrefix(msg, sCLRGRY) } }
private void logWarn(String msg){ if((Boolean)settings.logWarn != false) { log.warn logPrefix(sSPACE + msg, sCLRORG) }  }

private void logError(String msg, Exception ex=null){
	if((Boolean)settings.logError != false){
		log.error logPrefix(msg, sCLRRED)
		String a,b; a= sNULL; b=sNULL
		try{
			if(ex){
				a= getExceptionMessageWithLine(ex)
				b= getStackTrace(ex)
			}
		}catch(ignored){ }
		if(a||b) log.error logPrefix(a+" \n"+b, sCLRRED)
	}
}

static String span(String str, String clr=sNULL, String sz=sNULL, Boolean bld=false, Boolean br=false) { return str ? "<span ${(clr || sz || bld) ? "style='${clr ? "color: ${clr};" : sBLK}${sz ? "font-size: ${sz};" : sBLK}${bld ? "font-weight: bold;" : sBLK}'" : sBLK}>${str}</span>${br ? sLINEBR : sBLK}" : sBLK }

static String logPrefix(String msg, String color= sNULL){
	return span("Sensibo (v" + devVersionFLD + ") | ", sCLRGRY) + span(msg, color)
}

@Field static final String sSPCSB7='      │'
@Field static final String sSPCSB6='     │'
@Field static final String sSPCS6 ='      '
@Field static final String sSPCS5 ='     '
@Field static final String sSPCST='┌─ '
@Field static final String sSPCSM='├─ '
@Field static final String sSPCSE='└─ '
@Field static final String sNWL='\n'
@Field static final String sDBNL='\n\n • '

@CompileStatic
static String spanStr(Boolean html,String s){ return html? span(s) : s }

@CompileStatic
static String doLineStrt(Integer level,List<Boolean>newLevel){
	String lineStrt; lineStrt=sNWL
	Boolean dB; dB=false
	Integer i
	for(i=iZ;i<level;i++){
		if(i+i1<level){
			if(!newLevel[i]){
				if(!dB){ lineStrt+=sSPCSB7; dB=true }
				else lineStrt+=sSPCSB6
			}else lineStrt+= !dB ? sSPCS6:sSPCS5
		}else lineStrt+= !dB ? sSPCS6:sSPCS5
	}
	return lineStrt
}

@CompileStatic
static String dumpListDesc(List data,Integer level,List<Boolean> lastLevel,String listLabel,Boolean html=false,Boolean reorder=true){
	String str; str=sBLK
	Integer n; n=i1
	List<Boolean> newLevel=lastLevel

	List list1=data?.collect{it}
	Integer sz=list1.size()
	for(Object par in list1){
		String lbl=listLabel+"[${n-i1}]".toString()
		if(par instanceof Map){
			Map<String,Object> newmap=[:]
			newmap[lbl]=(Map)par
			Boolean t1=n==sz
			newLevel[level]=t1
			str+=dumpMapDesc(newmap,level,newLevel,n,sz,!t1,html,reorder)
		}else if(par instanceof List || par instanceof ArrayList){
			Map<String,Object> newmap=[:]
			newmap[lbl]=par
			Boolean t1=n==sz
			newLevel[level]=t1
			str+=dumpMapDesc(newmap,level,newLevel,n,sz,!t1,html,reorder)
		}else{
			String lineStrt
			lineStrt=doLineStrt(level,lastLevel)
			lineStrt+=n==i1 && sz>i1 ? sSPCST:(n<sz ? sSPCSM:sSPCSE)
			str+=spanStr(html, lineStrt+lbl+": ${par} (${objType(par)})".toString() )
		}
		n+=i1
	}
	return str
}

@CompileStatic
static String dumpMapDesc(Map<String,Object> data,Integer level,List<Boolean> lastLevel,Integer listCnt=null,Integer listSz=null,Boolean listCall=false,Boolean html=false,Boolean reorder=true){
	String str; str=sBLK
	Integer n; n=i1
	Integer sz=data?.size()
	Map<String,Object> svMap,svLMap,newMap; svMap=[:]; svLMap=[:]; newMap=[:]
	for(Map.Entry<String,Object> par in data){
		String k=(String)par.key
		def v=par.value
		if(reorder && v instanceof Map){
			svMap+=[(k): v]
		}else if(reorder && (v instanceof List || v instanceof ArrayList)){
			svLMap+=[(k): v]
		}else newMap+=[(k):v]
	}
	newMap+=svMap+svLMap
	Integer lvlpls=level+i1
	for(Map.Entry<String,Object> par in newMap){
		String lineStrt
		List<Boolean> newLevel=lastLevel
		Boolean thisIsLast=n==sz && !listCall
		if(level>iZ)newLevel[(level-i1)]=thisIsLast
		Boolean theLast
		theLast=thisIsLast
		if(level==iZ)lineStrt=sDBNL
		else{
			theLast=theLast && thisIsLast
			lineStrt=doLineStrt(level,newLevel)
			if(listSz && listCnt && listCall)lineStrt+=listCnt==i1 && listSz>i1 ? sSPCST:(listCnt<listSz ? sSPCSM:sSPCSE)
			else lineStrt+=((n<sz || listCall) && !thisIsLast) ? sSPCSM:sSPCSE
		}
		String k=(String)par.key
		def v=par.value
		String objType=objType(v)
		if(v instanceof Map){
			str+=spanStr(html, lineStrt+"${k}: (${objType})".toString() )
			newLevel[lvlpls]=theLast
			str+=dumpMapDesc((Map)v,lvlpls,newLevel,null,null,false,html,reorder)
		}
		else if(v instanceof List || v instanceof ArrayList){
			str+=spanStr(html, lineStrt+"${k}: [${objType}]".toString() )
			newLevel[lvlpls]=theLast
			str+=dumpListDesc((List)v,lvlpls,newLevel,sBLK,html,reorder)
		}
		else{
			str+=spanStr(html, lineStrt+"${k}: (${v}) (${objType})".toString() )
		}
		n+=i1
	}
	return str
}

@CompileStatic
static String objType(obj){ return span(myObj(obj),sCLRORG) }

@CompileStatic
static String getMapDescStr(Map<String,Object> data,Boolean reorder=true){
	List<Boolean> lastLevel=[true]
	String str=dumpMapDesc(data,iZ,lastLevel,null,null,false,true,reorder)
	return str!=sBLK ? str:'No Data was returned'
}

static String myObj(obj){
	if(obj instanceof String) {return "String"}
	else if(obj instanceof GString) {return "GString"}
	else if(obj instanceof Map) {return "Map"}
	else if(obj instanceof LinkedHashMap) {return "LinkedHashMap"}
	else if(obj instanceof HashMap) {return "HashMap"}
	else if(obj instanceof List) {return "List"}
	else if(obj instanceof ArrayList) {return "ArrayList"}
	else if(obj instanceof Integer) {return "Integer"}
	else if(obj instanceof BigInteger) {return "BigInteger"}
	else if(obj instanceof Long) {return "Long"}
	else if(obj instanceof Boolean) {return "Boolean"}
	else if(obj instanceof BigDecimal) {return "BigDecimal"}
	else if(obj instanceof Double) {return "Double"}
	else if(obj instanceof Float) {return "Float"}
	else if(obj instanceof Byte) {return "Byte"}
	else if(obj instanceof com.hubitat.app.DeviceWrapper)return 'Device'
	else { return "unknown"}
}

def pageDumpPCache(){
	LinkedHashMap t0,t1,t2
	t0= sensiboMapFLD
	t1= sensiboDeviceMapFLD
	//sensiboClimateMapFLD= [:]
	//sensiboCAPMapFLD= [:]
	String message=getMapDescStr(t0,false)
	String message1=getMapDescStr(t1,false)
	return dynamicPage((sNM):sPDPC,(sTIT):sBLK,uninstall:false){
		section('sensiboMapFLD Cache dump'){
			paragraph message
		}
		section('sensiboDeviceMapFLD Cache dump'){
			paragraph message1
		}
	}
}
