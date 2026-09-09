# 📦 File Mate Changelog

All notable changes to the **File Mate** Android Application and Web Frontend file server project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to Semantic Versioning.

## [2.0.0] - 2026-09-09

### 🎨 Rebranding & Ecosystem Integration
- **Mate Series Official Rebrand:**
  - Rebranded application from *Shared Server* / *WebFS* to **File Mate**, harmonizing with *Music Mate*, *Trip Mate*, and *Trading Mate*.
  - Adopted **Electric Indigo** (`#6366F1`) & **Cyber Cyan** (`#38BDF8`) brand theme palette.
  - Deployed upgraded modern adaptive launcher icon featuring a rich gradient background (`#4F46E5` &rarr; `#312E81` &rarr; `#1E1B4B`), bold Cyber Cyan Wi-Fi broadcast waves, and the signature white **"M"** monogram.
  - Updated foreground service notification channel and status indicators to **File Mate Running**.

### 🌐 Web Dashboard Refinements
- **Grid View Progressive Disclosure:**
  - Decluttered grid cards by replacing competing 6-button rows with a clean primary action (`⬇ Download` / `⬇ ZIP`) and a sleek `•••` More Menu.
  - Added glassmorphic dropdown popovers for secondary actions: *Preview / View*, *Copy Link*, *Share QR*, *Move / Rename*, and *Delete*.
  - Enhanced direct interaction: clicking preview thumbnails or file/folder titles immediately opens or navigates to the item.
  - Added smart auto-dismiss for open dropdown menus when clicking outside or selecting an action.
- **Drag-and-Drop Visual Polish:**
  - Upgraded `#dragOverlay` with real-time dynamic destination indicator (`Uploading to: /<path>`).
  - Added pulsing Electric Indigo to Cyber Cyan animated border and frosted glass backdrop effect.
- **Favicon & Branding:**
  - Added embedded SVG favicon to browser tabs matching the Electric Indigo File Mate icon.
  - Updated title and navbar header branding to **📁 File Mate**.
  - Aligned primary CSS theme variables to Electric Indigo (`#6366F1` / `#4F46E5`).

### 📱 Android Native Experience
- **Permission Onboarding Rationale:**
  - Replaced immediate, abrupt system settings redirect with an empathetic Material 3 `AlertDialog` explaining the necessity of All Files Access for Wi-Fi file sharing and junk cleanup.
  - Highlighted clear local privacy guarantee: 100% on-device Wi-Fi operation with zero cloud dependencies or external analytics.
- **Layout & Inset Protections:**
  - Added `.statusBarsPadding()` to the server dashboard screen to prevent status bar and camera notch overlap on edge-to-edge displays.
  - Renamed network stats label to `File Server`.
  - Bumped version to `2.0.0` (`versionCode 7`).

## [1.6.0] - 2026-08-22

### 🚀 Added
- **Interactive Web UI Image Preview Zoom & Pan:**
  - Added dedicated zoom toolbar controls (`+`, `−`, and interactive percentage indicator badge `100%`).
  - Added multi-gesture support: double-click / double-tap to toggle zoom ($1\times \leftrightarrow 2.5\times$), mouse scroll-wheel / trackpad pinch zoom, and fluid touch pinch-to-zoom for mobile/tablets.
  - Implemented freeform click-and-drag panning with dynamic `grab`/`grabbing` cursor states when zoomed in ($> 1\times$).
  - Added keyboard shortcuts for zoom navigation: <kbd>+</kbd> / <kbd>=</kbd> to Zoom In, <kbd>-</kbd> / <kbd>_</kbd> to Zoom Out, and <kbd>0</kbd> to Reset Zoom.
  - Added automatic state resets on slide change, playlist progression, or modal dismissal.
- **Web UI Toolbar Sort Selector & UX Refinements:**
  - Added a persistent sort selector dropdown in the header toolbar synced two-way with table header columns and Grid Mode.
  - Optimized grid cards with $34\text{px}+$ comfortable touch targets and wrapping action buttons.
  - Added responsive mobile viewport layout (< 768px) with intelligent column reduction and flexible search box.
  - Added smooth exit transition animations (`.modal-closing`) across all modal dialogs.
  - Standardized ARIA accessibility (`aria-label`, `role="button"`) and visible focus states (`:focus-visible`).
- **Android Native UI/UX Overhaul (Jetpack Compose & Material 3):**
  - Added `BackHandler` predictive back navigation in `FileBrowserScreen` to smoothly traverse directory hierarchies before app exit.
  - Implemented responsive adaptive grid (`GridCells.Adaptive(minSize = 130.dp)`) for tablets, foldables, and landscape mode.
  - Added double-tap zoom ($1\times \leftrightarrow 2.5\times$) with pan boundary clamping in native `PreviewScreen`.
  - Added external media launcher and playback overlay delegating video/audio files to `Intent.ACTION_VIEW` via FileProvider.
  - Added unsaved changes safeguard confirmation dialog in `TextEditorScreen`.
  - Added dynamic bottom content insets when floating mini-player bar is active.
  - Unified theme styling with Indigo/Slate Material 3 palette.

---

## [1.5.0] - 2026-08-20

### 🐛 Fixed
- **Slideshow Pager Syncing Issue:**
  - Fixed an issue in `PreviewScreen` and `PremiumSlideshowScreen` where swiping the image pager manually was abruptly interrupted and stuck displaying half of two images. The global casting state now syncs via `settledPage` to avoid feedback loops with programmatic scroll animations.
- **AirPlay Casting Protocol:**
  - Added required `User-Agent: MediaControl/1.0` and `X-Apple-Session-ID` headers to the AirPlay HTTP PUT `/photo` and `/stop` endpoints to fix casting compatibility with Apple TV boxes.

---

## [1.4.0] - 2026-08-15

### 🚀 Added
- **Media Casting (AirPlay & DLNA):**
  - Integrated a new "Cast Media" tab on the Android App using mDNS (`NsdManager`) to discover local network media players, smart TVs, and AirPlay devices.
  - Developed custom `DlnaCaster` and `AirPlayCaster` implementations to stream images wirelessly.
  - Tested AirPlay HTTP PUT payload logic directly over the network to send binary JPEG images.
- **Web Slideshow Experience & Metadata:**
  - Added a new `/api/exif` endpoint that extracts GPS coordinates and timestamp from images.
  - Implemented reverse-geocoding to display the location (e.g. "Paris, France") and date on a frosted-glass overlay.
  - Improved the slideshow visual layout by injecting a dynamic blurred, screen-filling background (`backdrop-filter`) to handle mixed aspect ratio images elegantly.
  - Updated the slideshow speed dropdown (3s, 5s, 10s, 20s) with a legible interface, defaulting to 5s.

---

## [1.3.0] - 2026-08-15
- **Security & Authentication:**
  - Implemented PIN-based basic authentication to secure the web server interface.
  - Automatically generates a 4-digit PIN on server startup.
- **Batch Downloads & Archives:**
  - `GET /api/download-zip`: Streams multiple selected files/folders as a `.zip` archive on the fly.
  - `POST /api/unzip`: Extracts uploaded `.zip` archives directly on the device.
- **Media & UI Enhancements:**
  - `GET /api/thumbnail`: Generates memory-optimized thumbnails for images and videos using `ThumbnailUtils`.
  - Added dedicated `/api/rename` endpoint for simpler file renaming.
  - Added `/api/search` endpoint for recursive file discovery.
- **Android App Polish:**
  - Added dynamic QR Code and PIN display in `MainActivity` for quick connection.
  - Implemented `WakeLock` in `FileServerService` to keep CPU awake during large file transfers.
  - **Dynamic HTTP Compression**: Text and JSON responses (such as file lists and system stats) are now transparently gzipped via the `Accept-Encoding: gzip` header to dramatically reduce network bandwidth.

---

## [1.2.0] - 2026-08-08

### 🚀 Added
- **Storage Maintenance & Cleaning Suite (HTTP API & Native Android):**
  - `POST /api/clean-empty-folders`: Recursively scans and purges nested empty directories.
  - `POST /api/clean-junk-files`: Scans and removes OS junk files (`.DS_Store`, `Thumbs.db`, `desktop.ini`, `*.tmp`, `*.bak`, `*~`, `._*`), freeing up disk space.
  - `GET /api/storage-stats`: Computes total storage usage, file/folder counts, category size breakdown (Images, Videos, Audio, Documents, Archives, Other), and Top 10 Largest Files list.
- **Android Native Utilities (`StorageMaintenanceHelper.kt`):**
  - Standalone Kotlin utility helper enabling 1-tap storage cleaning directly on Android devices without requiring a web browser.
  - Asynchronous execution in Coroutines (`Dispatchers.IO` / `Dispatchers.Main`) with automatic `MediaScannerConnection` sync.
  - Material 3 `AlertDialog` displaying detailed, scrollable lists of deleted file & directory paths.
- **Tabbed View Architecture (`MainActivity.kt`):**
  - Added Material 3 `TabRow` (`📡 Server & Sharing` vs `🛠️ Storage Utilities`) placed directly below the primary Start/Stop toggle button, eliminating the need to scroll down to access maintenance tools.

### 🎨 UI & UX Enhancements
- **Web UI Image Preview Slideshow Mode:**
  - Automatic directory image playlist discovery supporting `.jpg`, `.jpeg`, `.png`, `.gif`, `.webp`, `.svg`, `.bmp`, and `.ico`.
  - Added header controls: `◀ Previous`, `Next ▶`, `▶ Play / ⏸ Pause` (3-second auto-advance timer), `⛶ Fullscreen`, `🗑️ Quick Delete`, and `Image X / Y` counter badge.
  - Quick Delete functionality: Deletes active photo via `POST /api/delete`, displays toast feedback, updates background file list, and automatically advances to the next photo.
  - Interactive floating overlay navigation arrows (`‹` and `›`) on left/right sides of preview container.
  - Keyboard Navigation: `←` (Previous), `→` (Next), `Space` (Play/Pause Autoplay), `F` (Fullscreen), `Delete` (Quick Delete), `Esc` (Close & Stop).
- **Enlarged Grid View Layout:**
  - Expanded grid card sizes (`minmax(220px, 1fr)` base, `240px` on desktop) and preview thumbnail container heights (`160px` to `180px`).
- **Single-Line 5-Button Action Bar:**
  - Enforced `flex-wrap: nowrap` on `.grid-actions` so all 5 action icons (`👁️ View`, `🔗 Link`, `📦 Move`, `⬇ Download`, `🗑️ Delete`) fit on a single, non-wrapping line across desktop, tablet, and mobile displays.
- **Android Visual Polish:**
  - Added infinite breathing/pulsing animation (`1.0x ➔ 1.35x` using `graphicsLayer`) on the green active status dot when the server is running.
  - Enhanced `StatCard` with color-coded directional traffic arrows (`↑ Emerald` for Tx, `↓ Indigo` for Rx).
- **Web Toast Notification System:**
  - Replaced intrusive native `alert()` browser popups with animated floating Toasts (Success, Error, Info).
- **Live Search & Filter Bar:**
  - Real-time search filter with keyboard shortcut (`/` to focus, `Esc` to clear).
- **Code & Text File Viewer:**
  - Formatted preview container rendering source/text files (`.txt`, `.json`, `.md`, `.js`, `.py`, `.html`, `.css`, `.kt`, `.java`, `.sh`, `.xml`, etc.) inside a styled code view rather than raw iframes.

### 🔒 Security & Backend Fixes
- **Multipart Form Upload Handler (`/api/upload`):**
  - Implemented multipart `form-data` parser and raw binary byte stream receiver supporting file uploads up to **500 MB** (`server.setMaxRequestSize(500 * 1024 * 1024)`).
- **Path Traversal Vulnerability Protection:**
  - Enforced strict canonical path normalization (`toPath().toAbsolutePath().normalize()`) in `resolveFile()` against `sharedRoot` boundary.
- **Cross-Platform Filename Sanitization:**
  - Sanitized Windows backslashes (`replace('\\', '/')`) and directory path separators on all uploaded, moved, downloaded, or deleted filenames.
- **Recursive Folder Deletion:**
  - Updated `/api/delete` endpoint to support `deleteRecursively()`, enabling folder deletion in both single & batch modes.

### 🧪 Testing & Verification
- Added `FileServerSecurityTest.kt` with unit tests for path normalization security, cross-platform filename sanitization, and OS junk file pattern detection (`3/3 PASSED`).
- Verified build stability with `./gradlew assembleDebug` (`BUILD SUCCESSFUL`).
