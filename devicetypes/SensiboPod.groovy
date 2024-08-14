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

		//command "switchFanLevel"
		//command "switchMode"
		//command "raiseCoolSetpoint"
		//command "lowerCoolSetpoint"
		//command "raiseHeatSetpoint"
		//command "lowerHeatSetpoint"
		//command "voltage"
		command "raiseTemperature"
		command "lowerTemperature"
		//command "switchSwing"
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
//		command "fullswing"
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
//		command "toggleClimateReact"
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
	wsendEvent(name: "supportedThermostatModes", value: [gtSupportedModes()])
	wsendEvent(name: 'supportedThermostatFanModes', value: [gtSupportedFanModes()])
	//if(advLogsActive()){ runIn(1800, "logsOff") }
	if(advLogsActive()){ runIn(28800, "logsOff") }
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
	wsendEvent(name: "supportedThermostatModes", value: [gtSupportedModes()])
	wsendEvent(name: 'supportedThermostatFanModes', value: [gtSupportedFanModes()])
//	wsendEvent(name: "maxUpdateInterval", value: 65)
//	wsendEvent(name: "lastTempUpdate", value: new Date() )

	wsendEvent(name: sFANMODE, value: sAUTO)
	wsendEvent(name: 'airConditionerFanMode', value: sAUTO)
	wsendEvent(name: sSWING, value: "stopped")
	wsendEvent(name: sCURM, value: sOFF)
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
			fanMediumLow()
			break
		case "medium":
			fanMedium()
			break
		case "mediumHigh":
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

	Integer temp
	temp = itemp.toInteger()
	logDebug("setTemperature : " + temp)

	Boolean result = wsetACStates(sHEAT, temp, sON, null, null)

	if(result){
		logInfo( "Heating temperature changed to " + temp + " for " + gtDNI())

		generateModeEvent(sHEAT)
		wsendEvent(name: sHEATSP, value: temp)
		generateSetTempEvent(temp)

	}else{
		generateErrorEvent()
	}
	cmdRefresh()
}

def setCoolingSetpoint(itemp){
	logTrace( "setCoolingSetpoint($itemp)")

	Integer temp
	temp = itemp.toInteger()
	logDebug("setTemperature : " + temp )

	Boolean result = wsetACStates(sCOOL, temp, sON, null, null)

	if(result){
		logInfo( "Cooling temperature changed to " + temp + " for " + gtDNI())

		generateModeEvent(sCOOL)

		wsendEvent(name: sCOOLSP, value: temp)
		generateSetTempEvent(temp)
	}else{
		generateErrorEvent()
	}
	cmdRefresh()
}


// standard because of switch capability

// Turn off or Turn on the AC
def on(){
	logTrace( "on()")

	Boolean result = wsetACStates( null, null, sON, null, null)
	logDebug("Result : " + result)
	if(result){
		logInfo( "AC turned ON for " + gtDNI())
		generateModeEvent(sdCV(sCURM))
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
	if(mode == [sCOOL,sAUTO]){
		m= 'cooling'
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

def generateSetTempEvent(temp){
	wsendEvent(name: sTHERMSP, value: temp, descriptionText: gtDisplayName()+" set temperature is now ${temp}")
	wsendEvent(name: sTARGTEMP, value: temp, descriptionText: gtDisplayName()+" set temperature is now ${temp}")
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
void switchMode(){
	logTrace( "switchMode()")

	String currentMode = sdCV(sCURM)
	logDebug("switching AC mode from current mode: $currentMode")

	switch (currentMode){
		case sHEAT:
			modeMode(sCOOL)
			break
		case sCOOL:
			modeMode("fan")
			break
		case "fan":
			modeMode("dry")
			break
		case "dry":
			modeMode(sAUTO)
			break
		case sAUTO:
			modeMode(sHEAT)
			break
	}
}

void modeMode(String newMode){
	logTrace( "modeMode() " + newMode)

	String dni= gtDNI()
	logInfo( "Mode change request " + newMode + " for " + dni)
	Boolean result

	String LevelBefore = sdCV(sFANMODE)
	String Level; Level = LevelBefore

	if(newMode==sOFF){ // off always exists
		result = wsetACStates( null, null, sOFF, null, null)

	}else{
		Map capabilities = gtCapabilities(newMode)
		if(capabilities.remoteCapabilities != null){
			// see if fan level exists
			List<String> fanLevels = ((Map<String,List>)capabilities.remoteCapabilities).fanLevels
			logDebug("Fan levels capabilities : " + fanLevels)
			if(!(Level in fanLevels)){
				Level = GetNextFanLevel(LevelBefore,fanLevels)
				logWarn("Changing Fan : " + Level)
			}

			result = wsetACStates(newMode, null, sON, Level, null)

		}else{ // the mode does not exist, so guess one
			Map<String,Map> themodes = gtCapabilities("modes")
			List<String> lmodes; lmodes=[]
			themodes.each{
				lmodes= lmodes+[it.key] as List<String>
			}
			if(!(newMode in lmodes))
				logWarn("requested $newMode does not exist, try one of $lmodes")
//			String sMode = GetNextMode(newMode,lmodes)
//			NextMode(sMode)
			return
		}
	}

	if(result){
		generateModeEvent(newMode)
		if(LevelBefore != Level){
			generatefanLevelEvent(Level)
		}
		logInfo( "AC turned $newMode for " + dni)
	}else{
		generateErrorEvent()
	}
	cmdRefresh()
}

String GetNextMode(String mode, List<String>modes){
	logTrace( "GetNextMode " + mode + " modes: $modes")

	List<String> listMode = [sHEAT,sCOOL,/*'fan','dry',*/ sAUTO,sOFF]
	String newMode = returnNext(listMode,modes,mode)

	logDebug("Next Mode = " + newMode)

	return newMode
}

void NextMode(sMode){
	logTrace( "NextMode($sMode)")

	if(sMode != null){
		switch (sMode){
			case sHEAT:
				heat()
				break
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
			case sOFF:
				off()
				break
		}
	}
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
String returnNext(List<String> liste1, List<String> liste2, String val) throws Exception{
	try{
		Integer index = liste2.indexOf(val)

		if(index == -1) throw new Exception()
		else return liste2[liste2.indexOf(val)]
	}
	catch(ignored){
		String nval
		if(liste1.indexOf(val)+ 1 == liste1.size()){
			nval = liste1[0]
		}else{
			nval = liste1[liste1.indexOf(val) + 1]
		}
		returnNext(liste1, liste2, nval)
	}
}

void cmdRefresh(){
	logTrace( "cmdRefresh()")
	parent.afterCmdRefresh()
}

def refresh(){
	logTrace( "refresh()")
	poll()
}

void poll(){
	logTrace( "poll()")

	Map results = (Map)parent.pollChild(this)
	parseEventData(results)
}

def parseEventData(Map<String,Object> results){
	logTrace( "parseEventData()")
	logDebug("parseEventData $results")

	if(results){
		try{
			results.each{ String name, value ->

				//logDebug("name: " + name + " value: " + value)
				String desc= getThermostatDescriptionText(name, value)
				String unit; unit = sNULL
				Boolean doit; doit= true

				switch(name){
					case sTEMP:
					case "feelsLike":
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
						generateSetTempEvent(value)
						break
					case sTHERMFANMODE:
					case sFANMODE:
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

			String mode= sdCV(sCURM)
			Integer Setpoint = idCV(sTARGTEMP).toInteger()
			if(mode in [sCOOL,sAUTO])
				wsendEvent(name: sCOOLSP, value: Setpoint)
			if(mode in [sHEAT,sAUTO])
				wsendEvent(name: sHEATSP, value: Setpoint)
		}catch(e){
			logError("parse error",e)
		}
	}
}

def setFanSetpoint(itemp){
	logTrace( "setFanSetpoint($itemp)")

	Integer temp
	temp = itemp.toInteger()
	logDebug("setTemperature : " + temp )

	Boolean result = wsetACStates("fan", temp, sON, null, null)

	if(result){
		logInfo( "Fan temperature changed to " + temp + " for " + gtDNI())

		generateModeEvent("fan")

		generateSetTempEvent(temp)
	}else{
		generateErrorEvent()
	}
	cmdRefresh()
}

// Set Temperature
def setDrySetpoint(itemp){
	logTrace( "setDrySetpoint($itemp)")

	Integer temp
	temp = itemp.toInteger()
	logDebug("setTemperature : " + temp )

	Boolean result = wsetACStates("dry", temp, sON, null, null)

	if(result){
		logInfo( "Dry temperature changed to " + temp + " for " + gtDNI())

		generateModeEvent("dry")
		generateSetTempEvent(temp)
	}else{
		generateErrorEvent()
	}
	cmdRefresh()
}




void lowerTemperature(){
	logTrace( "lowerTemperature()")

	String operMode = sdCV(sCURM)

	Integer Setpoint
	Setpoint = idCV(sTARGTEMP)
	logDebug("Current target temperature = ${Setpoint}")

	Setpoint = temperatureDown(Setpoint)

	if(Setpoint == -1){
		return
	}

	switch (operMode){
		case sHEAT:
			setHeatingSetpoint(Setpoint)
			break
		case sCOOL:
			setCoolingSetpoint(Setpoint)
			break
		case "fan":
			setFanSetpoint(Setpoint)
			break
		case "dry":
			setDrySetpoint(Setpoint)
			break
		case sAUTO:
			setHeatingSetpoint(Setpoint)
			setCoolingSetpoint(Setpoint)
			break
		default:
			break
	}
}

void raiseTemperature(){
	logTrace( "raiseTemperature()"	)

	String operMode = sdCV(sCURM)

	Integer Setpoint
	Setpoint = idCV(sTARGTEMP)
	logDebug("Current target temperature = ${Setpoint}")

	Setpoint = temperatureUp(Setpoint)

	if(Setpoint == -1)
		return

	switch (operMode){
		case sHEAT:
			setHeatingSetpoint(Setpoint)
			break
		case sCOOL:
			setCoolingSetpoint(Setpoint)
			break
		case "fan":
			setFanSetpoint(Setpoint)
			break
		case "dry":
			setDrySetpoint(Setpoint)
			break
		case sAUTO:
			setHeatingSetpoint(Setpoint)
			setCoolingSetpoint(Setpoint)
			break
		default:
			break
	}
}

Integer temperatureUp(Integer temp){
	logTrace( "temperatureUp($temp)")

	List<Integer> values= GetTempValues()
	if(values==null) return -1

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

void raiseCoolSetpoint(){
	logTrace( "raiseCoolSetpoint()")

	Integer Setpoint
	Setpoint = idCV(sTARGTEMP)
	logDebug("Current target temperature = ${Setpoint}")

	Setpoint = temperatureUp(Setpoint)

	Boolean result = wsetACStates(null, Setpoint, sON, null, null)
	if(result){
		logInfo( "Cooling temperature changed to " + Setpoint + " for " + gtDNI())

		if(sdCV(sSW) == sOFF){ generateSwitchEvent(sON) }

		wsendEvent(name: sCOOLSP, value: Setpoint)
		// todo auto?

		generateSetTempEvent(Setpoint)
		logDebug("New target Temperature = ${Setpoint}")

	}else{
		generateErrorEvent()
	}
	cmdRefresh()
}

void raiseHeatSetpoint(){
	logTrace( "raiseHeatSetpoint()")

	Integer Setpoint
	Setpoint = idCV(sTARGTEMP)
	String theTemp = gtTempUnit()

	logDebug("Current target temperature = ${Setpoint}")

	Setpoint = temperatureUp(Setpoint)

	Boolean result = wsetACStates(null, Setpoint, sON, null, null)
	if(result){
		logInfo( "Heating temperature changed to " + Setpoint + " for " + gtDNI())

		if(sdCV(sSW) == sOFF){ generateSwitchEvent(sON) }

		wsendEvent(name: sHEATSP, value: Setpoint)
		// todo auto?

		generateSetTempEvent(Setpoint)

		logDebug("New target Temperature = ${Setpoint}")

	}else{
		generateErrorEvent()

	}
	cmdRefresh()

}

List<Integer> GetTempValues(String mode=sNULL){
	String sunit = gtTempUnit()
	Map capabilities = gtCapabilities( mode ?: sdCV(sCURM))
	List<Integer> values

	if(sunit == sF){
		if(((Map<String,Map>)capabilities.remoteCapabilities).temperatures.F == null){
			return null
		}
		values = ((Map<String,Map<String,Map<String,List>>>)capabilities.remoteCapabilities).temperatures.F.values
	}else{
		if(((Map<String,Map>)capabilities.remoteCapabilities).temperatures.C == null){
			return null
		}
		values = ((Map<String,Map<String,Map<String,List>>>)capabilities.remoteCapabilities).temperatures.C.values
	}
	return values
}

Integer temperatureDown(Integer temp){
	logTrace( "temperatureDown($temp)")

	List<Integer> values= GetTempValues()
	if(values==null) return -1

	List<Integer> found = values.findAll{ number -> number < temp} as List<Integer>
	logDebug("Values retrieved : " + found)
	Integer res
	if(found == null || found.empty) res = values.first() as Integer
	else res = found.last()

	logDebug("Temp before: " + temp )
	logDebug("Temp after : " + res)

	return res
}

void lowerCoolSetpoint(){
	logTrace( "lowerCoolSetpoint()")

	Integer Setpoint
	Setpoint = idCV(sTARGTEMP)
	logDebug("Current target temperature = ${Setpoint}")

	Setpoint = temperatureDown(Setpoint)

	Boolean result = wsetACStates(null, Setpoint, sON, null, null)

	if(result){
		logInfo( "Cooling temperature changed to " + Setpoint + " for " + gtDNI())

		if(sdCV(sSW) == sOFF){ generateSwitchEvent(sON) }

		wsendEvent(name: sCOOLSP, value: Setpoint)
		// todo auto

		generateSetTempEvent(Setpoint)

		logDebug("New target Temperature = ${Setpoint}")

	}else{
		logDebug("error")
		generateErrorEvent()
	}
	cmdRefresh()
}

void lowerHeatSetpoint(){
	logTrace( "lowerHeatSetpoint()")

	Integer Setpoint
	Setpoint = idCV(sTARGTEMP)

	logDebug("Current target temperature = ${Setpoint}")

	Setpoint = temperatureDown(Setpoint)

	Boolean result = wsetACStates(null, Setpoint, sON, null, null)
	if(result){
		logInfo( "Heating temperature changed to " + Setpoint + " for " + gtDNI())

		if(sdCV(sSW) == sOFF){ generateSwitchEvent(sON) }

		wsendEvent(name: sHEATSP, value: Setpoint)

		generateSetTempEvent(Setpoint)

		logDebug("New target Temperature = ${Setpoint}")

	}else{
		generateErrorEvent()

	}
	cmdRefresh()
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

		Boolean result = wsetACStates( null, null, sON, Level, null)

		if(result){
			if(sdCV(sSW) == sOFF){ generateSwitchEvent(sON) }
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

	Integer Setpoint = temp.toInteger()
	String LevelBefore = fan
	Map capabilities = gtCapabilities(newMode)
	String Level
	Level = LevelBefore
	if(capabilities.remoteCapabilities != null){
		// see if fan level exists
		List<String> fanLevels = ((Map<String,List>)capabilities.remoteCapabilities).fanLevels
		//logDebug("Fan levels capabilities : " + fanLevels)
		if(!(Level in fanLevels)){
			Level = GetNextFanLevel(Level,fanLevels)
			logWarn("Changing Fan : " + Level)
		}

		Boolean result = wsetACStates(newMode, Setpoint, sON, Level, null)

		if(result){
			generateModeEvent(newMode)
			if(LevelBefore != Level){
				generatefanLevelEvent(Level)
			}

		}else{
			generateErrorEvent()
		}
		cmdRefresh()
	}else{
		generateErrorEvent()
	}
}

def fullswing(){
	logTrace( "fullswing()")
	setSwingMode("rangeFull")
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

def setMinCoolTemp(Double value=null){
	logTrace("setMinCoolTemp($value)")
	Integer v= GetMinMax('cool', true, (value!=null), value)
	String units = gtLtScale()
	if(v!= -1){
		Integer t = idCV(sCOOLSP)
		wsendEvent(name: "minCoolTemp", value: v, unit: units)
		wsendEvent(name: "minCoolingSetpoint", value: v, unit: units)
		if(t < value){
			//setCoolingSetpoint(value) // this may turn on system
		}
	}else{
		logWarn("invalid min cool temperature $value "+units)
	}
}

def setMaxCoolTemp(Double value=null){
	logTrace("setMaxCoolTemp($value)")
	Integer v= GetMinMax('cool', false, (value!=null), value)
	String units = gtLtScale()
	if(v!= -1){
		Integer t = idCV(sCOOLSP)
		wsendEvent(name: "maxCoolTemp", value: v, unit: units)
		wsendEvent(name: "maxCoolingSetpoint", value: v, unit: units)
		if(t > value){
			//setCoolingSetpoint(value) // this may turn on system
		}
	}else{
		logWarn("invalid max cool temperature $value "+units)
	}
}

def setMinHeatTemp(Double value=null){
	logTrace("setMinHeatTemp($value)")
	Integer v= GetMinMax('heat', true, (value!=null), value)
	String units = gtLtScale()
	if(v!= -1){
		Integer t = idCV(sHEATSP)
		wsendEvent(name: "minHeatTemp", value: v, unit: units)
		wsendEvent(name: "minHeatingSetpoint", value: v, unit: units)
		if(t < value){
			//setHeatingSetpoint(value) // this may turn on system
		}
	}else{
		logWarn("invalid min heat temperature $value "+units)
	}
}

def setMaxHeatTemp(Double value=null){
	logTrace("setMaxHeatTemp($value)")
	Integer v= GetMinMax('heat', false, (value!=null), value)
	String units = gtLtScale()
	if(v!= -1){
		Integer t = idCV(sHEATSP)
		wsendEvent(name: "maxHeatTemp", value: v, unit: units)
		wsendEvent(name: "maxHeatingSetpoint", value: v, unit: units)
		if(t > value){
			//setHeatingSetpoint(value) // this may turn on system
		}
	}else{
		logWarn("invalid max heat temperature $value "+units)
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
			fanMediumLow()
			break
		case "medium":
			fanMedium()
			break
		case "mediumHigh":
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

// toggle Climate React
void toggleClimateReact(){
	String currentClimateMode = sdCV("Climate")

	logTrace( "toggleClimateReact() current Climate: $currentClimateMode")

	switch (currentClimateMode){
		case sOFF:
			setClimateReact(sON)
			break
		case sON:
			setClimateReact(sOFF)
			break
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
/*
def switchFanLevel(){
	logTrace( "switchFanLevel()")

	def currentFanMode = sdCV(sFANMODE)
	logDebug("switching fan level from current mode: $currentFanMode")
	def returnCommand

	switch (currentFanMode){
		case "low":
			returnCommand = dfanLevel("medium")
			break
		case "medium":
			returnCommand = dfanLevel("high")
			break
		case "high":
			returnCommand = dfanLevel(sAUTO)
			break
		case sAUTO:
			returnCommand = dfanLevel("quiet")
			break
		case "quiet":
			returnCommand = dfanLevel("medium_high")
			break
		case "medium_high":
			returnCommand = dfanLevel("medium_low")
			break
		case "medium_low":
			returnCommand = dfanLevel("strong")
			break
		case "strong":
			returnCommand = dfanLevel("low")
			break
	}
	returnCommand
} */

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

void switchSwing(){
	logTrace( "switchSwing()")

	String currentMode = sdCV(sSWING)
	logDebug("switching Swing mode from current mode: $currentMode")

	switch (currentMode){
		case "stopped":
			setSwingMode("fixedTop")
			break
		case "fixedTop":
			setSwingMode("fixedMiddleTop")
			break
		case "fixedMiddleTop":
			setSwingMode("fixedMiddle")
			break
		case "fixedMiddle":
			setSwingMode("fixedMiddleBottom")
			break
		case "fixedMiddleBottom":
			setSwingMode("fixedBottom")
			break
		case "fixedBottom":
			setSwingMode("rangeTop")
			break
		case "rangeTop":
			setSwingMode("rangeMiddle")
			break
		case "rangeMiddle":
			setSwingMode("rangeBottom")
			break
		case "rangeBottom":
			setSwingMode("rangeFull")
			break
		case "rangeFull":
			setSwingMode("horizontal")
			break
		case "horizontal":
			setSwingMode("both")
			break
		case "both":
			setSwingMode("stopped")
			break
	}
}

def setSwingMode(String newSwing){
	logTrace( "setSwingMode($newSwing)")

	String SwingBefore = sdCV(sSWING)
	Map capabilities = gtCapabilities(sdCV(sCURM))
	String Swing
	Swing = SwingBefore
	if(capabilities.remoteCapabilities != null){
		List<String> Swings = ((Map<String,List>)capabilities.remoteCapabilities).swing
		logDebug("Swing capabilities : " + Swings)
		if(!(Swing in Swings)){
			Swing = GetNextSwingMode(newSwing,Swings)
			logDebug("Changing Swing : " + Swing)
		}

		Boolean result = wsetACStates(null, null, sON, null, Swing)
		if(result){
			generateSwingModeEvent(Swing)
			if(sdCV(sSW) == sOFF){ generateSwitchEvent(sON) }
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
	if(name in [sTEMP,sTARGTEMP,sTHERMSP,sCOOLSP,sHEATSP]){
		return "$name is $value " + gtTempUnit()
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
	}else if(name == "voltage" || name== "battery"){
		return "Battery voltage was ${value}"
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

static String getObjType(obj){
	if(obj instanceof String){return "String"}
	else if(obj instanceof GString){return "GString"}
	else if(obj instanceof Map){return "Map"}
	else if(obj instanceof LinkedHashMap){return "LinkedHashMap"}
	else if(obj instanceof HashMap){return "HashMap"}
	else if(obj instanceof List){return "List"}
	else if(obj instanceof ArrayList){return "ArrayList"}
	else if(obj instanceof Integer){return "Integer"}
	else if(obj instanceof BigInteger){return "BigInteger"}
	else if(obj instanceof Long){return "Long"}
	else if(obj instanceof Boolean){return "Boolean"}
	else if(obj instanceof BigDecimal){return "BigDecimal"}
	else if(obj instanceof Float){return "Double"}
	else if(obj instanceof Float){return "Float"}
	else if(obj instanceof Byte){return "Byte"}
	else if(obj instanceof com.hubitat.app.DeviceWrapper)return 'Device'
	else{ return "unknown"}
}

void wsendEvent(Map prop){ sendEvent(prop) }

Boolean wsetACStates(String imode, targetTemperature, String ion, String fanLevel, String swingM){
	String mode= imode?: sdCV(sCURM)
	Integer Setpoint = targetTemperature!=null ? targetTemperature : idCV(sTARGTEMP)
	String on= ion ?: sON
	String fan = fanLevel ?: sdCV(sFANMODE)
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

Double getThermostatResolution(){
	return gtLtScale() == sC ? 0.5D : 1.0D
}

def roundDegrees(Double value){
	return gtLtScale() == sC ? Math.round(value * 2.0D) / 2.0D : Math.round(value)
}

private String gtSupportedModes(){
	logTrace("gtSupportedModes called")
	Map<String,Map> themodes = gtCapabilities("modes")
	String sModes; sModes=""
	themodes.remoteCapabilities.each{
		sModes = sModes+it.key+", "
	}
	sModes = sModes+sOFF
	logDebug("Returned modes: "+sModes)
	return sModes
}

private String gtSupportedFanModes(){		// uses the auto mode returned as the basis for the all the modes
	logTrace("gtSupportedFanModes called")
	Map<String,Map> capabilities = gtCapabilities("auto")
	String sFanModes; sFanModes=""
	if(capabilities.remoteCapabilities != null){
		// see if fan level exists
		List<String> fanLevels = ((Map<String,List>)capabilities.remoteCapabilities).fanLevels
		logDebug("Fan levels capabilities : " + fanLevels)
		sFanModes = fanLevels.join(", ")		
	}else{
		logWarn("No fan levels returned. Using defaults")
		sFanModes = sON+", circulate, "+sAUTO
	}
	logDebug("Returned modes: "+sFanModes)
	return sFanModes
}