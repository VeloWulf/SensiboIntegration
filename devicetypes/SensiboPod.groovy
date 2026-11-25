/**
 *  Sensibo Thermostat Device
 *
 *  Copyright 2021 Paul Hutton
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Date		Comments
 *  2021-02-15	Forked from Bryan Li's port from ST
 *  2024-03-27	Significant updates, support thermostat capabilities
 *  2024-07-16  Improved backwards compatibility and added supported modes and fan levels for dashboards
 *  2024-08-14  Added non-standard modes and fan levels to function with dashboards (colour coding will not work but functions will)
 *  2026-11-25  nh.schottfam - Fixes:
 *                - supportedThermostatModes / supportedThermostatFanModes sent as JSON lists (were one string);
 *                  fan modes cover all of the unit's modes; added refreshSupportedModes command
 *                - medium_low / medium_high fan names accepted; setAirConditionerMode accepts fanOnly
 *                - failed polls and "--" values are skipped (last values kept) instead of being reported
 *                - setSwingMode uses the requested swing; setAll "off" turns the unit off and updates setpoint/fan
 *                - no crash in raise/lower when the mode has no temperatures; no endless recursion in returnNext
 *                - modes without fan levels don't get the previous fan level; on() works before the first poll
 *              Behaviour changes:
 *                - heating/cooling setpoints no longer turn the unit on or change its mode: stored, and applied
 *                  when the unit is on in that mode (or auto); raise/lower is one call in the current mode
 *                - off, setpoint, fan level and swing changes send only that value (PATCH); fan level and swing
 *                  no longer turn the unit on
 *                - setpoints use the nearest temperature the AC supports within the min/max setpoint limits
 *                - temperatures use the hub's scale, converted to/from the pod's unit; events include units
 *                - thermostatOperatingState in auto is heating/cooling/idle from room temperature vs target
 *                - refresh fetches fresh data from Sensibo (poll uses the app's cached data)
 *              Clean-up: removed unused commands and helpers; setSchedule stub (not supported)
 *
 */

//file:noinspection GroovySillyAssignment
//file:noinspection GrDeprecatedAPIUsage
//file:noinspection GroovyDoubleNegation
//file:noinspection GroovyUnusedAssignment
//file:noinspection unused
//file:noinspection SpellCheckingInspection
//file:noinspection GroovyFallthrough
//ffile:noinspection GrMethodMayBeStatic
//file:noinspection GroovyAssignabilityCheck
//file:noinspection UnnecessaryQualifiedReference


import groovy.json.*
import groovy.transform.Field
import groovy.transform.CompileStatic

preferences{
	//Logging Message Config
	input "logInfo", "bool", title: "Show Info Logs?", required: false, defaultValue: true
	input "logWarn", "bool", title: "Show Warning Logs?", required: false, defaultValue: true
	input "logError", "bool", title: "Show Error Logs?", required: false, defaultValue: true
	input "logDebug", "bool", title: "Show Debug Logs?", description: "Only leave on when required", required: false, defaultValue: false
	input "logTrace", "bool", title: "Show Detailed Logs?", description: "Only Enabled when asked by the developer", required: false, defaultValue: false
}

metadata{
	definition (name: "SensiboPod", namespace: "velowulf", author: "Paul Hutton", oauth: false){
		// capability "Actuator"
		// capability "Initialize"
		capability "Battery"
		capability "Health Check"
		capability "Polling"
		capability "PowerSource"
		capability "Refresh"
		capability "RelativeHumidityMeasurement"
		capability "Sensor"
		capability "Switch"
		capability "TemperatureMeasurement"
		capability "Thermostat"
		capability "Voltage Measurement"


		attribute "minHeatTemp", "number"
		attribute "maxHeatTemp", "number"
		attribute "minCoolTemp", "number"
		attribute "maxCoolTemp", "number"
		attribute "minCoolingSetpoint", "number"		//google alexa compatability
		attribute "maxCoolingSetpoint", "number"		//google alexa compatability
		attribute "minHeatingSetpoint", "number"		//google alexa compatability
		attribute "maxHeatingSetpoint", "number"		//google alexa compatability
//		attribute "thermostatThreshold", "number"
//		attribute "lastTempUpdate", "date"
//		attribute "maxUpdateInterval", "number"
//		attribute "thermostatTemperatureSetpoint", "String"	//google


		attribute sTEMPUNIT,"String"
		attribute "productModel","String"
		attribute "firmwareVersion","String"
		attribute "Climate","String"
		attribute sTARGTEMP,"Number"
		attribute "feelsLike","Number"
		attribute "Error","string"
		attribute sSWING, "String"
		attribute "airConditionerMode","String"
		attribute "airConditionerFanMode","String"
		attribute sCURM,"String"
		attribute sFANMODE,"String"


//		attribute "statusText","String"
		command "setAll",[
				[name:"Thermostat mode*", type: "ENUM", constraints: [
						sCOOL,
						"fan",
						"dry",
						sAUTO,
						sHEAT,
						sOFF ]
				],
				[name: "Temperature*", type: "NUMBER", description: ""],
				[name:"Fan level*", type: "ENUM", constraints: [
						sON,
						"circulate",
						sAUTO,
						"quiet",
						"low",
						"mediumLow",
						"medium",
						"mediumHigh",
						"high",
						"strong" ]
				]
		]

		command "setMinCoolTemp", [[ name: "temperature*", type: "NUMBER"]]
		command "setMaxCoolTemp", [[ name: "temperature*", type: "NUMBER"]]
		command "setMinHeatTemp", [[ name: "temperature*", type: "NUMBER"]]
		command "setMaxHeatTemp", [[ name: "temperature*", type: "NUMBER"]]
		command "resetMinMax"
		command "refreshSupportedModes"

		//command "voltage"
		command "raiseTemperature"
		command "lowerTemperature"
		command "setSwingMode", [
				[ name:"Swing Mode*", type: "ENUM", description: "Pick an option", constraints: [
						"fixedTop",
						"fixedMiddleTop",
						"fixedMiddle",
						"fixedMiddleBottom",
						"fixedBottom",
						"rangeTop",
						"rangeMiddle",
						"rangeBottom",
						"rangeFull",
						"horizontal",
						"both",
						"stopped" ]
				]
		]
		command "modeDry"
		command "modeFan"
		command "fanLow"
		command "fanMediumLow"
		command "fanMedium"
		command "fanMediumHigh"
		command "fanHigh"
		command "fanQuiet"
		command "fanStrong"
		command "setAirConditionerMode", [
				[ name:"State*", type: "ENUM", constraints: [
						sCOOL,
						"fan",
						"dry",
						sAUTO,
						sHEAT,
						sOFF ]
				]
		]

		command "setAirConditionerFanMode", [
				[ name:"State*", type: "ENUM", constraints: [
						sON,
						"circulate",
						sAUTO,
						"quiet",
						"low",
						"mediumLow",
						"medium",
						"mediumHigh",
						"high",
						"strong" ]
				]
		]
		command "setClimateReact", [
				[ name:"State*", type: "ENUM", constraints: [
						sON,
						sOFF
				] ]
		]
		command "setClimateReactConfiguration",[
				[name:"Set Climate React State*", type: "ENUM", constraints: [
						sON, sOFF ]
				],
				[name:"Climate react trigger type*", type: "ENUM", constraints: [
						sTEMP, sHUMIDITY, "feelsLike" ]
				],
				[name:"Low Temp or humidity*", type: "NUMBER", description: "Low Threshold"],
				[name:"High Temp or humidity*", type: "NUMBER", description: "High Threshold"],
				[name:"Low state*", type: "JSON_OBJECT", description: "What happens at low threshold JSON"],
				[name:"High state*", type: "JSON_OBJECT", description: "What happens at high threshold JSON"]
		]
	}
}

@Field static final String devVersionFLD  = '2.0.0.0'

@Field static final String sNULL          = (String)null
@Field static final String sBLANK         = ''
@Field static final String sSPACE         = ' '
@Field static final String sLINEBR        = '<br>'
@Field static final String sTRUE          = 'true'
@Field static final String sFALSE         = 'false'
@Field static final String sCLRRED        = 'red'
@Field static final String sCLRGRY        = 'gray'
@Field static final String sCLRORG        = 'orange'

@Field static final String sON            = 'on'
@Field static final String sOFF           = 'off'
@Field static final String sTEMP          = 'temperature'
@Field static final String sTARGTEMP      = 'targetTemperature'
@Field static final String sSW            = 'switch'
@Field static final String sCURM          = 'currentmode'
@Field static final String sFANMODE       = 'fanLevel'
@Field static final String sSWING         = 'swing'
@Field static final String sCOOL          = 'cool'
@Field static final String sHEAT          = 'heat'
@Field static final String sAUTO          = 'auto'
@Field static final String sTHERMMODE     = 'thermostatMode'
@Field static final String sTHERMOPER     = 'thermostatOperatingState'
@Field static final String sTHERMFANMODE  = 'thermostatFanMode'
@Field static final String sHEATSP        = 'heatingSetpoint'
@Field static final String sCOOLSP        = 'coolingSetpoint'
@Field static final String sTHERMSP       = 'thermostatSetpoint'
@Field static final String sHUMIDITY      = 'humidity'
@Field static final String sTEMPUNIT      = 'temperatureUnit'
@Field static final String sC             = 'C'
@Field static final String sF             = 'F'

def installed(){
	logTrace("installed")
	initialize()
}

def updated(){
	logTrace("updated")
	refreshSupportedModes()
	//if(advLogsActive()){ runIn(1800, "logsOff") }
	if(advLogsActive()){ runIn(28800, "logsOff") }
}

void refreshSupportedModes(){
	logTrace("refreshSupportedModes")
	Map rc = gtModesCapabilities() ?: [:]	// fetched once for both lists ([:] if it failed: both use defaults, no second try)
	wsendEvent(name: "supportedThermostatModes", value: JsonOutput.toJson(gtSupportedModes(rc)))
	wsendEvent(name: 'supportedThermostatFanModes', value: JsonOutput.toJson(gtSupportedFanModes(rc)))
}

Boolean advLogsActive(){ return ((Boolean)settings.logDebug || (Boolean)settings.logTrace) }
void logsOff(){
	device.updateSetting("logDebug",[value:sFALSE,type:"bool"])
	device.updateSetting("logTrace",[value:sFALSE,type:"bool"])
	log.debug "Disabling debug logs"
}

def initialize(){
	logTrace("initialize")
	// Let's just set a few things before starting
	String hubScale= gtLtScale()

	// Let's set all base thermostat settings
	if(hubScale == sC){
		wsendEvent(name: "minCoolTemp", value: 15.5, unit: sC) // 60°F
		wsendEvent(name: "minCoolingSetpoint", value: 15.5, unit: sC) // Google
		wsendEvent(name: "maxCoolTemp", value: 35.0, unit: sC) // 95°F
		wsendEvent(name: "maxCoolingSetpoint", value: 35.0, unit: sC) // Google
		wsendEvent(name: "minHeatTemp", value: 1.5, unit: sC) // 35°F
		wsendEvent(name: "minHeatingSetpoint", value: 1.5, unit: sC) // Google
		wsendEvent(name: "maxHeatTemp", value: 26.5, unit: sC) // 80°F
		wsendEvent(name: "maxHeatingSetpoint", value: 26.5, unit: sC) // Google
		wsendEvent(name: sTEMP, value: 22.0, unit: sC) // 72°F
		wsendEvent(name: sHEATSP, value: 21.0, unit: sC) // 70°F
		wsendEvent(name: sCOOLSP, value: 24.5, unit: sC) // 76°F
		wsendEvent(name: sTHERMSP, value: 21.0, unit: sC) // 70°F

		wsendEvent(name: sTARGTEMP, value: 21.0, unit: sC) // 70°F
//		wsendEvent(name: "thermostatThreshold", value: 0.5, unit: sC) // Set by user
	}else{
		wsendEvent(name: "minCoolTemp", value: 60, unit: sF) // 15.5°C
		wsendEvent(name: "minCoolingSetpoint", value: 60, unit: sF) // Google
		wsendEvent(name: "maxCoolTemp", value: 95, unit: sF) // 35°C
		wsendEvent(name: "maxCoolingSetpoint", value: 95, unit: sF) // Google
		wsendEvent(name: "minHeatTemp", value: 35, unit: sF) // 1.5°C
		wsendEvent(name: "minHeatingSetpoint", value: 35, unit: sF) // Google
		wsendEvent(name: "maxHeatTemp", value: 80, unit: sF) // 26.5°C
		wsendEvent(name: "maxHeatingSetpoint", value: 80, unit: sF) // Google
		wsendEvent(name: sTEMP, value: 72, unit: sF) // 22°C
		wsendEvent(name: sHEATSP, value: 70, unit: sF) // 21°C
		wsendEvent(name: sCOOLSP, value: 76, unit: sF) // 24.5°C
		wsendEvent(name: sTHERMSP, value: 70, unit: sF) // 21°C

		wsendEvent(name: sTARGTEMP, value: 70, unit: sF) // 21°C
//		wsendEvent(name: "thermostatThreshold", value: 1.0, unit: sF) // Set by user
	}
	wsendEvent(name: sSW, value: sOFF)
	wsendEvent(name: sTHERMFANMODE, value: sAUTO)
	wsendEvent(name: sTHERMMODE, value: sOFF)
	wsendEvent(name: sTHERMOPER, value: "idle")
	refreshSupportedModes()
//	wsendEvent(name: "maxUpdateInterval", value: 65)
//	wsendEvent(name: "lastTempUpdate", value: new Date() )

	wsendEvent(name: sFANMODE, value: sAUTO)
	wsendEvent(name: 'airConditionerFanMode', value: sAUTO)
	wsendEvent(name: sSWING, value: "stopped")
	// currentmode is left unset: it holds the last real AC mode (never "off"); gtActiveMode() covers the gap before the first poll
	wsendEvent(name: 'airConditionerMode', value: sOFF)
}


// Standard thermostat commands

def off(){
	logTrace( "off()")
	modeMode(sOFF)
}

def heat(){
	logTrace( "heat()")
	modeMode(sHEAT)
}

def emergencyHeat(){		// emergency heat not a supported function for Sensibo so use regular heat
	logTrace( "emergencyHeat()")
	modeMode(sHEAT)
}

def cool(){
	logTrace( "cool()")
	modeMode(sCOOL)
}

def auto(){
	logTrace( "auto()")
	modeMode(sAUTO)
}

def fanAuto(){
	logTrace( "fanAuto()")
	dfanLevel(sAUTO)
}

def fanCirculate(){
	logTrace( "fanCirculate()")
	dfanLevel("low")
}

def fanOn(){
	logTrace( "fanOn()")
	dfanLevel("medium")
}

def setThermostatFanMode(String mode){
	logTrace( "setThermostatFanMode($mode)")
	switch (mode){
		case sON:
			fanOn()
			break
		case "circulate":
			fanCirculate()
			break
		case sAUTO:
			fanAuto()
			break
		case "quiet":
			fanQuiet()
			break
		case "low":
			fanLow()
			break
		case "mediumLow":
		case "medium_low":	// Sensibo API name, as reported in the fan mode attributes
			fanMediumLow()
			break
		case "medium":
			fanMedium()
			break
		case "mediumHigh":
		case "medium_high":	// Sensibo API name, as reported in the fan mode attributes
			fanMediumHigh()
			break
		case "high":
			fanHigh()
			break
		case "strong":
			fanStrong()
			break
		default:
			generateErrorEvent()
	}
}

@CompileStatic
def setThermostatMode(mode){
	logTrace( "setThermostatMode($mode)")

	switch (mode){
		case sCOOL:
			cool()
			break
		case "fan":
			modeFan()
			break
		case "dry":
			modeDry()
			break
		case sAUTO:
			auto()
			break
		case sHEAT:
			heat()
			break
		case sOFF:
			off()
			break
		default:
			generateErrorEvent()
	}
}

def setHeatingSetpoint(itemp){
	logTrace( "setHeatingSetpoint($itemp)")
	setModeSetpoint(sHEAT, itemp)
}

def setCoolingSetpoint(itemp){
	logTrace( "setCoolingSetpoint($itemp)")
	setModeSetpoint(sCOOL, itemp)
}

// Standard thermostat behaviour: the heating/cooling setpoint is always stored, and is only sent to the AC
// when the unit is on and in that mode (or auto, which has a single target). It never turns the unit on or
// changes its mode; a stored setpoint is applied when that mode is selected or the unit is turned on.
private void setModeSetpoint(String forMode, itemp){
	String spName = forMode == sHEAT ? sHEATSP : sCOOLSP
	String cur = sdCV(sCURM)
	Boolean apply = sdCV(sSW) == sON && cur in [forMode, sAUTO]
	// check against the range of the mode it will be used in (auto has its own range)
	Integer temp = snapToSupported(toPod(itemp), apply ? cur : forMode, forMode)
	if(temp == null) return
	if(apply){
		if(applySetpoint(temp)) logInfo( "${spName} changed to ${toHub(temp)} for " + gtDNI())
	}else{
		sendTempEvent(spName, temp)
		logInfo( "${spName} set to ${toHub(temp)}; it will be used when ${forMode} mode is on")
	}
}

// send a new target in the current mode, without changing the mode
private Boolean applySetpoint(Integer temp){
	Boolean result = wsetACProperty(sTARGTEMP, temp)	// only the target changes
	if(result){
		generateSetTempEvent(temp)
		String mode = sdCV(sCURM)
		if(mode in [sHEAT, sAUTO]) sendTempEvent(sHEATSP, temp)
		if(mode in [sCOOL, sAUTO]) sendTempEvent(sCOOLSP, temp)
	}else{
		generateErrorEvent()
	}
	cmdRefresh()
	return result
}


// part of the Thermostat capability; Sensibo schedules are not supported by this driver
def setSchedule(schedule){
	logWarn("setSchedule is not supported for Sensibo")
}

// standard because of switch capability

// Turn off or Turn on the AC
def on(){
	logTrace( "on()")

	String mode = gtActiveMode()
	Integer sp = snapToSupported(gtStoredSetpoint(mode), mode)	// heating/cooling setpoint set while the unit was off
	Boolean result = wsetACStates( mode, sp, sON, null, null)
	logDebug("Result : " + result)
	if(result){
		logInfo( "AC turned ON for " + gtDNI())
		generateModeEvent(mode)
		if(sp != null) generateSetTempEvent(sp)
	}else{
		generateErrorEvent()
	}
	cmdRefresh()
}



// non standard commands

def modeDry(){
	logTrace( "dry()")
	modeMode("dry")
}

def modeFan(){
	logTrace( "modeFan()")
	modeMode("fan")
}

def fanLow(){
	logTrace( "fanLow()")
	dfanLevel("low")
}

def fanMediumLow(){
	logTrace( "fanMediumLow()")
	dfanLevel("medium_low")
}

def fanMedium(){
	logTrace( "fanMedium()")
	dfanLevel("medium")
}

def fanMediumHigh(){
	logTrace( "fanMediumHigh()")
	dfanLevel("medium_high")
}
def fanHigh(){
	logTrace( "fanHigh()")
	dfanLevel("high")
}

def fanQuiet(){
	logTrace( "fanQuiet()")
	dfanLevel("quiet")
}

def fanStrong(){
	logTrace( "fanStrong()")
	dfanLevel("strong")
}



// Logging and event management
def generatefanLevelEvent(String Level){
	if(Level == null){ logWarn("No fan level to report"); return }
	String LevelBefore = sdCV(sFANMODE)
	wsendEvent(name: sFANMODE, value: Level, descriptionText: "Fan mode is now ${Level}")
	if(LevelBefore!=Level) logInfo( "Fan level changed to " + Level + " for " + gtDNI())
	wsendEvent(name: 'airConditionerFanMode', value: Level)
	String mode
	mode = Level
/*	mode = (mode in ["high", "medium", "mediumLow", "mediumHigh", "strong"]) ? sON : mode
	mode = (mode in ["low", "quiet"]) ? "circulate" : mode
	mode = !(mode in [sON, "circulate"]) ? sAUTO : mode*/
	wsendEvent(name: 'thermostatFanMode', value: mode)
}

void generateModeEvent(String mode, Boolean doSW=true){
	if(mode != sOFF) wsendEvent(name: sCURM, value: mode, descriptionText: "AC mode is now ${mode}")
	wsendEvent(name: 'airConditionerMode', value: mode)

	String m
	if(mode in [sHEAT,sCOOL,sAUTO,"dry","fan",sOFF])
		m= mode
	/*else if(mode in ["dry"]) {		//non-standard
		m= sCOOL
	}*/
	else // 'fan'
		m= sOFF
	
	wsendEvent(name: sTHERMMODE, value: m, descriptionText: "AC mode is now ${m}")

	m= sBLANK
	if(mode == sCOOL){
		m= 'cooling'
	}else if(mode == sAUTO){
		m= gtAutoOperatingState()	// the AC decides; estimate from room temperature vs target
	}else if(mode == sHEAT){
		m= 'heating'
    }else if(mode == "dry"){
		m= 'drying'
	}else if(mode=="fan"){
		m= 'fan only'
	}else{
		m= 'idle'
	}
	wsendEvent(name: sTHERMOPER, value: m)

	if(doSW) generateSwitchEvent(mode==sOFF ? sOFF : sON)
}

void generateErrorEvent(){
	logError(gtDisplayName()+" FAILED to set the AC State")
//	wsendEvent(name: "Error", value: "Error", descriptionText: gtDisplayName()+" FAILED to set or get the AC State")
}

// in auto the AC chooses heating or cooling itself; estimate it from room temperature vs target (both hub scale)
private String gtAutoOperatingState(){
	def t = device.currentValue(sTEMP)
	def sp = device.currentValue(sTARGTEMP)
	if(t == null || sp == null) return 'idle'
	Double td = t as Double, spd = sp as Double
	if(td > spd) return 'cooling'
	if(td < spd) return 'heating'
	return 'idle'
}

// temp is in the pod's unit; events are sent in the hub's scale
def generateSetTempEvent(temp){
	def t = toHub(temp)
	wsendEvent(name: sTHERMSP, value: t, unit: gtLtScale(), descriptionText: gtDisplayName()+" set temperature is now ${t}")
	wsendEvent(name: sTARGTEMP, value: t, unit: gtLtScale(), descriptionText: gtDisplayName()+" set temperature is now ${t}")
}

private void sendTempEvent(String name, podValue){
	def t = toHub(podValue)
	wsendEvent(name: name, value: t, unit: gtLtScale(), descriptionText: getThermostatDescriptionText(name, t))
}

void generateSwitchEvent(String status){
	wsendEvent(name: sSW, value: status, descriptionText: gtDisplayName()+" is now ${status}")
}

// Unit conversion
static Double cToF(temp){
	return (temp * 1.8D + 32.0D).toDouble()
}

static Double fToC(temp){
	return ((temp - 32.0D) / 1.8D).toDouble()
}

// non standard command
void modeMode(String newMode){
	logTrace( "modeMode() " + newMode)

	String dni= gtDNI()
	logInfo( "Mode change request " + newMode + " for " + dni)
	Boolean result

	String LevelBefore = sdCV(sFANMODE)
	String Level; Level = LevelBefore
	Integer sp; sp = null

	if(newMode==sOFF){ // off always exists; only "on" changes
		result = wsetACProperty('on', false)

	}else{
		Map capabilities = gtCapabilities(newMode)
		if(capabilities.remoteCapabilities != null){
			// see if fan level exists
			List<String> fanLevels = ((Map<String,List>)capabilities.remoteCapabilities).fanLevels
			logDebug("Fan levels capabilities : " + fanLevels)
			Boolean noFan = !fanLevels	// mode has no fan levels (e.g. some units' dry mode): don't send one
			if(noFan) Level = null
			else if(!(Level in fanLevels)){
				Level = GetNextFanLevel(LevelBefore,fanLevels)
				logWarn("Changing Fan : " + Level)
			}

			sp = snapToSupported(gtStoredSetpoint(newMode), newMode)	// heat/cool use the stored heating/cooling setpoint
			result = wsetACStates(newMode, sp, sON, Level, null, noFan)

		}else{ // the mode does not exist, so guess one
			Map rc = gtModesCapabilities()
			List<String> lmodes = rc ? rc.keySet().collect{ it as String } : []
			if(!(newMode in lmodes))
				logWarn("requested $newMode does not exist, try one of $lmodes")
			return
		}
	}

	if(result){
		generateModeEvent(newMode)
		if(sp != null) generateSetTempEvent(sp)
		if(LevelBefore != Level){
			generatefanLevelEvent(Level)
		}
		logInfo( "AC turned $newMode for " + dni)
	}else{
		generateErrorEvent()
	}
	cmdRefresh()
}

String GetNextFanLevel(String fanLevel, List<String>fanLevels){
	logTrace( "GetNextFanLevel " + fanLevel)

	if(!fanLevels) return null

	List<String> listFanLevel = ['quiet','low','medium_low','medium','medium_high','high',sAUTO,'strong']
	String newFanLevel = returnNext(listFanLevel,fanLevels,fanLevel)

	logDebug("Next fanLevel = " + newFanLevel)

	return newFanLevel
}

/**
 * find val in liste2, if there return next value; if not there find val in liste1 and return next value
 * @throws Exception
 */
String returnNext(List<String> liste1, List<String> liste2, String val){
	if(val in liste2) return val
	Integer idx = liste1.indexOf(val)	// -1 if unknown, so search starts at liste1[0]
	Integer n = liste1.size()
	for(Integer i = 1; i <= n; i++){	// bounded: tries each value once instead of recursing forever
		String nval = liste1[(idx + i) % n]
		if(nval in liste2) return nval
	}
	logWarn("No matching value for ${val} in ${liste2}")
	return null
}

void cmdRefresh(){
	logTrace( "cmdRefresh()")
	parent.afterCmdRefresh()
}

// fetch fresh data from Sensibo; the parent then calls poll() on each device when it arrives
def refresh(){
	logTrace( "refresh()")
	parent.refreshDevices()
}

// update from the parent's cached data (no cloud call unless the cache is empty or failed)
void poll(){
	logTrace( "poll()")

	Map results = (Map)parent.pollChild(this)
	parseEventData(results)
}

def parseEventData(Map<String,Object> results){
	logTrace( "parseEventData()")
	logDebug("parseEventData $results")

	if(results && (String)results.Error == "Failed"){
		logWarn("parseEventData: Sensibo returned no AC state; keeping last known values")
		return
	}

	if(results){
		try{
			results.each{ String name, value ->

				// "--" means the Sensibo API call failed; keep the last known value
				if(value == "--"){
					logDebug("parseEventData skipping $name (no data)")
					return
				}

				//logDebug("name: " + name + " value: " + value)
				String unit; unit = sNULL
				Boolean doit; doit= true

				switch(name){
					case sTEMP:
					case "feelsLike":
						value= toHub(value)	// Sensibo reports in the pod's unit
						unit= gtLtScale()
						break
					case sHUMIDITY:
					case "battery":
					case "powerSource":
					case "Climate":
					case sTEMPUNIT:
					case "productModel":
					case "firmwareVersion":
					case "Error":
						break
					case sON:
						doit= false	// same value as "switch", handled there
						break
					case sSW:
						generateSwitchEvent(value as String)
						if(value == sOFF) generateModeEvent(value as String,false)
						doit= false
						break
					case sTHERMMODE:
					case sCURM:
						// this presumes switch was run first (above)
						if(sdCV(sSW) != sOFF){
							generateModeEvent(value as String,false)
						} else if(sdCV(sCURM) != value)
							wsendEvent(name: sCURM, value: value, descriptionText: "AC mode is now ${value}")
						doit= false
						break
					case  sTARGTEMP:
					case  sCOOLSP:
					case  sHEATSP:
					case  sTHERMSP:
						generateSetTempEvent(value)	// sends targetTemperature and thermostatSetpoint
						doit= false
						break
					case sTHERMFANMODE:
					case sFANMODE:
						// some ac units don't have fan levels for some modes so use auto as a default if the value is returned as null
						if(value == null) value = sAUTO
						generatefanLevelEvent(value as String)
						doit= false
						break
					case sSWING:
						generateSwingModeEvent(value as String)
						doit= false
						break
					case "updated":
						doit= false
						break
					case "voltage":
						unit="mA"
						break
					default:
						logWarn("UNKNOWN name: " + name + " value: " + value)
				}
				if(doit){
					Map evt= [
							name: name,
							value: value,
							descriptionText: getThermostatDescriptionText(name, value),
					] + (unit!=sNULL ? [unit: unit] : [:])
					wsendEvent(evt)
				}
			}

			// keep the heating/cooling setpoints in step with the AC's target (already in the hub's scale)
			String mode= sdCV(sCURM)
			// in auto, re-estimate now that this poll's room temperature and target are in
			if(mode == sAUTO && sdCV(sSW) == sON) wsendEvent(name: sTHERMOPER, value: gtAutoOperatingState())
			def Setpoint = device.currentValue(sTARGTEMP)
			if(Setpoint != null){
				if(mode in [sCOOL,sAUTO])
					wsendEvent(name: sCOOLSP, value: Setpoint, unit: gtLtScale())
				if(mode in [sHEAT,sAUTO])
					wsendEvent(name: sHEATSP, value: Setpoint, unit: gtLtScale())
			}
		}catch(e){
			logError("parse error",e)
		}
	}
}

// Set Temperature
void lowerTemperature(){
	logTrace( "lowerTemperature()")
	stepTemperature(false)
}

void raiseTemperature(){
	logTrace( "raiseTemperature()")
	stepTemperature(true)
}

// one step up/down through the AC's supported temperatures for the current mode
private void stepTemperature(Boolean up){
	String mode = sdCV(sCURM)
	Boolean isOn = sdCV(sSW) == sON
	Integer cur = (!isOn ? gtStoredSetpoint(mode) : null) ?: gtPodTarget()
	logDebug("Current target temperature = ${cur}")
	if(cur == null) return

	Integer sp; sp = up ? temperatureUp(cur) : temperatureDown(cur)
	if(sp == -1){
		logWarn("No temperature range for mode $mode")
		return
	}
	sp = snapToSupported(sp, mode)	// respect the min/max setpoint limits in heat/cool
	if(sp == cur){
		logInfo("Temperature already at the ${up ? 'maximum' : 'minimum'} for ${mode}")
		return
	}
	if(isOn){
		applySetpoint(sp)
	}else if(mode in [sHEAT, sCOOL]){
		setModeSetpoint(mode, toHub(sp))	// unit is off: just store it
	}else{
		logInfo("Unit is off; temperature not changed")
	}
}

Integer temperatureUp(Integer temp){
	logTrace( "temperatureUp($temp)")

	List<Integer> values= GetTempValues()
	if(!values) return -1

	List<Integer> found
	found = values.findAll{ number -> number > temp} as List<Integer>

	logDebug("Values retrieved : " + found)

	Integer res
	if(found == null || found.empty) res = values.last() as Integer
	else res = found.first()

	logDebug("Temp before: " + temp	)
	logDebug("Temp after : " + res)

	return res
}

// Use the nearest temperature that is both supported by the AC in `mode` (Sensibo rejects others) and within the
// driver's min/max heating or cooling setpoint limits (limitsFor: heat or cool; defaults to mode). Logged when changed.
// temp and the result are in the pod's unit. If the limits exclude every supported value, the AC's range wins.
private Integer snapToSupported(Integer temp, String mode, String limitsFor = sNULL){
	if(temp == null) return null
	String lf = limitsFor ?: mode
	Integer lo; lo = null
	Integer hi; hi = null
	if(lf == sHEAT || lf == sCOOL){
		String n = lf == sHEAT ? "Heating" : "Cooling"
		def a = device.currentValue("min${n}Setpoint")
		def b = device.currentValue("max${n}Setpoint")
		lo = a != null ? toPod(a) : null
		hi = b != null ? toPod(b) : null
	}
	List<Integer> values = GetTempValues(mode)
	List<Integer> allowed = values ? (values.findAll{ v -> (lo == null || (v as Integer) >= lo) && (hi == null || (v as Integer) <= hi) } as List<Integer>) : null
	Integer best
	if(allowed){
		best = (Integer)allowed.min{ v -> Math.abs((v as Integer) - temp) }
	}else if(values){
		best = (Integer)values.min{ v -> Math.abs((v as Integer) - temp) }
	}else{	// AC range unknown: apply the limits only
		best = temp
		if(lo != null && best < lo) best = lo
		if(hi != null && best > hi) best = hi
	}
	if(best != temp) logWarn("${toHub(temp)} is outside the allowed ${lf} range; using ${toHub(best)}")
	return best
}

List<Integer> GetTempValues(String mode=sNULL){
	String sunit = gtTempUnit()
	Map capabilities = gtCapabilities( mode ?: sdCV(sCURM))
	List<Integer> values

	// remoteCapabilities is [:] for an unknown mode (e.g. currentmode "off" before the first poll),
	// and some modes (e.g. fan) have no temperatures
	Map temps = (Map)((Map)capabilities?.remoteCapabilities)?.temperatures
	Map unitTemps = (Map)temps?.get(sunit == sF ? sF : sC)
	values = (List<Integer>)unitTemps?.values
	return values
}

Integer temperatureDown(Integer temp){
	logTrace( "temperatureDown($temp)")

	List<Integer> values= GetTempValues()
	if(!values) return -1

	List<Integer> found = values.findAll{ number -> number < temp} as List<Integer>
	logDebug("Values retrieved : " + found)
	Integer res
	if(found == null || found.empty) res = values.first() as Integer
	else res = found.last()

	logDebug("Temp before: " + temp )
	logDebug("Temp after : " + res)

	return res
}

def dfanLevel(String newLevel){
	logTrace( "dfanLevel " + newLevel)

	Map capabilities = gtCapabilities(sdCV(sCURM))
	String Level; Level = newLevel
	if(capabilities.remoteCapabilities != null){
		// see if fan level exists
		List<String> fanLevels = ((Map<String,List>)capabilities.remoteCapabilities).fanLevels
		//logDebug("Fan levels capabilities : " + fanLevels)
		if(!(Level in fanLevels)){
			Level = GetNextFanLevel(Level,fanLevels)
			logWarn("Changing Fan : " + Level)
		}

		if(Level == null) return

		Boolean result = wsetACProperty(sFANMODE, Level)	// only the fan level changes; doesn't turn the unit on

		if(result){
			generatefanLevelEvent(Level)
		}else{
			generateErrorEvent()
		}
		cmdRefresh()
	}else{
		logWarn("Fan mode $newLevel does not exist")
		// other instructions may be required if mode does not exist
	}
}

def setAll(String newMode,temp,String fan){
	logTrace( "setAll() " + newMode + ",$temp," + fan )

	if(newMode == sOFF){ off(); return }	// "off" is a mode choice but not a Sensibo mode; {on:true, mode:"off"} is rejected

	Integer Setpoint = snapToSupported(toPod(temp), newMode)
	if(Setpoint == null) return
	String f = fan == sON ? "medium" : (fan == "circulate" ? "low" : fan)	// same as fanOn()/fanCirculate()
	String Level
	Level = fanToApi(f)
	Map capabilities = gtCapabilities(newMode)
	if(capabilities.remoteCapabilities){
		// see if fan level exists
		List<String> fanLevels = ((Map<String,List>)capabilities.remoteCapabilities).fanLevels
		//logDebug("Fan levels capabilities : " + fanLevels)
		Boolean noFan = !fanLevels	// mode has no fan levels (e.g. some units' dry mode): don't send one
		if(noFan) Level = null
		else if(!(Level in fanLevels)){
			Level = GetNextFanLevel(Level,fanLevels)
			logWarn("Changing Fan : " + Level)
		}

		Boolean result = wsetACStates(newMode, Setpoint, sON, Level, null, noFan)

		if(result){
			generateModeEvent(newMode)
			generateSetTempEvent(Setpoint)
			generatefanLevelEvent(Level)	// ignores null

		}else{
			generateErrorEvent()
		}
		cmdRefresh()
	}else{
		generateErrorEvent()
	}
}

Integer GetMinMax(String mode,Boolean min,Boolean isIn,value){
	List<Integer> values= GetTempValues(mode)
	if(values==null || values.empty) return -1

	Integer res
	if(values.size() && !isIn) {
		res= min ? values.first() : values.last()
		return res
	}

	if(isIn) return (value.toInteger() in values) ? value.toInteger() : -1
	else return -1
}

def resetMinMax(){
	logTrace("resetMinMax()")
// reset these to what AC reports
	setMinCoolTemp(null)
	setMaxCoolTemp(null)
	setMinHeatTemp(null)
	setMaxHeatTemp(null)
}

def setMinCoolTemp(Double value=null){ logTrace("setMinCoolTemp($value)"); setMinMax(sCOOL, true, value) }
def setMaxCoolTemp(Double value=null){ logTrace("setMaxCoolTemp($value)"); setMinMax(sCOOL, false, value) }
def setMinHeatTemp(Double value=null){ logTrace("setMinHeatTemp($value)"); setMinMax(sHEAT, true, value) }
def setMaxHeatTemp(Double value=null){ logTrace("setMaxHeatTemp($value)"); setMinMax(sHEAT, false, value) }

// value (hub scale) must be one of the AC's temperatures for the mode; null resets to what the AC reports
private void setMinMax(String mode, Boolean min, Double value){
	Integer v= GetMinMax(mode, min, (value!=null), value != null ? toPod(value) : null)
	String pre = min ? "min" : "max"
	String a1 = pre + (mode == sCOOL ? "CoolTemp" : "HeatTemp")
	String a2 = pre + (mode == sCOOL ? "CoolingSetpoint" : "HeatingSetpoint")
	if(v != -1){
		sendTempEvent(a1, v)
		sendTempEvent(a2, v)
	}else{
		logWarn("invalid ${a1} $value " + gtLtScale())
	}
}

void setAirConditionerMode(String modes){
	logTrace( "setAirConditionerMode($modes)")

	String currentMode = sdCV(sCURM)
	logDebug("switching AC mode from current mode: $currentMode")

	switch (modes){
		case sCOOL:
			cool()
			break
		case "fanOnly":	// Hubitat airConditionerMode name for fan mode
		case "fan":
			modeFan()
			break
		case "dry":
			modeDry()
			break
		case sAUTO:
			auto()
			break
		case sHEAT:
			heat()
			break
		case sOFF:
			off()
			break
		default:
			generateErrorEvent()
	}
}

def setAirConditionerFanMode(String mode){
	logTrace( "setAirConditionerFanMode($mode)")
	switch (mode){
		case sON:
			fanOn()
			break
		case "circulate":
			fanCirculate()
			break
		case sAUTO:
			fanAuto()
			break
		case "quiet":
			fanQuiet()
			break
		case "low":
			fanLow()
			break
		case "mediumLow":
		case "medium_low":	// Sensibo API name, as reported in the fan mode attributes
			fanMediumLow()
			break
		case "medium":
			fanMedium()
			break
		case "mediumHigh":
		case "medium_high":	// Sensibo API name, as reported in the fan mode attributes
			fanMediumHigh()
			break
		case "high":
			fanHigh()
			break
		case "strong":
			fanStrong()
			break
		default:
			generateErrorEvent()
	}
}

// Set Climate React on/off
def setClimateReact(String ClimateState){

	logTrace( "setClimateReact($ClimateState)")
	Boolean result = (Boolean)parent.setClimateReact(this, gtDNI(), ClimateState)
	if(result){
		logInfo( "Climate React changed to " + ClimateState + " for " + gtDNI())
		wsendEvent(name: 'Climate', value: ClimateState)
	}else{
		generateErrorEvent()
	}
	cmdRefresh()
}

def setClimateReactConfiguration(String on_off, String stype,ilowThres, ihighThres,String lowState,String highState){
	///////////////////////////////////////////////
	// on_off : enable climate react string on/off
	// stype : possible values are sTEMP, sHUMIDITY or "feelsLike"
	// lowThres and highThres - number parameters (temperature or humidity)
	// lowState and highState are json MAP: (entries can be left out if not needed)
	//	to turn on AC:
	//	   {"on": true, "targetTemperature": 21.0, "fanLevel":"auto", "temperatureUnit":"C", "mode":"heat", "swing": "stopped"}
	//	to turn off AC:
	//	   {"on": false}
	//
	// Some examples:
	//
	// Range 19-24 Celcius, start to heat to 22 at auto fan if the temp is lower than 19 and stop the AC when higher than 24
	// setClimateReactConfiguration('on','temperature',19, 24,
	//	   '{"on": true, "targetTemperature": 22.0, "fanLevel":"auto", "temperatureUnit":"C", "mode":"heat", "swing": "stopped"}',
	//	   '{"on": false}' )
	//
	// Range 67-68 Farenheit, start to heat to 68 at auto fan if the temp is lower than 67 and stop the AC when higher than 68
	// setClimateReactConfiguration('on', 'temperature',67, 68,
	//	   '{"on": true, "targetTemperature": 68.0, "fanLevel":"auto", "temperatureUnit":"F", "mode":"heat", "swing": "stopped"}',
	//	   '{"on": false}' )
	//
	///////////////////////////////////////////////

	logTrace( "setClimateReactConfiguration()")

	Double lowThres, highThres
	lowThres= ilowThres as Double
	highThres= ihighThres as Double
	if(gtLtScale() == sF){
		lowThres = fToC(lowThres).round(1)
		highThres = fToC(highThres).round(1)
	}

	Map lowStateMap =  new JsonSlurper().parseText(lowState)
	Map highStateMap = new JsonSlurper().parseText(highState)

	Map lowStateJson, highStateJson

	if(lowStateMap){
		lowStateJson = lowStateMap
		/* [
			on: lowStateList[0],
			fanLevel: lowStateList[1],
			temperatureUnit: lowStateList[2],
			targetTemperature: lowStateList[3],
			mode: lowStateList[4]
		] */
	}else{ lowStateJson = null }

	if(highStateMap){
		highStateJson = highStateMap
		/* [
			on: highStateList[0],
			fanLevel: highStateList[1],
			temperatureUnit: highStateList[2],
			targetTemperature: highStateList[3],
			mode: highStateList[4]
		] */
	}else{ highStateJson = null }

	Boolean on= (on_off==sON)

	// smart_type ?
	// low_temperature_threshold ?
	// high_temperature_threshold ?
	// enable_climate_react ??
	Map root = [
		enabled: on,
		deviceUid: gtDNI(),
		type: stype,
//		highTemperatureWebhook: null,
		highTemperatureThreshold: highThres,
		highTemperatureState: highStateJson,
//		lowTemperatureWebhook: null,
		lowTemperatureState: lowStateJson,
		lowTemperatureThreshold: lowThres
	]

	String json1= JsonOutput.toJson(root)
	logDebug("CLIMATE REACT JSON STRING : " + JsonOutput.prettyPrint(json1))
	Boolean result = parent.setClimateReactConfiguration(this, gtDNI(), json1)

	if(result){
		logInfo( "Climate React settings changed for " + gtDNI())
		wsendEvent(name: 'Climate', value: on_off)
	}else{
		generateErrorEvent()
	}
	cmdRefresh()
}

String GetNextSwingMode(String swingMode, List<String>swingModes){
	logTrace( "GetNextSwingMode() " + swingMode)

	if(!swingModes){
		return (String)null
	}

	List<String> listSwingMode = ['stopped','fixedTop','fixedMiddleTop','fixedMiddle','fixedMiddleBottom','fixedBottom','rangeTop','rangeMiddle','rangeBottom','rangeFull','horizontal','both']
	String newSwingMode = returnNext(listSwingMode,swingModes,swingMode)
	logDebug("Next Swing Mode = " + newSwingMode)

	return newSwingMode
}

def setSwingMode(String newSwing){
	logTrace( "setSwingMode($newSwing)")

	Map capabilities = gtCapabilities(sdCV(sCURM))
	String Swing
	Swing = newSwing	// use the requested swing; only substitute if the unit doesn't support it
	if(capabilities.remoteCapabilities != null){
		List<String> Swings = ((Map<String,List>)capabilities.remoteCapabilities).swing
		logDebug("Swing capabilities : " + Swings)
		if(!(Swing in Swings)){
			Swing = GetNextSwingMode(newSwing,Swings)
			logDebug("Changing Swing : " + Swing)
		}
		if(Swing == null){
			logWarn("Swing $newSwing not supported in mode ${sdCV(sCURM)}")
			return
		}

		Boolean result = wsetACProperty(sSWING, Swing)	// only the swing changes; doesn't turn the unit on
		if(result){
			generateSwingModeEvent(Swing)
		}else{
			generateErrorEvent()
		}
		cmdRefresh()
	}else{
		generateErrorEvent()
	}
}

def generateSwingModeEvent(String mode){
	String SwingBefore = sdCV(sSWING)
	if(SwingBefore!=mode) logInfo( "Swing mode changed to " + mode + " for " + gtDNI())
	wsendEvent(name: sSWING, value: mode, descriptionText: gtDisplayName()+" swing mode is now ${mode}")
}

String getThermostatDescriptionText(String name, value){
	if(name in [sTEMP,"feelsLike",sTARGTEMP,sTHERMSP,sCOOLSP,sHEATSP]){
		return "$name is $value " + gtLtScale()	// attributes are in the hub's scale
	}else if(name == sHUMIDITY){
		return "$name is $value %"
	}else if(name == sFANMODE){
		return "fan level is $value"
	}else if(name == sON){
		return "switch is $value"
	}else if(name in ["mode",sTHERMMODE, sTHERMOPER,sTHERMFANMODE]){
		return "$name is ${value}"
	}else if(name == sCURM){
		return "thermostat mode was ${value}"
	}else if(name == "powerSource"){
		return "power source mode was ${value}"
	}else if(name == "Climate"){
		return "Climate React was ${value}"
	}else if(name == sTEMPUNIT){
		return "thermostat unit was ${value}"
	}else if(name == "voltage"){
		return "Battery voltage was ${value}"
	}else if(name == "battery"){
		return "Battery was ${value}"
	}else if(name == sSWING){
		return "Swing mode was ${value}"
	}else if(name == "Error"){
		String str = (value == "Failed") ? "failed" : "success"
		return "Last setACState was ${str}"
	}else{
		return "${name} = ${value}"
	}
}

// parse events into attributes
def parse(String description){
	logDebug("parse '${description}'")
	// this does nothing for virtual device
/*
	String name; name = null
	def value; value = null
	String statusTextmsg = ""
	def msg = parseLanMessage(description)

	def headersAsString = msg.header // => headers as a string
	def headerMap = msg.headers	// => headers as a Map
	def body = msg.body			// => request body as a string
	def status = msg.status		// => http status code of the response
	def json = msg.json			// => any JSON included in response body, as a data structure of lists and maps
	def xml = msg.xml			// => any XML included in response body, as a document tree structure
	def data = msg.data			// => either JSON or XML in response body (whichever is specified by content-type header in response)

	if(description?.startsWith("on/off:")){
		logDebug("Switch command")
		name = sSW
		value = description?.endsWith(" 1") ? sON : sOFF
	}else if(description?.startsWith(sTEMP)){
		logDebug("Temperature")
		name = sTEMP
		value = device.currentValue(sTEMP)
	}else if(description?.startsWith(sHUMIDITY)){
		logDebug("Humidity")
		name = sHUMIDITY
		value = idCV(sHUMIDITY)
	}else if(description?.startsWith(sTARGTEMP)){
		logDebug(sTARGTEMP)
		name = sTARGTEMP
		value = idCV(sTARGTEMP)
	}else if(description?.startsWith(sFANMODE)){
		logDebug(sFANMODE)
		name = sFANMODE
		value = sdCV(sFANMODE)
	}else if(description?.startsWith(sCURM)){
		logDebug("mode")
		name = sCURM
		value = sdCV(sCURM)
	}else if(description?.startsWith(sON)){
		logDebug(sON)
		name = sON
		value = sdCV(sON)
	}else if(description?.startsWith(sSW)){
		logDebug(sSW)
		name = sSW
		value = sdCV(sON)
	}else if(description?.startsWith(sTEMPUNIT)){
		logDebug(sTEMPUNIT)
		name = sTEMPUNIT
		value = gtTempUnit()
	}else if(description?.startsWith("Error")){
		logDebug("Error")
		name = "Error"
		value = sdCV("Error")
	}else if(description?.startsWith("voltage")){
		logDebug("voltage")
		name = "voltage"
		value = device.currentValue("voltage")
	}else if(description?.startsWith("battery")){
		logDebug("battery")
		name = "battery"
		value = idCV("battery")
	}else if(description?.startsWith(sSWING)){
		logDebug(sSWING)
		name = sSWING
		value = sdCV(sSWING)
	}

	def result = createEvent(name: name, value: value)
	logDebug("Parse returned ${result?.descriptionText}")
	return result */
}

def ping(){
	logTrace( "calling parent ping()")
	return parent.ping()
}


public void enableDebugLog(){ device.updateSetting("logDebug",[value:sTRUE,type:"bool"]); logInfo("Debug Logs Enabled From Main App...") }
public void disableDebugLog(){ device.updateSetting("logDebug",[value:sFALSE,type:"bool"]); logInfo("Debug Logs Disabled From Main App...") }
public void enableTraceLog(){ device.updateSetting("logTrace",[value:sTRUE,type:"bool"]); logInfo("Trace Logs Enabled From Main App...") }
public void disableTraceLog(){ device.updateSetting("logTrace",[value:sFALSE,type:"bool"]); logInfo("Trace Logs Disabled From Main App...") }

private void logDebug(String msg){ if((Boolean)settings.logDebug){ log.debug logPrefix(msg, "purple") } }
private void logInfo(String msg){ if((Boolean)settings.logInfo != false){ log.info logPrefix(msg, "#0299b1") } }
private void logTrace(String msg){ if((Boolean)settings.logTrace){ log.trace logPrefix(msg, sCLRGRY) } }
private void logWarn(String msg){ if((Boolean)settings.logWarn != false){ log.warn logPrefix(sSPACE + msg, sCLRORG) } }

private void logError(String msg, Exception ex=null){
	if((Boolean)settings.logError != false){
		log.error logPrefix(msg, sCLRRED)
		String a
		try{
			if(ex) a = getExceptionMessageWithLine(ex)
		}catch(ignored){ }
		if(a) log.error logPrefix(a, sCLRRED)
	}
}

static String span(String str, String clr=sNULL, String sz=sNULL, Boolean bld=false, Boolean br=false){ return str ? "<span ${(clr || sz || bld) ? "style='${clr ? "color: ${clr};" : sBLANK}${sz ? "font-size: ${sz};" : sBLANK}${bld ? "font-weight: bold;" : sBLANK}'" : sBLANK}>${str}</span>${br ? sLINEBR : sBLANK}" : sBLANK }

String logPrefix(String msg, String color = sNULL){
	return span("Sensibo ${gtDisplayName()} (v" + devVersionFLD + ") | ", sCLRGRY) + span(msg, color)
}

void wsendEvent(Map prop){ sendEvent(prop) }

// change a single acState property (on, targetTemperature, fanLevel, swing); value in the pod's unit
Boolean wsetACProperty(String property, value){
	logDebug("wsetACProperty $property = $value")
	return (Boolean)parent.setACProperty(this, gtDNI(), property, value)
}

// noFan: leave fanLevel out of the request instead of falling back to the current fan level
Boolean wsetACStates(String imode, targetTemperature, String ion, String fanLevel, String swingM, Boolean noFan=false){
	String mode= imode ?: gtActiveMode()
	Integer Setpoint = targetTemperature!=null ? (targetTemperature as Integer) : gtPodTarget()	// pod's unit
	String on= ion ?: sON
	String fan = noFan ? null : (fanLevel ?: sdCV(sFANMODE))
	String swing = swingM ?: sdCV(sSWING)

	logDebug("Temp Unit (Setpoint) : " + Setpoint+" Temp Unit : " + gtTempUnit())

	return (Boolean)parent.setACStates(
			this,
			gtDNI(),
			on,
			mode,
			Setpoint,
			fan,
			swing,
			gtTempUnit())
}

String gtTempUnit(){ return sdCV(sTEMPUNIT) }

private Integer idCV(String a){ return device.currentValue(a).toInteger() }
private String sdCV(String a){ return (String)device.currentValue(a) }
String gtDisplayName(){ return (String)device.displayName }
String gtDNI(){ return (String)device.deviceNetworkId }

private Map gtCapabilities(String mode){ return  (Map)parent.getCapabilities(gtDNI(), mode) }

private String gtLtScale(){ return (String)location.getTemperatureScale() }

// Units: Sensibo values and requests use the pod's unit; Hubitat attributes and command arguments use the hub's scale
private String gtPodUnit(){ return gtTempUnit() ?: gtLtScale() }
private Boolean unitsDiffer(){ return gtPodUnit() != gtLtScale() }

// pod unit -> hub scale (for events)
def toHub(v){
	if(v == null || !unitsDiffer()) return v
	Double d = v as Double
	return (gtLtScale() == sF ? cToF(d) : fToC(d)).round(1)
}

// hub scale -> pod unit (for requests); Sensibo targets are whole degrees
Integer toPod(v){
	if(v == null) return null
	Double d = v as Double
	if(unitsDiffer()) d = gtLtScale() == sF ? fToC(d) : cToF(d)
	return Math.round(d) as Integer
}

// current target temperature, in the pod's unit
private Integer gtPodTarget(){
	def t = device.currentValue(sTARGTEMP)
	return t != null ? toPod(t) : null
}

// stored heating/cooling setpoint for heat/cool mode (pod's unit), or null for other modes
private Integer gtStoredSetpoint(String mode){
	String a = mode == sHEAT ? sHEATSP : (mode == sCOOL ? sCOOLSP : sNULL)
	def v = a ? device.currentValue(a) : null
	return v != null ? toPod(v) : null
}

// the AC mode to use when none is given: currentmode is unset (or "off" from older versions) until the first poll
private String gtActiveMode(){
	String m = sdCV(sCURM)
	if(m && !(m in [sOFF, '--'])) return m
	List<String> modes = gtSupportedModes()
	return (sCOOL in modes) ? sCOOL : (modes.find{ it != sOFF } ?: sCOOL)
}

Double getThermostatResolution(){
	return gtLtScale() == sC ? 0.5D : 1.0D
}

def roundDegrees(Double value){
	return gtLtScale() == sC ? Math.round(value * 2.0D) / 2.0D : Math.round(value)
}

// command names use camelCase; the Sensibo API uses medium_low / medium_high
static String fanToApi(String l){
	if(l == 'mediumLow') return 'medium_low'
	if(l == 'mediumHigh') return 'medium_high'
	return l
}

// remoteCapabilities for all modes: [mode: [temperatures:..., fanLevels:..., swing:...]], or null
private Map gtModesCapabilities(){ return (Map)gtCapabilities("modes")?.remoteCapabilities }

// rc: result of gtModesCapabilities(), fetched here if not passed
private List<String> gtSupportedModes(Map irc = null){
	logTrace("gtSupportedModes called")
	Map rc = irc != null ? irc : gtModesCapabilities()
	List<String> modes; modes = []
	if(rc) modes.addAll(rc.keySet().collect{ it as String })
	else{
		logWarn("No modes returned. Using defaults")
		modes = [sHEAT, sCOOL, sAUTO]
	}
	if(!(sOFF in modes)) modes << sOFF
	logDebug("Returned modes: "+modes)
	return modes
}

// fan levels across all of the unit's modes (in first-seen order)
private List<String> gtSupportedFanModes(Map irc = null){
	logTrace("gtSupportedFanModes called")
	Map rc = irc != null ? irc : gtModesCapabilities()
	List<String> fanModes; fanModes = []
	rc?.each{ k, v ->
		((List)((Map)v)?.fanLevels)?.each{ if(!((it as String) in fanModes)) fanModes << (it as String) }
	}
	logDebug("Fan levels capabilities : " + fanModes)
	if(!fanModes){
		logWarn("No fan levels returned. Using defaults")
		fanModes = [sON, "circulate", sAUTO]
	}
	logDebug("Returned fan modes: "+fanModes)
	return fanModes
}
