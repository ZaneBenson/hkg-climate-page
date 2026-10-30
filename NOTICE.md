# Notices

## HMG Leafia Plus (GPL-3.0)

https://github.com/VysseCodes/HMG-Leafia-Plus. The read-only USB kit follows the Leafia
kit's approach. Copied from it at commit 08f4436, each file saying so in its header:
`tools/lpatch/` (the `lpatch` tool: `lpatch.c`, `build.sh`, `test.sh`), `tools/mkdelta.py`
and `tools/requirements.txt`. We changed `lpatch zipadd` to stream the APK and the dex
instead of loading them whole, so it can rebuild the 162 MB split-screen panel APK, and
extended `test.sh` for that. Much of the first research on the seat and climate services
was done alongside it.

## hkmc-coldstart-heat (MIT)

https://github.com/eclipseempire/hkmc-coldstart-heat by eclipseempire. First found the
stock seat call this plugin uses (`com.hkmc.micom.usm`, `setFrontSeatHeatDrvStep` /
`setFrontSeatHeatAstStep`) and the steering-wheel heat path.

## NEMESIS (AGPL-3.0)

https://github.com/xwtk/hmg_nemesis by xwtk. This plugin installs and runs through NEMESIS.
