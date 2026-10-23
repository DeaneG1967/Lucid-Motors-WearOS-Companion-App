This is a WearOS Android watch app for Lucid's vehicles, allowing locking and unlocking of doors, opening and closing of frunk and trunk, honking the horn and flashing the headlights.
Battery SOC percentage is displayed at the top of the watchface.
The area below the Battery SOC normally shows the car's chosen nickname. If the car is plugged in, an indicator replaces the nickname and charging current is displayed if nonzero.

The line of text just below the current charge percentage shows the car's nickname when not plugged in. Otherwise, it displays "Plugged In" if plugged in but not charging, or a lightning bolt icon and the current charging speed if charging.

There's also a tiny yellow polling status dot that appears briefly at the very bottom of the screen each time the app polls Lucid's Cloud API. The app connects to the car through Lucid's cloud, so it works when both the car and the watch have access to the internet (WiFi or LTE) - exactly the same mechanism as Lucid's phone app.

The Frunk, Door Locks, and Trunk buttons ask for user confirmation before sending their request to the cloud for safety.

<img width="480" height="480" alt="watch_media_2026-10-20_15_15_17" src="https://github.com/user-attachments/assets/d72fff04-ba5d-4f21-8d15-da65875afff8" />
