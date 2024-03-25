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
 *  2024-03-24	Significant updates, support thermostat capabilities
 *
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


		attribute "temperatureUnit","String"
		attribute "productModel","String"
		attribute "firmwareVersion","String"
		attribute "Climate","String"
		attribute "targetTemperature","Number"
		attribute "feelsLike","Number"
		attribute "Error","string"
		attribute "swing", "String"
		attribute "airConditionerMode","String"
		attribute "airConditionerFanMode","String"
		attribute "currentmode","String"
		attribute "fanLevel","String"


//		attribute "statusText","String"
		command "setAll",[
				[name:"Thermostat mode*", type: "ENUM", constraints: [
						"cool",
						"fan",
						"dry",
						"auto",
						"heat",
						sOFF ]
				],
				[name: "Temperature*", type: "NUMBER", description: ""],
				[name:"Fan level*", type: "ENUM", constraints: [
						sON,
						"circulate",
						"auto",
						"quiet",
						"low",
						"medium",
						"high",
						"strong" ]
				]
		]

		command "setMinCoolTemp", [[ name: "temperature*", type: "NUMBER"]]
		command "setMaxCoolTemp", [[ name: "temperature*", type: "NUMBER"]]
		command "setMinHeatTemp", [[ name: "temperature*", type: "NUMBER"]]
		command "setMaxHeatTemp", [[ name: "temperature*", type: "NUMBER"]]
		// command "switchFanLevel"
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
		command "fanMedium"
		command "fanHigh"
		command "fanQuiet"
		command "fanStrong"
//		command "fullswing"
		command "setAirConditionerMode", [
				[ name:"State*", type: "ENUM", constraints: [
						"cool",
						"fan",
						"dry",
						"auto",
						"heat",
						sOFF ]
				]
		]

		command "setAirConditionerFanMode", [
				[ name:"State*", type: "ENUM", constraints: [
						sON,
						"circulate",
						"auto",
						"quiet",
						"low",
						"medium",
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
						"temperature", "humidity", "feelsLike" ]
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


def installed(){
	logTrace("installed")
	// Let's just set a few things before starting
	def hubScale = getTemperatureScale()

	// Let's set all base thermostat settings
	if(hubScale == "C"){
		sendEvent(name: "minCoolTemp", value: 15.5, unit: "C") // 60°F
		sendEvent(name: "minCoolingSetpoint", value: 15.5, unit: "C") // Google
		sendEvent(name: "maxCoolTemp", value: 35.0, unit: "C") // 95°F
		sendEvent(name: "maxCoolingSetpoint", value: 35.0, unit: "C") // Google
		sendEvent(name: "minHeatTemp", value: 1.5, unit: "C") // 35°F
		sendEvent(name: "minHeatingSetpoint", value: 1.5, unit: "C") // Google
		sendEvent(name: "maxHeatTemp", value: 26.5, unit: "C") // 80°F
		sendEvent(name: "maxHeatingSetpoint", value: 26.5, unit: "C") // Google
		sendEvent(name: "temperature", value: 22.0, unit: "C") // 72°F
		sendEvent(name: "heatingSetpoint", value: 21.0, unit: "C") // 70°F
		sendEvent(name: "coolingSetpoint", value: 24.5, unit: "C") // 76°F
		sendEvent(name: "thermostatSetpoint", value: 21.0, unit: "C") // 70°F

		sendEvent(name: "targetTemperature", value: 21.0, unit: "C") // 70°F
//		sendEvent(name: "thermostatThreshold", value: 0.5, unit: "C") // Set by user
	}else{
		sendEvent(name: "minCoolTemp", value: 60, unit: "F") // 15.5°C
		sendEvent(name: "minCoolingSetpoint", value: 60, unit: "F") // Google
		sendEvent(name: "maxCoolTemp", value: 95, unit: "F") // 35°C
		sendEvent(name: "maxCoolingSetpoint", value: 95, unit: "F") // Google
		sendEvent(name: "minHeatTemp", value: 35, unit: "F") // 1.5°C
		sendEvent(name: "minHeatingSetpoint", value: 35, unit: "F") // Google
		sendEvent(name: "maxHeatTemp", value: 80, unit: "F") // 26.5°C
		sendEvent(name: "maxHeatingSetpoint", value: 80, unit: "F") // Google
		sendEvent(name: "temperature", value: 72, unit: "F") // 22°C
		sendEvent(name: "heatingSetpoint", value: 70, unit: "F") // 21°C
		sendEvent(name: "coolingSetpoint", value: 76, unit: "F") // 24.5°C
		sendEvent(name: "thermostatSetpoint", value: 70, unit: "F") // 21°C

		sendEvent(name: "targetTemperature", value: 70, unit: "F") // 21°C
//		sendEvent(name: "thermostatThreshold", value: 1.0, unit: "F") // Set by user
	}
	sendEvent(name: "switch", value: sOFF)
	sendEvent(name: "thermostatFanMode", value: "auto")
	sendEvent(name: "thermostatMode", value: sOFF)
	sendEvent(name: "thermostatOperatingState", value: "idle")
	sendEvent(name: "supportedThermostatModes", value: ["heat", "cool", "auto", sOFF])
	sendEvent(name: 'supportedThermostatFanModes', value: [sON, "circulate", "auto"])
//	sendEvent(name: "maxUpdateInterval", value: 65)
//	sendEvent(name: "lastTempUpdate", value: new Date() )

	sendEvent(name: "fanLevel", value: "auto")
	sendEvent(name: 'airConditionerFanMode', value: "auto")
	sendEvent(name: "swing", value: "stopped")
	sendEvent(name: "currentmode", value: sOFF)
	sendEvent(name: 'airConditionerMode', value: sOFF)
}

def updated(){
	logTrace("updated")
	//if(advLogsActive()){ runIn(1800, "logsOff") }
	if(advLogsActive()){ runIn(28800, "logsOff") }
}

Boolean advLogsActive(){ return ((Boolean)settings.logDebug || (Boolean)settings.logTrace) }
void logsOff(){
	device.updateSetting("logDebug",[value:sFALSE,type:"bool"])
	device.updateSetting("logTrace",[value:sFALSE,type:"bool"])
	log.debug "Disabling debug logs"
}



String gtDisplayName(){ return (String)device.displayName }
String gtDNI(){ return (String)device.deviceNetworkId }
String gtTempUnit(){ return (String)device.currentValue("temperatureUnit") }


// Standard thermostat commands

def off(){
	logTrace( "off()")
	modeMode(sOFF)
}

def heat(){
	logTrace( "heat()")
	modeMode("heat")
}

def cool(){
	logTrace( "cool()")
	modeMode("cool")
}

def auto(){
	logTrace( "auto()")
	modeMode("auto")
}

def fanAuto(){
	logTrace( "fanAuto()")
	dfanLevel("auto")
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
		case "auto":
			fanAuto()
			break
		default:
			generateErrorEvent()
	}
}

@CompileStatic
def setThermostatMode(mode){
	logTrace( "setThermostatMode($mode)")

	switch (mode){
		case "cool":
			cool()
			break
			//case "fan":
			//	returnCommand = modeFan()
			//	break
			//case "dry":
			//	returnCommand = modeDry()
			//	break
		case "auto":
			auto()
			break
		case "heat":
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

	Boolean result = wsetACStates("heat", temp, sON, null, null)

	if(result){
		logInfo( "Heating temperature changed to " + temp + " for " + gtDNI())

		generateModeEvent("heat")
		sendEvent(name: 'heatingSetpoint', value: temp)
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

	Boolean result = wsetACStates("cool", temp, sON, null, null)

	if(result){
		logInfo( "Cooling temperature changed to " + temp + " for " + gtDNI())

		generateModeEvent("cool")

		sendEvent(name: 'coolingSetpoint', value: temp)
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
		generateModeEvent(device.currentValue("currentmode"))
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

def fanMedium(){
	logTrace( "fanMedium()")
	dfanLevel("medium")
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
	String LevelBefore = device.currentValue("fanLevel")
	sendEvent(name: "fanLevel", value: Level, descriptionText: "Fan mode is now ${Level}")
	if(LevelBefore!=Level) logInfo( "Fan level changed to " + Level + " for " + gtDNI())
	sendEvent(name: 'airConditionerFanMode', value: Level)
	String mode
	mode = Level
	mode = (mode in ["high", "medium", "strong"]) ? sON : mode
	mode = (mode in ["low", "quiet"]) ? "circulate" : mode
	mode = !(mode in [sON, "circulate"]) ? "auto" : mode
	sendEvent(name: 'thermostatFanMode', value: mode)
}

void generateModeEvent(String mode, Boolean doSW=true){
	if(mode != sOFF) sendEvent(name: "currentmode", value: mode, descriptionText: "AC mode is now ${mode}")
	sendEvent(name: 'airConditionerMode', value: mode)

	String m
	if(mode in ['heat','cool','auto',sOFF])
		m= mode
	else if(mode in ["dry"]) {
		m= 'cool'
	}else // 'fan'
		m= 'off'
	sendEvent(name: "thermostatMode", value: m, descriptionText: "AC mode is now ${m}")

	m= sBLANK
	if(mode in ["cool","dry"]){
		m= 'cooling'
	}else if(mode in ["heat","auto"]){
		m= 'heating'
	}else if(mode=="fan"){
		m= 'fan only'
	}else{
		m= 'idle'
	}
	sendEvent(name: 'thermostatOperatingState', value: m)

	if(doSW) generateSwitchEvent(mode==sOFF ? sOFF : sON)
}

void generateErrorEvent(){
	logError(gtDisplayName()+" FAILED to set the AC State")
//	sendEvent(name: "Error", value: "Error", descriptionText: gtDisplayName()+" FAILED to set or get the AC State")
}

def generateSetTempEvent(temp){
	sendEvent(name: "thermostatSetpoint", value: temp, descriptionText: gtDisplayName()+" set temperature is now ${temp}")
	sendEvent(name: "targetTemperature", value: temp, descriptionText: gtDisplayName()+" set temperature is now ${temp}")
}

void generateSwitchEvent(String status){
	sendEvent(name: "switch", value: status, descriptionText: gtDisplayName()+" is now ${status}")
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

	String currentMode = device.currentValue("currentmode")
	logDebug("switching AC mode from current mode: $currentMode")

	switch (currentMode){
		case "heat":
			modeMode("cool")
			break
		case "cool":
			modeMode("fan")
			break
		case "fan":
			modeMode("dry")
			break
		case "dry":
			modeMode("auto")
			break
		case "auto":
			modeMode("heat")
			break
	}
}

void modeMode(String newMode){
	logTrace( "modeMode() " + newMode)

	String dni= gtDNI()
	logInfo( "Mode change request " + newMode + " for " + dni)
	Boolean result

	String LevelBefore = device.currentValue("fanLevel")
	String Level; Level = LevelBefore

	if(newMode==sOFF){ // off always exists
		result = wsetACStates( null, null, sOFF, null, null)

	}else{
		Map capabilities = (Map)parent.getCapabilities(dni,newMode)
		if(capabilities.remoteCapabilities != null){
			// see if fan level exists
			List<String> fanLevels = ((Map<String,List>)capabilities.remoteCapabilities).fanLevels
			//logDebug("Fan levels capabilities : " + fanLevels)
			if(!(Level in fanLevels)){
				Level = GetNextFanLevel(LevelBefore,fanLevels)
				logWarn("Changing Fan : " + Level)
			}

			result = wsetACStates(newMode, null, sON, Level, null)

		}else{ // the mode does not exist, so guess one
			Map<String,Map> themodes = (Map)parent.getCapabilities(dni,"modes")
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

	List<String> listMode = ['heat','cool',/*'fan','dry',*/ 'auto',sOFF]
	String newMode = returnNext(listMode,modes,mode)

	logDebug("Next Mode = " + newMode)

	return newMode
}

void NextMode(sMode){
	logTrace( "NextMode($sMode)")

	if(sMode != null){
		switch (sMode){
			case "heat":
				heat()
				break
			case "cool":
				cool()
				break
			case "fan":
				modeFan()
				break
			case "dry":
				modeDry()
				break
			case "auto":
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

	List<String> listFanLevel = ['low','medium','high','auto','quiet','medium_high','medium_low','strong']
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
					case "voltage":
						unit="mA"
						break
					case "battery":
					case "powerSource":
					case "Climate":
					case "temperatureUnit":
					case "temperature":
					case "feelsLike":
					case "humidity":
					case "productModel":
					case "firmwareVersion":
					case "Error":
						break
					case "updated":
						doit= false
						break
					case sON:
					case "switch":
						generateSwitchEvent(value as String)
						if(value == sOFF) generateModeEvent(value as String,false)
						doit= false
						break
					case "thermostatMode":
					case "currentmode":
						// this presumes switch was run first (above)
						if(device.currentValue("switch") != sOFF){
							generateModeEvent(value as String,false)
						} else if(device.currentValue("currentmode") != value)
							sendEvent(name: "currentmode", value: value, descriptionText: "AC mode is now ${value}")
						doit= false
						break
					case  "coolingSetpoint":
					case  "heatingSetpoint":
					case  "thermostatSetpoint":
					case  "targetTemperature":
						generateSetTempEvent(value)
						break
					case "thermostatFanMode":
					case "fanLevel":
						generatefanLevelEvent(value as String)
						doit= false
						break
					case "swing":
						generateSwingModeEvent(value as String)
						doit= false
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
					sendEvent(evt)
				}
			}

			String mode= device.currentValue("currentmode")
			Integer Setpoint = device.currentValue("targetTemperature").toInteger()
			if(mode in ["cool","auto"])
				sendEvent(name: 'coolingSetpoint', value: Setpoint)
			if(mode in ["heat","auto"])
				sendEvent(name: 'heatingSetpoint', value: Setpoint)
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

	String operMode = device.currentValue("currentmode")

	Integer Setpoint
	Setpoint = device.currentValue("targetTemperature").toInteger()
	logDebug("Current target temperature = ${Setpoint}")

	Setpoint = temperatureDown(Setpoint)

	if(Setpoint == -1){
		return
	}

	switch (operMode){
		case "heat":
			setHeatingSetpoint(Setpoint)
			break
		case "cool":
			setCoolingSetpoint(Setpoint)
			break
		case "fan":
			setFanSetpoint(Setpoint)
			break
		case "dry":
			setDrySetpoint(Setpoint)
			break
		case "auto":
			setHeatingSetpoint(Setpoint)
			setCoolingSetpoint(Setpoint)
			break
		default:
			break
	}
}

void raiseTemperature(){
	logTrace( "raiseTemperature()"	)

	String operMode = device.currentValue("currentmode")

	Integer Setpoint
	Setpoint = device.currentValue("targetTemperature").toInteger()
	logDebug("Current target temperature = ${Setpoint}")

	Setpoint = temperatureUp(Setpoint)

	if(Setpoint == -1)
		return

	switch (operMode){
		case "heat":
			setHeatingSetpoint(Setpoint)
			break
		case "cool":
			setCoolingSetpoint(Setpoint)
			break
		case "fan":
			setFanSetpoint(Setpoint)
			break
		case "dry":
			setDrySetpoint(Setpoint)
			break
		case "auto":
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
	Setpoint = device.currentValue("targetTemperature").toInteger()
	logDebug("Current target temperature = ${Setpoint}")

	Setpoint = temperatureUp(Setpoint)

	Boolean result = wsetACStates(null, Setpoint, sON, null, null)
	if(result){
		logInfo( "Cooling temperature changed to " + Setpoint + " for " + gtDNI())

		if(device.currentValue("switch") == sOFF){ generateSwitchEvent(sON) }

		sendEvent(name: 'coolingSetpoint', value: Setpoint)
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
	Setpoint = device.currentValue("targetTemperature").toInteger()
	String theTemp = gtTempUnit()

	logDebug("Current target temperature = ${Setpoint}")

	Setpoint = temperatureUp(Setpoint)

	Boolean result = wsetACStates(null, Setpoint, sON, null, null)
	if(result){
		logInfo( "Heating temperature changed to " + Setpoint + " for " + gtDNI())

		if(device.currentValue("switch") == sOFF){ generateSwitchEvent(sON) }

		sendEvent(name: 'heatingSetpoint', value: Setpoint)
		// todo auto?

		generateSetTempEvent(Setpoint)

		logDebug("New target Temperature = ${Setpoint}")

	}else{
		generateErrorEvent()

	}
	cmdRefresh()

}

List<Integer> GetTempValues(){
	String sunit = gtTempUnit()
	Map capabilities = (Map)parent.getCapabilities(gtDNI(), device.currentValue("currentmode"))
	List<Integer> values

	if(sunit == "F"){
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
	Setpoint = device.currentValue("targetTemperature").toInteger()
	logDebug("Current target temperature = ${Setpoint}")

	Setpoint = temperatureDown(Setpoint)

	Boolean result = wsetACStates(null, Setpoint, sON, null, null)

	if(result){
		logInfo( "Cooling temperature changed to " + Setpoint + " for " + gtDNI())

		if(device.currentValue("switch") == sOFF){ generateSwitchEvent(sON) }

		sendEvent(name: 'coolingSetpoint', value: Setpoint)
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
	Setpoint = device.currentValue("targetTemperature").toInteger()

	logDebug("Current target temperature = ${Setpoint}")

	Setpoint = temperatureDown(Setpoint)

	Boolean result = wsetACStates(null, Setpoint, sON, null, null)
	if(result){
		logInfo( "Heating temperature changed to " + Setpoint + " for " + gtDNI())

		if(device.currentValue("switch") == sOFF){ generateSwitchEvent(sON) }

		sendEvent(name: 'heatingSetpoint', value: Setpoint)

		generateSetTempEvent(Setpoint)

		logDebug("New target Temperature = ${Setpoint}")

	}else{
		generateErrorEvent()

	}
	cmdRefresh()
}


def dfanLevel(String newLevel){
	logTrace( "dfanLevel " + newLevel)

	Map capabilities = (Map)parent.getCapabilities(gtDNI(), device.currentValue("currentmode"))
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
			if(device.currentValue("switch") == sOFF){ generateSwitchEvent(sON) }
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
	Map capabilities = (Map)parent.getCapabilities(gtDNI(),newMode)
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

def setMinCoolTemp(Double value){
	//List<Integer> values= GetTempValues()
	//if(values==null) return -1
	def units = getTemperatureScale()
	logTrace("setMinCoolTemp($value)")
	def t = device.currentValue("coolingSetpoint")
	sendEvent(name: "minCoolTemp", value: value, unit: units)
	sendEvent(name: "minCoolingSetpoint", value: value, unit: units)
	if(t < value){
		setCoolingSetpoint(value)
	}
}

def setMaxCoolTemp(Double value){
	//List<Integer> values= GetTempValues()
	//if(values==null) return -1
	def units = getTemperatureScale()
	logTrace("setMaxCoolTemp($value)")
	def t = device.currentValue("coolingSetpoint")
	sendEvent(name: "maxCoolTemp", value: value, unit: units)
	sendEvent(name: "maxCoolingSetpoint", value: value, unit: units)
	if(t > value){
		setCoolingSetpoint(value)
	}
}

def setMinHeatTemp(Double value){
	//List<Integer> values= GetTempValues()
	//if(values==null) return -1
	def units = getTemperatureScale()
	logTrace("setMinHeatTemp($value)")
	def t = device.currentValue("heatingSetpoint")
	sendEvent(name: "minHeatTemp", value: value, unit: units)
	sendEvent(name: "minHeatingSetpoint", value: value, unit: units)
	if(t < value){
		setHeatingSetpoint(value)
	}
}

def setMaxHeatTemp(Double value){
	//List<Integer> values= GetTempValues()
	//if(values==null) return -1
	def units = getTemperatureScale()
	logTrace("setMaxHeatTemp($value)")
	def t = device.currentValue("heatingSetpoint")
	sendEvent(name: "maxHeatTemp", value: value, unit: units)
	sendEvent(name: "maxHeatingSetpoint", value: value, unit: units)
	if(t > value){
		setHeatingSetpoint(value)
	}
}

void setAirConditionerMode(String modes){
	logTrace( "setAirConditionerMode($modes)")

	String currentMode = device.currentValue("currentmode")
	logDebug("switching AC mode from current mode: $currentMode")

	switch (modes){
		case "cool":
			cool()
			break
		case "fanOnly":
		case "fan":
			modeFan()
			break
		case "dry":
			modeDry()
			break
		case "auto":
			auto()
			break
		case "heat":
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
		case "auto":
			fanAuto()
			break
		case "quiet":
			fanQuiet()
			break
		case "low":
			fanLow()
			break
		case "medium":
			fanMedium()
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
	String currentClimateMode = device.currentValue("Climate")

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
		sendEvent(name: 'Climate', value: ClimateState)
	}else{
		generateErrorEvent()
	}
	cmdRefresh()
}

def setClimateReactConfiguration(String on_off, String stype,ilowThres, ihighThres,String lowState,String highState){
	///////////////////////////////////////////////
	// on_off : enable climate react string on/off
	// stype : possible values are "temperature", "humidity" or "feelsLike"
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
	if(getTemperatureScale() == "F"){
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

	Boolean on= (on_off=='on')

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
		sendEvent(name: 'Climate', value: on_off)
	}else{
		generateErrorEvent()
	}
	cmdRefresh()
}
/*
def switchFanLevel(){
	logTrace( "switchFanLevel()")

	def currentFanMode = device.currentValue("fanLevel")
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
			returnCommand = dfanLevel("auto")
			break
		case "auto":
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

	String currentMode = device.currentValue("swing")
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

	String SwingBefore = device.currentValue("swing")
	Map capabilities = (Map)parent.getCapabilities(gtDNI(), device.currentValue("currentmode"))
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
			if(device.currentValue("switch") == sOFF){ generateSwitchEvent(sON) }
		}else{
			generateErrorEvent()
		}
		cmdRefresh()
	}else{
		generateErrorEvent()
	}
}

def generateSwingModeEvent(String mode){
	String SwingBefore = device.currentValue("swing")
	if(SwingBefore!=mode) logInfo( "Swing mode changed to " + mode + " for " + gtDNI())
	sendEvent(name: "swing", value: mode, descriptionText: gtDisplayName()+" swing mode is now ${mode}")
}

String getThermostatDescriptionText(String name, value){
	if(name in ["temperature","targetTemperature","thermostatSetpoint","coolingSetpoint","heatingSetpoint"]){
		return "$name is $value " + gtTempUnit()
	}else if(name == "humidity"){
		return "$name is $value %"
	}else if(name == "fanLevel"){
		return "fan level is $value"
	}else if(name == sON){
		return "switch is $value"
	}else if(name in ["mode","thermostatMode", "thermostatOperatingState","thermostatFanMode"]){
		return "$name is ${value}"
	}else if(name == "currentmode"){
		return "thermostat mode was ${value}"
	}else if(name == "powerSource"){
		return "power source mode was ${value}"
	}else if(name == "Climate"){
		return "Climate React was ${value}"
	}else if(name == "temperatureUnit"){
		return "thermostat unit was ${value}"
	}else if(name == "voltage"){
		return "Battery voltage was ${value}"
	}else if(name == "battery"){
		return "Battery was ${value}"
	}else if(name == "voltage" || name== "battery"){
		return "Battery voltage was ${value}"
	}else if(name == "swing"){
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
		name = "switch"
		value = description?.endsWith(" 1") ? sON : sOFF
	}else if(description?.startsWith("temperature")){
		logDebug("Temperature")
		name = "temperature"
		value = device.currentValue("temperature")
	}else if(description?.startsWith("humidity")){
		logDebug("Humidity")
		name = "humidity"
		value = device.currentValue("humidity")
	}else if(description?.startsWith("targetTemperature")){
		logDebug("targetTemperature")
		name = "targetTemperature"
		value = device.currentValue("targetTemperature")
	}else if(description?.startsWith("fanLevel")){
		logDebug("fanLevel")
		name = "fanLevel"
		value = device.currentValue("fanLevel")
	}else if(description?.startsWith("currentmode")){
		logDebug("mode")
		name = "currentmode"
		value = device.currentValue("currentmode")
	}else if(description?.startsWith(sON)){
		logDebug(sON)
		name = sON
		value = device.currentValue(sON)
	}else if(description?.startsWith("switch")){
		logDebug("switch")
		name = "switch"
		value = device.currentValue(sON)
	}else if(description?.startsWith("temperatureUnit")){
		logDebug("temperatureUnit")
		name = "temperatureUnit"
		value = gtTempUnit()
	}else if(description?.startsWith("Error")){
		logDebug("Error")
		name = "Error"
		value = device.currentValue("Error")
	}else if(description?.startsWith("voltage")){
		logDebug("voltage")
		name = "voltage"
		value = device.currentValue("voltage")
	}else if(description?.startsWith("battery")){
		logDebug("battery")
		name = "battery"
		value = device.currentValue("battery")
	}else if(description?.startsWith("swing")){
		logDebug("swing")
		name = "swing"
		value = device.currentValue("swing")
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


Boolean wsetACStates(String imode, targetTemperature, String ion, String fanLevel, String swingM){
	String mode= imode?: device.currentValue("currentmode")
	Integer Setpoint = targetTemperature!=null ? targetTemperature : device.currentValue("targetTemperature")
	String on= ion ?: sON
	String fan = fanLevel ?: device.currentValue("fanLevel")
	String swing = swingM ?: device.currentValue("swing")

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

Double getThermostatResolution(){
	return getTemperatureScale() == "C" ? 0.5D : 1.0D
}

def roundDegrees(Double value){
	return getTemperatureScale() == "C" ? Math.round(value * 2.0D) / 2.0D : Math.round(value)
}
