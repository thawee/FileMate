# WebFS Review & Improvement Plan

## 1. Critical Bugs & Backend Defects
- [x] **Fix Missing File Upload Route `/api/upload`**: Added `/api/upload` handler in `FileServerService.kt` with robust multipart form-data parsing and raw byte stream fallback.
- [x] **Increase Server Max Request Size**: Configured `server.setMaxRequestSize(500 * 1024 * 1024)` in `FileServerService.kt` to prevent 413 Payload Too Large errors on file uploads.
- [x] **Fix Directory Traversal Vulnerability**: Secured `resolveFile()` using `toPath().toAbsolutePath().normalize()` validation to prevent accessing files outside `sharedRoot`.
- [x] **Sanitize Filenames**: Added cross-platform path separator sanitization (`replace('\\', '/')`) on all uploaded, downloaded, moved, or deleted filenames.
- [x] **Support Directory Deletion**: Updated `deleteResponse` to allow recursive folder deletion (`deleteRecursively()`) and updated `script.js` to allow folder deletion in single & batch modes.

## 2. Web UI / UX Improvements
- [x] **Toast Notification System**: Replaced intrusive browser `alert()` popups with styled floating toast notifications (Success, Error, Info).
- [x] **Search & Filter Bar**: Added real-time live search input field (`/` shortcut) to quickly filter files by name.
- [x] **Text & Code File Previewer**: Upgraded preview modal to render code/text files (`.txt`, `.json`, `.md`, `.js`, `.py`, `.html`, `.css`, `.kt`, `.java`, `.sh`, `.xml`, etc.) in styled code view with syntax highlighting aesthetics instead of raw iframes.
- [x] **Copy Link & File Actions**: Added "Copy Link" action to file lists and preview modal with instant toast feedback.
- [x] **Enhanced Grid View & Single-Line Actions**: Enlarged grid card size (min 220px-240px width, 160px-180px preview height) and forced action buttons (`👁️ View`, `🔗 Link`, `📦 Move`, `⬇ Download`, `🗑️ Delete`) onto a single, non-wrapping line (`flex-wrap: nowrap`).
- [x] **Enhanced Drag & Drop Zone**: Improved drag overlay visibility and visual drop target feedback.
- [x] **Keyboard Navigation & Accessibility**: Added shortcuts (`/` for search, `Delete` for batch deletion, `Escape` to close modals).

## 3. Storage Maintenance & Cleaning Tools
- [x] **Clean Empty Folders API & UI**: Added `POST /api/clean-empty-folders` backend endpoint that recursively finds & deletes empty folders, plus a frontend scan & clean tool button.
- [x] **Clean OS Junk Files API & UI**: Added `POST /api/clean-junk-files` backend endpoint that scans & purges OS junk files (`.DS_Store`, `Thumbs.db`, `desktop.ini`, `*.tmp`, `._*`), freeing up disk space.
- [x] **Storage Analytics & Storage Breakdown**: Added `GET /api/storage-stats` backend endpoint computing category size distribution (Images, Videos, Audio, Docs, Archives) and Top 10 Largest Files list.
- [x] **Tools & Maintenance Modal**: Added a modal UI in `index.html`, `style.css`, and `script.js` with visual category storage bar, top files inspector, and one-click cleaning actions with live Toast feedback.

## 4. Android App UI / UX Improvements
- [x] **Copy IP / Server URL Button**: Added clickable URL badge with one-tap copy-to-clipboard functionality and Toast feedback.
- [x] **Server Running Status Pulse**: Added active status indicator dot (Green for Active, Red for Stopped) and contextual status text in `MainActivity.kt`.
- [x] **Android Native Utilities Suite**: Added `StorageMaintenanceHelper.kt` with live progress bars, status feedback, and interactive Material 3 Results Dialog showing deleted paths for Clean Empty Folders and Purge Junk Files in `MainActivity.kt`.
- [x] **Tabbed Navigation Architecture (Zero Scroll Access)**: Implemented Material 3 `TabRow` (`📡 Server & Sharing` vs `🛠️ Storage Utilities`) in `MainActivity.kt` so utilities are instantly accessible at the top of the viewport with 0 scrolling.
- [x] **Sticky Web Header & Independent Scrollable File Area**: Updated `style.css` so `<header>`, search bar, action buttons, and breadcrumbs remain fixed at top of viewport, with independent smooth scrolling on `.file-list-container`.
- [x] **Web UI Image Preview Slideshow**: Added full slideshow navigation (`◀ Prev`, `Next ▶`, `▶ Play / ⏸ Pause` 3-second autoplay, `⛶ Fullscreen`, counter badge `X / Y`, floating `‹` / `›` overlay arrows, and `←` / `→` / `Space` / `F` keyboard controls) to image previewer in `index.html`, `style.css`, and `script.js`.
- [x] **Quick Delete & Auto-Advance Image**: Added `🗑️ Quick Delete` button in image viewer to delete the active image (`POST /api/delete`), display toast notification, and automatically advance to the next photo in `index.html` & `script.js` (with `Delete` key shortcut).
- [x] **Accessibility & Touch Targets**: Ensured buttons meet touch target guidelines with clean spacing and high-contrast dark theme colors.

## 5. Verification & Testing
- [x] Build project with `./gradlew assembleDebug` (SUCCESSFUL).
- [x] Verify unit tests with `./gradlew test` (3/3 PASSED).
- [x] Verified security path normalization, junk file detection, and filename sanitization.
