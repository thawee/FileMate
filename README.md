# 📁 File Mate: Android Local File Server & Storage Maintenance Suite

[![Android](https://img.shields.io/badge/Platform-Android_7.0%2B-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![UI Framework](https://img.shields.io/badge/UI-Jetpack_Compose_Material_3-7F52FF.svg)](https://developer.android.com/jetpack/compose)
[![Web Tech](https://img.shields.io/badge/Web_UI-HTML5_%7C_CSS3_%7C_JS-orange.svg)](app/src/main/assets/)
[![Build](https://img.shields.io/badge/Build-Gradle_9.5-025E8D.svg)](build.gradle.kts)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

**File Mate** is a lightweight, high-performance Android application that converts your mobile device into a local HTTP File Server with an interactive Web Dashboard and native storage maintenance tools. Part of the **Mate Series** ecosystem (`Music Mate`, `Trip Mate`, `Trading Mate`, `File Mate`).

Whether you need to transfer large files wirelessly across your local network, view photo slideshows, edit/read code files, clean up OS junk files (`.DS_Store`, `Thumbs.db`, `desktop.ini`), or remove empty directories, **File Mate** provides a complete solution with zero third-party cloud dependencies.

---

## ✨ Key Features

### 📡 Local Web File Server & Sharing
- **Zero-Configuration Server:** Instantly host files over local Wi-Fi on customizable HTTP ports.
- **PIN Authentication & QR Code Pairing:** Auto-generates a secure 4-digit PIN on startup. Scan the dynamic QR code in the app to quickly connect and authenticate.
- **Dynamic HTTP GZIP Compression:** Transparently compresses text and JSON responses (like file lists) on the fly, dramatically reducing bandwidth usage and increasing UI loading speed.
- **Zero-Copy File Streaming:** Uses native `FileChannel.transferTo()` with full support for `HTTP 206 Partial Content` Range requests, allowing you to stream large 4K movies or seek through audio files straight from the browser without draining the phone's battery.
- **Foreground Service & Wakelock:** Runs reliably in the background with a CPU Wakelock to ensure long batch downloads or heavy uploads are never interrupted when the screen turns off.
- **Real-Time Traffic Monitor:** Tracks live Upload (`Tx`) and Download (`Rx`) speeds.

### 🛠️ Storage Maintenance & Cleaning Utilities
- **Clean OS Junk Files:** Scans and purges system junk files (`.DS_Store`, `Thumbs.db`, `desktop.ini`, `*.tmp`, `*.bak`, `*~`, `._*`) from device storage.
- **Clean Empty Directories:** Recursively identifies and deletes nested empty folders.
- **Storage Analytics & Breakdown:** Computes category storage distribution (Images, Videos, Audio, Documents, Archives, Other) and lists the **Top 10 Largest Files**.
- **Dual Execution:** Perform cleaning operations directly on Android via Material 3 native dialogs or remotely through the Web UI.

### 📺 Media Casting (AirPlay & DLNA)
- **Local Network Discovery:** Uses Android `NsdManager` (mDNS) to instantly find local smart TVs, DLNA media renderers, and Apple AirPlay devices.
- **Direct Wireless Streaming:** Cast high-quality JPEG images straight from your device to compatible TVs via the Android app's "Cast Media" tab.

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
- **Batch Actions & Archives:** Batch selection for bulk moving, deleting, or **downloading as a single `.zip` file**. You can also upload a `.zip` file and extract it directly on the device.
- **Media Thumbnails:** Generates and caches fast, memory-optimized thumbnails for image and video galleries.
- **Recursive Search:** Instantly scan directories and their children for specific files.
- **Animated Toast System:** Modern floating toast notifications replacing intrusive browser popups.

### 📱 Android Native Experience (Jetpack Compose)
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

**File Mate** is organized into a modular Android app directory structure:

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

File Mate exposes a set of RESTful HTTP endpoints for remote management:

| Endpoint | Method | Description | Query / Body Parameters |
| :--- | :---: | :--- | :--- |
| `/api/list` | `GET` | Retrieve directory contents | `?path=/relative/path` |
| `/api/upload` | `POST` | Upload single or multiple files (up to 500 MB) | `?path=/relative/target/dir`<br>*(Multipart form-data or raw stream)* |
| `/api/download` | `GET` | Download file or folder | `?path=/relative/file/path` |
| `/api/delete` | `POST` | Delete file or directory recursively | JSON Body: `{"path": "/relative/path"}` or `{"paths": [...]}` |
| `/api/move` | `POST` | Move/Rename file or directory | JSON Body: `{"source": "/old/path", "target": "/new/path"}` |
| `/api/clean-empty-folders` | `POST` | Scan and remove empty nested directories | JSON Body: `{"path": "/relative/root"}` |
| `/api/clean-junk-files` | `POST` | Scan and delete OS junk files (`.DS_Store`, `Thumbs.db`, etc.) | JSON Body: `{"path": "/relative/root"}` |
| `/api/storage-stats` | `GET` | Fetch storage category sizes & Top 10 largest files | `?path=/relative/root` |

---

## 🚀 Getting Started

### Prerequisites
- **Android Studio:** Jellyfish / Ladybug or newer.
- **JDK:** Java 17 or higher.
- **Android SDK:** API Level 24 (Android 7.0 Nougat) or higher (Target SDK 34 / 35).
- **Gradle:** Version 9.5 (managed via Gradle Wrapper).

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

### 1. Launch Server on Android
1. Open the **File Mate** app on your Android device.
2. Grant storage permissions if prompted (`All Files Access` or `Storage Permission`).
3. Tap **Start Server**.
4. The status indicator will glow green, showing your local IP address (e.g., `http://192.168.1.100:8080`).

### 2. Connect from Any Web Browser
1. Connect your PC, tablet, or phone to the **same Wi-Fi network**.
2. Open any web browser (Chrome, Safari, Firefox, Edge) and enter the displayed URL.
3. Browse, search (`/`), download, upload (drag & drop), view photos in slideshow mode, or read code files directly.

### 3. Run Storage Cleanup
- **On Android:** Switch to the `🛠️ Storage Utilities` tab in the app to clean empty folders or remove OS junk files with 1 tap.
- **On Web Dashboard:** Click **Storage Tools** in the top navigation bar to view storage breakdown analytics and execute cleanups remotely.

---

## 🧪 Testing & Verification

File Mate includes comprehensive unit tests verifying security boundary constraints, path sanitization, and junk file identification:

```bash
./gradlew test
```

Unit Test Coverage (`FileServerSecurityTest.kt`):
- ✅ **Path Traversal Normalization:** Verifies `../` directory escape prevention.
- ✅ **Cross-Platform Filename Sanitization:** Validates backslash (`\`) replacement on paths.
- ✅ **Junk File Pattern Matching:** Validates detection of `.DS_Store`, `Thumbs.db`, `desktop.ini`, `*.tmp`, `*.bak`, and `._*` files.

---

## 📄 License

This project is released under the [MIT License](LICENSE).
