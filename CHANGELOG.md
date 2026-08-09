# 📦 WebFS Changelog

All notable changes to the **WebFS** Android Application and Web Frontend file server project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres to Semantic Versioning.

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
