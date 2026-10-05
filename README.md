# ShareMate: share files and photos with nearby devices

[![Android](https://img.shields.io/badge/Platform-Android_8.0%2B-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![UI Framework](https://img.shields.io/badge/UI-Jetpack_Compose_Material_3-7F52FF.svg)](https://developer.android.com/jetpack/compose)
[![Web Tech](https://img.shields.io/badge/Web_UI-HTML5_%7C_CSS3_%7C_JS-orange.svg)](app/src/main/assets/)
[![Build](https://img.shields.io/badge/Build-Gradle_9.6-025E8D.svg)](build.gradle.kts)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

**ShareMate** shares files and photos from your Android device through a browser on the same local network. It belongs to the Mate series alongside MusicMate, TradingMate, FarmMate, and BillMate.

Choose files or a folder, create an expiring guest link, and show its QR code to a recipient. You can allow downloads, receive uploads into a folder, or allow both. Photo viewing and slideshows support the sharing workflow. Owner file management, proxy, and storage maintenance remain available separately.

The application ID remains `com.apincer.fileserver`, so the rebrand preserves existing installs and app data.

---

## ✨ Key Features

### 📡 Local Web File Server & Sharing
- **Local browser access:** The file server uses port 8080. The optional proxy uses port 8081. These ports are fixed.
- **Scoped guest links:** Share selected files or a folder for 15 minutes, one hour, or one day. Revoke a link from the app. Stopping the server invalidates its links.
- **Separate permissions:** Download guests can access only the selected content. Upload-only guests can send files into the chosen folder without browsing its existing files. Guest uploads are limited to 20 MiB per file and refuse filename conflicts.
- **Photo presentation:** View shared photos in the guest browser or start a local slideshow from a photo folder. Recipients can save images delivered to their browser.
- **PIN Authentication & QR Code Pairing:** Auto-generates a secure 4-digit PIN on startup. Scan the dynamic QR code in the app to quickly connect and authenticate, with persistent browser sessions across page reloads.
- **Owner access:** The owner dashboard keeps PIN authentication and file-management actions. API routes use exact matching. A reverse proxy must forward the original root paths.
- **Zero-Copy File Streaming:** Uses native `FileChannel.transferTo()` with full support for `HTTP 206 Partial Content` Range requests, allowing you to stream large 4K movies or seek through audio files straight from the browser without draining the phone's battery.
- **Foreground Service & Wakelock:** Runs reliably in the background with a CPU Wakelock to ensure long batch downloads or heavy uploads are never interrupted when the screen turns off.
- **Real-Time Traffic Monitor:** Tracks live Upload (`Tx`) and Download (`Rx`) speeds.

### 🛠️ Storage Maintenance & Cleaning Utilities
- **Clean OS Junk Files:** After confirmation, scans and permanently purges system junk files (`.DS_Store`, `Thumbs.db`, `Desktop.ini`, `*.tmp`, `*.bak`, `*~`, `._*`) from device storage. Review the warning first: backups and temporary files may contain important work.
- **Clean Empty Directories:** Recursively identifies and deletes nested empty folders.
- **Storage Analytics & Breakdown:** Computes category storage distribution (Images, Videos, Audio, Documents, Archives, Other) and lists the **Top 10 Largest Files**.
- **On-Device Cleanup:** Run cleanup from the Android Tools & Proxy Hub; the web dashboard displays storage capacity but does not expose cleanup endpoints.

### Photo presentation and casting
- Open an image folder and choose **Present photos** to start a local slideshow.
- AirPlay and DLNA discovery and image delivery are implemented. Receiver compatibility requires testing with the particular device. Google photo casting requires a custom receiver and remains outside this release; see the [Google Cast receiver guide](https://developers.google.com/cast/docs/web_receiver).

### 🎨 Modern Web Dashboard (Web UI)
- **Responsive Dark Theme:** Built with modern CSS Glassmorphism, smooth animations, and high-contrast typography.
- **Sticky Header Architecture:** Fixed top header with live search (`/`), sort selector dropdown, storage tools button, and action controls while file lists scroll independently.
- **Image Preview & Lightbox Zoom Engine:**
  - Full-screen lightbox viewer supporting `.jpg`, `.png`, `.gif`, `.webp`, `.svg`, etc.
  - **Interactive Multi-Level Zoom & Pan:** Scale from $50\%$ to $500\%$ ($0.5\times$ to $5.0\times$) using header toolbar buttons (`+`, `−`), interactive percentage badge (`100%`), mouse scroll wheel, trackpad pinch, or double-click / double-tap to toggle $1\times \leftrightarrow 2.5\times$.
  - **Freeform Drag Panning:** Click and drag to smoothly pan across large high-res photos when zoomed in ($> 100\%$) with dynamic `grab`/`grabbing` cursor states.
  - **Dynamic Blurred Backgrounds:** Automatically creates a frosted, screen-filling background from your image to elegantly handle mixed aspect ratios.
  - **EXIF Metadata Engine:** Extracts embedded GPS coordinates and timestamps from photos, reverse-geocoding them (e.g. "Tokyo, Japan") and displaying the info on a sleek glass overlay.
  - **Quick Delete (`🗑️`):** One-click button to delete the active image and auto-advance to the next photo.
  - Interactive floating overlay navigation arrows (`‹` and `›`) on left/right sides of preview container.
  - **Keyboard Shortcuts:** `←`, `→` for navigation, `Space` to Play/Pause, `F` for Fullscreen, `+` / `-` to Zoom In/Out, `0` to Reset Zoom, `Delete` to Quick Delete, `Esc` to Close.
- **Text & Code File Viewer:** Formatted code previewer for source files (`.txt`, `.json`, `.md`, `.js`, `.py`, `.html`, `.css`, `.kt`, `.java`, `.sh`, `.xml`, etc.).
- **Drag & Drop File Uploads:** Supports binary byte stream and multipart form-data uploads up to **500 MB** per request.
- **Transfer queue:** Owner browser uploads keep their destination when queued. Cancel queued or active transfers, retry failed or cancelled transfers, and inspect per-file progress. Cancelling cannot remove a file the server has already received.
- **Batch Actions & Archives:** Batch selection for bulk moving, deleting, or **downloading as a single `.zip` file**, including filenames with commas. ZIP extraction creates a new destination folder; it refuses to replace an existing folder and does not publish partially extracted files.
- **Media Thumbnails:** Generates and caches fast, memory-optimized thumbnails for image and video galleries.
- **Recursive Search:** Instantly scan directories and their children for specific files.
- **Animated Toast System:** Modern floating toast notifications replacing intrusive browser popups.

### 📱 Android Native Experience (Jetpack Compose)
- **Receive from other apps:** Choose ShareMate in Android's Share menu, confirm a destination folder, and copy the received content without replacing existing files.
- **Predictive Hierarchical Back Navigation:** Intercepts system back gestures with `BackHandler` to traverse subdirectories before exiting the app.
- **Adaptive Responsive Grid:** Automatically adapts column count on foldables, tablets, and landscape viewports (`GridCells.Adaptive(minSize = 130.dp)`).
- **In-App Double-Tap Zoom:** Double-tap gesture zoom ($1\times \leftrightarrow 2.5\times$) in native image preview.
- **External Media Launcher:** Launch videos and music in external system players (e.g. VLC) via `Intent.ACTION_VIEW` and FileProvider.
- **Unsaved Changes Safeguards:** Confirmation dialog in text editor to prevent accidental loss of modifications.

### 🔒 Security & Path Safety
- **Path Traversal Protection:** Normalizes all paths (`toPath().toAbsolutePath().normalize()`) to prevent directory traversal attacks (`../`).
- **Filename Sanitization:** Sanitizes cross-platform path separators (`\`) on Windows and Linux clients.
- **Root Boundary Enforcement:** Confines file access strictly within designated shared storage boundaries.

---

## 🏗️ Architecture & Project Structure

**ShareMate** is organized into a modular Android app directory structure:

```
FileMate/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/apincer/fileserver/
│   │   │   │   ├── MainActivity.kt               # Main Jetpack Compose UI with Tab navigation
│   │   │   │   ├── FileServerService.kt          # Android Foreground Service & HTTP Server handlers
│   │   │   │   ├── StorageMaintenanceHelper.kt   # Kotlin utility helper for storage cleanup & stats
│   │   │   │   ├── HttpProxyServer.kt            # HTTP Proxy wrapper & request routing
│   │   │   │   ├── TrafficMonitor.kt             # Real-time network speed & bandwidth tracker
│   │   │   │   ├── cast/                         # DLNA & AirPlay casting engine
│   │   │   │   ├── ui/browser/                   # File browser, preview, text editor, slideshow screens
│   │   │   │   └── theme/                        # Material 3 Compose Color, Type, Theme definitions
│   │   │   ├── assets/                           # Web UI Single Page Application assets
│   │   │   │   ├── index.html                    # Single-page web dashboard HTML structure
│   │   │   │   ├── style.css                     # Glassmorphism dark mode stylesheet
│   │   │   │   └── script.js                     # Frontend interactive logic & API client
│   │   │   └── AndroidManifest.xml              # Android permissions & service declarations
│   │   └── test/java/com/apincer/fileserver/
│   │       └── FileServerSecurityTest.kt         # Unit tests for security & file utilities
│   └── build.gradle.kts                          # App module build script
├── tasks/
│   ├── todo.md                                   # Task management and project roadmap
│   └── lessons.md                                # Project engineering lessons and best practices
├── CHANGELOG.md                                  # Version release notes and change history
├── build.gradle.kts                              # Root project build configuration
└── settings.gradle.kts                           # Gradle settings configuration
```

---

## 🔌 HTTP API Specifications

ShareMate exposes a set of RESTful HTTP endpoints for remote management:

| Endpoint | Method | Description | Query / Body Parameters |
| :--- | :---: | :--- | :--- |
| `/api/files` | `GET` | Retrieve directory contents | `?path=relative/folder` |
| `/api/upload` | `POST` | Upload a file (up to 500 MB per request) | `?path=relative/folder&filename=photo.jpg`; raw body or multipart form-data |
| `/api/download/{filename}` | `GET` | Download a file | `?path=relative/folder` (filename is the basename, not the full path) |
| `/api/delete` | `POST` | Delete files or directories recursively | `?path=relative/folder&name=one.txt&name=two%2Cfinal.txt` |
| `/api/move` | `POST` | Move files or directories | `?fromPath=source&targetPath=destination&name=one.txt&name=two%2Cfinal.txt` |
| `/api/download-zip` | `GET` | Download selected items as ZIP | `?path=relative/folder&name=one.txt&name=two%2Cfinal.txt` |
| `/api/unzip` | `POST` | Extract ZIP into a **new** sibling folder | `?path=relative/folder&name=archive.zip`; returns an error when the destination exists |
| `/api/qr` | `GET` | Generate a file/folder sharing QR code | `?path=relative/folder&name=photo.jpg&isFolder=false` |

For batch operations, repeat the URL-encoded `name` parameter for each complete filename; commas inside a filename are not separators. The legacy `names=first,second` form remains supported for existing clients. Authenticate through the web dashboard or use the server's Basic authentication credentials.

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio:** Jellyfish / Ladybug or newer.
- **JDK:** Java 17 or higher.
- **Android SDK:** API Level 26 (Android 8.0 Oreo) or higher (target SDK 36; compile SDK 37).
- **Gradle:** Version 9.6 (managed via Gradle Wrapper).

### Building from Source

1. **Clone the Repository:**
   ```bash
   git clone https://github.com/thawee/SharedServer.git
   cd SharedServer
   ```

2. **Run Unit Tests:**
   ```bash
   ./gradlew test
   ```

3. **Build Debug APK:**
   ```bash
   ./gradlew assembleDebug
   ```
   *The output APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.*

4. **Install on Connected Android Device:**
   ```bash
   ./gradlew installDebug
   ```

---

## 📱 How to Use

### Share selected content

1. Connect both devices to the same Wi-Fi network or phone hotspot.
2. Open the folder in ShareMate and choose **Share folder**, or select files and choose **Share selected**.
3. Choose the permission mode and expiry, then create the link.
4. Show the guest QR code or copy the link to the recipient.
5. Revoke access from the active shares list when finished.

Owner connection QR codes grant file-management access. Guest share QR codes grant only the selected content and permission mode. Use the guest code for recipients.

### Receive files from another Android app

1. Select files in Gallery or another app and choose **ShareMate** in its Share menu.
2. Confirm the destination folder in ShareMate.
3. Review the copy results. Existing filenames remain unchanged.

### 1. Launch Server on Android
1. Open the **ShareMate** app on your Android device.
2. Grant storage permissions if prompted (`All Files Access` or `Storage Permission`).
3. Tap **Start Server**.
4. The status indicator will glow green, showing your local IP address (e.g., `http://192.168.1.100:8080`).

### 2. Connect from Any Web Browser
1. Connect your PC, tablet, or phone to the **same Wi-Fi network**.
2. Open any web browser (Chrome, Safari, Firefox, Edge) and enter the displayed URL.
3. Browse, search (`/`), download, upload (drag & drop), view photos in slideshow mode, or read code files directly.

### 3. Run Storage Cleanup
- **On Android:** Open **Tools & Proxy Hub → Storage Maintenance** to clean empty folders. **Purge OS Junk Files** requires confirmation before permanently deleting matching files.
- **On Web Dashboard:** Use the file list for uploads, downloads, moves and ZIP extraction. Storage usage is shown in the header.

---

## 🧪 Testing & Verification

ShareMate includes unit tests for request-body assembly, archive extraction, batch filenames, QR URLs, native name validation, directory load ordering, image naming and security boundaries:

```bash
./gradlew test
```

Run the browser transfer checks with `node --test tools/verify/browser-upload-queue.test.cjs`.

For a running app forwarded to local port 18080, set `SHAREMATE_PIN` to its current owner PIN and run `node tools/verify-sharemate.mjs`. The script creates a unique test folder under Download, checks actual guest and owner HTTP routes, revokes its shares, and removes only that test folder. Set `SHAREMATE_URL` for another address. Optional browser verification accepts `SHAREMATE_PLAYWRIGHT` as the installed Playwright package path and `SHAREMATE_CHROME` as a browser executable path.

Examples of unit-test coverage:
- ✅ **Path Traversal Normalization:** Verifies `../` directory escape prevention.
- ✅ **Cross-Platform Filename Sanitization:** Validates backslash (`\`) replacement on paths.
- ✅ **Junk File Pattern Matching:** Validates detection of `.DS_Store`, `Thumbs.db`, `desktop.ini`, `*.tmp`, `*.bak`, and `._*` files.
- ✅ **Transfer Safety:** Validates complete upload bodies and ZIP extraction without overwrites or partial destinations.
- ✅ **Navigation & Names:** Validates stale-load rejection, filenames containing commas, local name validation, and QR links to nested files.

---

## 📄 License

This project is released under the [MIT License](LICENSE).
