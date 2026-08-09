# 📡 Shared Server: Android Local File Server & Storage Maintenance Suite

[![Android](https://img.shields.io/badge/Platform-Android_7.0%2B-brightgreen.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![UI Framework](https://img.shields.io/badge/UI-Jetpack_Compose_Material_3-7F52FF.svg)](https://developer.android.com/jetpack/compose)
[![Web Tech](https://img.shields.io/badge/Web_UI-HTML5_%7C_CSS3_%7C_JS-orange.svg)](app/src/main/assets/)
[![Build](https://img.shields.io/badge/Build-Gradle_9.5-025E8D.svg)](build.gradle.kts)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

**Shared Server** is a lightweight, high-performance Android application that converts your mobile device into a local HTTP File Server with an interactive Web Dashboard and native storage maintenance tools. 

Whether you need to transfer large files wirelessly across your local network, view photo slideshows, edit/read code files, clean up OS junk files (`.DS_Store`, `Thumbs.db`, `desktop.ini`), or remove empty directories, **Shared Server** provides a complete solution with zero third-party cloud dependencies.

---

## ✨ Key Features

### 📡 Local Web File Server & Sharing
- **Zero-Configuration Server:** Instantly host files over local Wi-Fi on customizable HTTP ports.
- **Real-Time Traffic Monitor:** Tracks live Upload (`Tx`) and Download (`Rx`) speeds along with total bandwidth consumed.
- **Foreground Service:** Server runs reliably in the background via an Android Foreground Service with notification controls.
- **One-Tap IP Copy:** Easily copy your server URL (e.g., `http://192.168.1.100:8080`) directly to your clipboard.

### 🛠️ Storage Maintenance & Cleaning Utilities
- **Clean OS Junk Files:** Scans and purges system junk files (`.DS_Store`, `Thumbs.db`, `desktop.ini`, `*.tmp`, `*.bak`, `*~`, `._*`) from device storage.
- **Clean Empty Directories:** Recursively identifies and deletes nested empty folders.
- **Storage Analytics & Breakdown:** Computes category storage distribution (Images, Videos, Audio, Documents, Archives, Other) and lists the **Top 10 Largest Files**.
- **Dual Execution:** Perform cleaning operations directly on Android via Material 3 native dialogs or remotely through the Web UI.

### 🎨 Modern Web Dashboard (Web UI)
- **Responsive Dark Theme:** Built with modern CSS Glassmorphism, smooth animations, and high-contrast typography.
- **Sticky Header Architecture:** Fixed top header with live search (`/`), storage tools button, and action controls while file lists scroll independently.
- **Image Preview Slideshow Mode:**
  - Full-screen lightbox viewer supporting `.jpg`, `.png`, `.gif`, `.webp`, `.svg`, etc.
  - Playlist auto-advance timer (3-second autoplay), manual previous/next buttons, and counter badges.
  - **Quick Delete (`🗑️`):** One-click button to delete the active image and auto-advance to the next photo.
  - Keyboard shortcuts (`←`, `→`, `Space` to Play/Pause, `F` for Fullscreen, `Delete` to Quick Delete, `Esc` to Close).
- **Text & Code File Viewer:** Formatted code previewer for source files (`.txt`, `.json`, `.md`, `.js`, `.py`, `.html`, `.css`, `.kt`, `.java`, `.sh`, `.xml`, etc.).
- **Drag & Drop File Uploads:** Supports binary byte stream and multipart form-data uploads up to **500 MB** per request.
- **Batch Actions:** Batch selection for bulk downloading, moving, or recursive folder deletion.
- **Animated Toast System:** Modern floating toast notifications replacing intrusive browser popups.

### 🔒 Security & Path Safety
- **Path Traversal Protection:** Normalizes all paths (`toPath().toAbsolutePath().normalize()`) to prevent directory traversal attacks (`../`).
- **Filename Sanitization:** Sanitizes cross-platform path separators (`\`) on Windows and Linux clients.
- **Root Boundary Enforcement:** Confines file access strictly within designated shared storage boundaries.

---

## 🏗️ Architecture & Project Structure

**Shared Server** is organized into a modular Android app directory structure:

```
SharedServer/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/webfs/
│   │   │   │   ├── MainActivity.kt               # Main Jetpack Compose UI with Tab navigation
│   │   │   │   ├── FileServerService.kt          # Android Foreground Service & HTTP Server handlers
│   │   │   │   ├── StorageMaintenanceHelper.kt   # Kotlin utility helper for storage cleanup & stats
│   │   │   │   ├── HttpProxyServer.kt            # HTTP Proxy wrapper & request routing
│   │   │   │   ├── TrafficMonitor.kt             # Real-time network speed & bandwidth tracker
│   │   │   │   └── theme/                        # Material 3 Compose Color, Type, Theme definitions
│   │   │   ├── assets/                           # Web UI Single Page Application assets
│   │   │   │   ├── index.html                    # Single-page web dashboard HTML structure
│   │   │   │   ├── style.css                     # Glassmorphism dark mode stylesheet
│   │   │   │   └── script.js                     # Frontend interactive logic & API client
│   │   │   └── AndroidManifest.xml              # Android permissions & service declarations
│   │   └── test/java/com/example/webfs/
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

Shared Server exposes a set of RESTful HTTP endpoints for remote management:

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
1. Open the **Shared Server** app on your Android device.
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

Shared Server includes comprehensive unit tests verifying security boundary constraints, path sanitization, and junk file identification:

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
