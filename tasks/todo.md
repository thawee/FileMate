# Fix: Hotspot IP Detection & Dynamic Network IP Refresh

## Root Cause Analysis
1. **Cellular (Mobile Carrier) IP Leakage:**
   - In `getLocalIpAddresses()`, network interfaces are enumerated without checking `networkInterface.isUp` or distinguishing between local LAN/hotspot interfaces and cellular WAN interfaces.
   - Cellular carrier interfaces (`rmnet*`, `ccmni*`, `pdp*`, `wwan*`, `ppp*`) assign private/CGNAT IPv4 addresses (e.g. `10.112.12.215`). Mobile carriers block inbound traffic, and Android's tethering firewall blocks hotspot clients from accessing cellular IPs.
   - These cellular IPs were accepted and prioritized or fallen back to as the primary server IP.
2. **Stale/Static IP Caching in UI:**
   - In `MainActivity.kt`: `val localIps = remember { getLocalIpAddresses() }` had no keys or triggers.
   - When the app is opened before turning on the Hotspot, `localIps` was evaluated once and cached permanently.
   - Turning on the hotspot, starting the server, or switching networks never invalidated or updated `localIps`, leaving stale or cellular IPs visible on the QR code and URL cards.
3. **No Automatic Network Change Listener:**
   - The UI lacked a lifecycle / connectivity listener (`ConnectivityManager.NetworkCallback` or resume effect) to refresh IP addresses when the user turns on Hotspot or connects to Wi-Fi while using the app.

## Tasks
- [x] **1. Enhance `getLocalIpAddresses()` with Cellular Filtering & Prioritization (`MainActivity.kt` & `NetworkUtils.kt`)**
  - Checked `networkInterface.isUp` and non-loopback.
  - Excluded cellular carrier WAN interfaces (`rmnet`, `ccmni`, `pdp`, `ppp`, `wwan`, `cellular`, `qmimux`, `clat`, `radio`) from local file sharing.
  - Prioritized Hotspot / AP interfaces (`ap`, `softap`, `swlan`, `tether`) and Wi-Fi (`wlan`, `eth`, `rndis`).
  - Prioritized standard local gateway subnets (e.g., `192.168.43.1`).
- [x] **2. Make IP Detection Dynamic & Reactive (`MainActivity.kt`)**
  - Replaced static `remember { getLocalIpAddresses() }` with reactive `networkAddresses` state.
  - Automatically refreshes IP list upon starting/stopping the server.
  - Implemented `DisposableEffect` with `ConnectivityManager.NetworkCallback` and app lifecycle `ON_RESUME` to auto-refresh local IPs whenever network interfaces change.
  - Added a manual refresh button on the URL card for instant user verification.
  - Added multi-interface filter chips when multiple local networks exist (e.g. Hotspot + Wi-Fi).
  - Added an offline warning if no active Wi-Fi or Hotspot is available.
- [x] **3. Verification & Testing**
  - Added unit tests in `NetworkUtilsTest.kt` for cellular filtering and hotspot prioritization.
  - Ran `./gradlew testDebugUnitTest` — all tests passed.
  - Ran `./gradlew assembleDebug` — APK built cleanly with 0 errors.

## Review & Summary
- **Root Cause Identified:**
  - Android assigned the cellular mobile data interface (`rmnet_data0`) an internal CGNAT IP (`10.112.12.215`).
  - Because `getLocalIpAddresses()` did not exclude cellular interfaces and `WebFSScreen` cached the IP list with `remember { getLocalIpAddresses() }` on startup, the cellular IP was mistakenly displayed instead of the Hotspot address.
  - Inbound traffic to cellular carrier IPs is blocked by carriers and Android firewall.
- **Solution Implemented:**
  - Created `NetworkUtils.kt` to classify network interfaces (`HOTSPOT`, `WIFI`, `ETHERNET`, `USB_TETHERING`, `CELLULAR`), strictly filter out cellular WAN interfaces, and rank Hotspot (`192.168.43.1` or `ap*`) at highest priority.
  - Updated `MainActivity.kt` to dynamically listen to network changes via `ConnectivityManager.NetworkCallback` and `LifecycleEventObserver` (`ON_RESUME`), and refresh IPs on server start/stop.
  - Added an interactive refresh button and interface selector chips in the Connection Details Card.
  - Added unit test suite `NetworkUtilsTest.kt`.
- **Verification:**
  - Unit tests in `NetworkUtilsTest.kt` passed with code 0.
  - Debug APK built successfully with `./gradlew assembleDebug`.




