# File Mate UX & Functional Enhancement Plan

## 1. Web Grid Mode: Card Decluttering & Progressive Disclosure
- [x] Refactor `.grid-card .grid-actions` in `script.js` and `style.css` to use progressive disclosure:
  - Primary button: Quick Download (`⬇️ Download` / `⬇️ ZIP` / `📁 Open`).
  - Secondary button: Sleek `•••` More Menu button.
  - Dropdown Menu: Clean glassmorphic popover with `Preview / View`, `Copy Link`, `Share QR`, `Move / Rename`, and `Delete`.
- [x] Ensure click on image/media opens preview directly, and click on directory navigates directly.
- [x] Add auto-dismiss on outside click or menu option selection.

## 2. Web Drag-and-Drop Visual Polish
- [x] Upgrade `#dragOverlay` in `index.html`, `style.css`, and `script.js` with:
  - Dynamic destination path indicator ("Uploading to: /...").
  - Animated Electric Indigo to Cyber Cyan pulsing dashed border and frosted glass backdrop.

## 3. Android First-Run Permission Onboarding
- [x] Add explanatory Material 3 `AlertDialog` in `MainActivity.kt` before requesting `MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`.
  - Explains the local Wi-Fi hosting & junk cleanup benefits with clear privacy assurances.

## 4. Verification & Testing
- [x] Verify web dashboard interactions (More menu, download, QR, move, delete).
- [x] Verify Android compilation and run unit test suite (`./gradlew test`).
- [x] Deploy updated APK to connected device (`adb install -r`).

## Review & Summary
- **Grid Card Ergonomics:** Reduced 6 cluttered buttons per card down to a single high-priority primary action button and an unobtrusive `•••` dropdown menu. This dramatically simplifies the UI when browsing large directories while retaining full 1-click access to all operations.
- **Visual Drag Feedback:** When dragging files into the browser, users now see the exact upload folder destination path and an animated Electric Indigo/Cyber Cyan border pulse.
- **Empathetic Onboarding:** Replaced abrupt Android system settings redirection with a clear Material 3 rationale dialog guaranteeing 100% local Wi-Fi privacy.
- **Fully Verified:** Compilation (`compileDebugKotlin`), unit test suite (`test`), APK build (`assembleDebug`), and device installation (`adb install -r`) all executed cleanly.


