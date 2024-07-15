# SensiboIntegration
Hubitat integration with Sensibo AC controllers  
Based on work by Brian Li for (https://github.com/joyfulhouse/SensiboIntegration)

Version 0.2 reintroduces the thermostat function (thanks to some fine work from nh.schottfam). Upgrades from version 0.1 should take into account the following manual actions:

    - to use the Thermostat features in dashboards you should either:
        -- recreate the sensibo pod devices using the app: remove and then readd (stable option but breaks rules so please ensure that you have a copy of rules before performing this step), OR
        -- uncomment the line capability "Initialize" in the driver code, restart your hub and then comment out the line again (developer option - USE AT OWN RISK)
    - any rules using the modeCool custom command should be changed to use the Cool custom command (see notes)

Notes:

    1. The previous version used a custom command called modeCool, which was the instruction to the air conditioner unit to switch to Cooling. The reintroduction of the thermostat function brings with it a hard coded function called Cool that does the exact same thing. I toyed with keeping the modeCool command as well for backwards compatibililty but in the end decided to keep the driver commands cleaner by not having this so a manual action is required to update any hub rules, etc. that use this modeCool custom command