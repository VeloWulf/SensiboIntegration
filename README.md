# SensiboIntegration
Hubitat integration with Sensibo AC controllers  
Based on work by Brian Li for (https://github.com/joyfulhouse/SensiboIntegration)

## Files

| File | Type | Required |
|---|---|---|
| `smartapps/SensiboIntegration.groovy` | App (Sensibo Integration) | Yes |
| `devicetypes/SensiboPod.groovy` | Driver (SensiboPod), one device per pod | Yes |
| `devicetypes/SensiboEventStream.groovy` | Driver (Sensibo Event Stream) | Only for real-time updates |

Get your API key at https://home.sensibo.com/me/api and enter it in the app.

## Real-time updates (optional)

Sensibo offers an event stream that reports changes as they happen. To use it:

1. Install the **Sensibo Event Stream** driver.
2. In the app, turn on **Real-time updates** and click **Done**. The app creates a "Sensibo Event Stream" device.

Changes made in the Sensibo app, by the remote or by Climate React show up in Hubitat within seconds, and temperature/humidity update about once a minute. While the stream is connected, full polls run every 15 minutes (30 if that interval is chosen), as Sensibo asks. If the stream drops, polling returns to the chosen interval until it reconnects.

The app page shows the stream status, the last event and the current refresh interval; the Sensibo Event Stream device has `streamStatus` and `lastEvent` attributes and Connect / Disconnect commands. Hubitat logs "STOP: EventStream Stopped" from time to time while the stream keeps working; the driver only reconnects if no events arrive afterwards.

## Changes in the 2026-11 update (by nh.schottfam)

Behaviour you may notice:

- **Setpoints no longer turn the AC on or change its mode.** `setHeatingSetpoint` / `setCoolingSetpoint` store the value and send it only when the unit is on in that mode (or auto); a stored setpoint is applied when that mode is selected or the unit is turned on. Raise/lower changes the temperature in the current mode only.
- **Fan level and swing changes no longer turn the AC on.** Off, setpoint, fan level and swing changes send only that value, so they don't overwrite changes made elsewhere.
- **Setpoints are limited** to the temperatures the AC supports in that mode and to the device's min/max heating and cooling setpoints (`setMinHeatTemp`, `setMaxCoolTemp`, ...; `resetMinMax` sets them to the AC's full range). Out-of-range values use the nearest allowed one, with a log warning.
- **Temperatures use the hub's scale** (C or F), converted to and from the pod's unit.
- **Refresh** on a device fetches fresh data from Sensibo; poll uses the app's cached data.
- **Refresh interval:** 5, 10 (default), 15 or 30 minutes. The 1 minute option was removed because Sensibo rate-limits frequent polling; if Sensibo returns a rate-limit error, polling pauses for 5 to 30 minutes.
- In auto mode, `thermostatOperatingState` shows heating, cooling or idle based on room temperature vs target.
- `supportedThermostatModes` and `supportedThermostatFanModes` are proper JSON lists. Existing devices update the next time their preferences are saved (or use the **Refresh Supported Modes** command).

Plus many fixes; see the change history at the top of each file.

Version 0.2 reintroduces the thermostat function (thanks to some fine work from nh.schottfam). Upgrades from version 0.1 should take into account the following manual actions:

    - to use the Thermostat features in dashboards you should either:
        -- recreate the sensibo pod devices using the app: remove and then readd (stable option but breaks rules so please ensure that you have a copy of rules before performing this step), OR
        -- uncomment the line capability "Initialize" in the driver code, restart your hub and then comment out the line again (developer option - USE AT OWN RISK)
    - any rules using the modeCool custom command should be changed to use the Cool custom command (see notes)

Notes:

    1. The previous version used a custom command called modeCool, which was the instruction to the air conditioner unit to switch to Cooling. The reintroduction of the thermostat function brings with it a hard coded function called Cool that does the exact same thing. I toyed with keeping the modeCool command as well for backwards compatibililty but in the end decided to keep the driver commands cleaner by not having this so a manual action is required to update any hub rules, etc. that use this modeCool custom command
