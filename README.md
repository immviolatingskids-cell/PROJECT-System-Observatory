# System Observatory — Telemetry Probe v0.2

A temporary, local-only native Android diagnostic app. It targets API 36, uses Kotlin and Jetpack Compose, and requests no internet permission.

## Build and install

Open this directory in Android Studio with Android SDK 36 installed. Use Build > Build APK(s), then install `app/build/outputs/apk/debug/app-debug.apk` with `adb install -r app/build/outputs/apk/debug/app-debug.apk` or Run. With Gradle 8.13 installed, run `gradle testDebugUnitTest assembleDebug`. GitHub Actions also runs the tests and uploads the debug APK as an artifact after each push.

## Sources and limitations

Overview: Android Build and SystemClock values; `/proc/version` where readable. Battery: ACTION_BATTERY_CHANGED and BatteryManager. Memory: ActivityManager. Storage: StatFs for the app data filesystem, not all physical partitions. Network: ConnectivityManager and NetworkCapabilities. Bandwidth values are Android estimates, not measured throughput. Thermal: PowerManager status and headroom. CPU and raw kernel sources: read-only `/proc` and selected `/sys` nodes after explicit root request.

Tap **REQUEST ROOT ACCESS** on the ROOT tab to start an interactive `su` shell and let your installed root manager show its normal permission dialog. The app verifies `id -u` returns `0` before scanning privileged sources. The session accepts only typed read-only operations (`id -u`, `cat`, `ls -1`, and optional `su -v`) on allowlisted `/proc` and `/sys` paths. On grant, it immediately rescans `/proc/version`, `/proc/cpuinfo`, `/proc/stat`, `/proc/meminfo`, `/proc/uptime`, CPU frequency nodes, thermal zones, power supplies, and devfreq devices. Root denial leaves standard telemetry usable. Raw thermal zones keep their kernel type and path; names are not inferred. Some sysfs files may remain inaccessible even with root. Missing or unsupported measurements are marked UNAVAILABLE or ERROR rather than zero.

CPU usage is derived from two `/proc/stat` snapshots. Storage used space and battery percentage are derived. Thermal Celsius is derived only when the raw value safely fits degrees or millidegrees. **Export Probe Report** saves human-readable JSON through Android's document picker; its `root` object records the actual request result, UID, provider hint, version, session status and error. Every reading includes raw/normalized values, unit, source, classification, timestamp, availability, and error.
