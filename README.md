This is a WearOS Android watch app for Lucid's vehicles, allowing locking and unlocking of doors, opening and closing of frunk and trunk, honking the horn and flashing the headlights.

Battery SOC percentage is displayed at the top of the watchface. The battery icon changes to yellow, white, or green for charge levels less than or equal to 20%, greater than 20% and less than 80%, and 80% or greater respectively.

The area below the Battery SOC normally shows the car's chosen nickname. If the car is plugged in, an indicator replaces the nickname and charging power is displayed if nonzero. The charging power text and the lightning bolt to its left turn blue for RangeXChange discharging, Silver for zero charging power (plugged in but not charging), and green for charging. Tapping the charging current text issues a stop charging command to the car.

There's also a tiny yellow polling status dot that appears briefly at the very bottom of the screen each time the app polls Lucid's Cloud API. The app connects to the car through Lucid's cloud, so it works when both the car and the watch have access to the internet (WiFi or LTE) - exactly the same mechanism as Lucid's phone app.

The Frunk, Door Locks, and Trunk buttons ask for user confirmation before sending their request to the cloud for safety.

<img width="480" height="480" alt="watch_media_2026-10-27_15_17_21" src="https://github.com/user-attachments/assets/f9e99392-8319-49e6-aeda-0616acf512b3" />

