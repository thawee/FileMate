# Protect Android storage during cleanup

- [x] Inspect both cleanup traversals and storage statistics. Existing exact-name protection happens after recursion; junk deletion has no exclusion.
- [x] Reproduce with isolated filesystem fixtures before implementing the fix.
- [x] Add one shared Android-subtree policy before cleanup traversal; apply it to cleanup candidate counts without changing storage totals.
- [x] Verify Android files and empty descendants survive while ordinary cleanup still works, including a root inside Android and similarly named AndroidBackup folders.
- [x] Build, integrate, and verify updated APK on emulator and phone. Never run cleanup against actual user files during verification.

Throughput checkpoint. One worker owns the helper and focused filesystem tests in an isolated worktree. Main owns task records, documentation, integration, and device verification. No signature or public API changes, architectural panel, PR publication, or release requested.

Verified 35 unit tests, including six real filesystem cleanup cases. The five original cases failed before the guard and passed after it. APK build and emulator launch passed; Storage tab displays the exclusion. Updated emulator and Samsung phone with app data preserved. No cleanup ran against actual user storage.

# ShareMate launcher refresh

- [x] Pin the current icon contract. Preserve folder, wireless waves, Mate M, launcher resource names, and application ID.
- [x] Simplify adaptive artwork and provide a separate monochrome outline.
- [x] Generate all legacy density icons from the same artwork and remove default robot assets.
- [x] Build, inspect rendered launcher icons, install on emulator and phone, and verify launcher behavior.

Throughput checkpoint. One generator owns vector and raster artwork so density exports cannot drift. No application logic, version change, or GitHub release is needed for this icon update.

Verified debug build, raw color and monochrome previews, actual emulator launcher rendering, and successful emulator and Samsung phone updates. All ten PNG dimensions and five XML resources checked. No version or published release changed.

# ShareMate 2.3.0 release

- [x] Resolve the forge and destination. GitHub API with existing credential helper; thawee/FileMate main. GitHub CLI is unavailable. This is a versioned release, not a PR merge chain.
- [x] Update version to 2.3.0 and code 12, finalize release notes, include the user documentation and emulator screenshots.
- [x] Verify current builds and tests, inspect APK metadata, and run the updated app on the emulator.
- [x] Resolve APK signing choice. User chose a clearly labeled debug-signed preview APK. Publish as a GitHub prerelease.
- [x] Commit verified files and publish main and the v2.3.0 tag without rewriting remote history.
- [x] Create the GitHub release with verified assets and confirm published metadata and downloads.

Throughput checkpoint. Builds and browser queue tests are independent. Signing and publishing depend on the user's APK choice. PR merge-chain steps are skipped because no PR chain exists. No force push or deployment.

Published https://github.com/thawee/FileMate/releases/tag/v2.3.0 as a prerelease at commit 2050cd9. Uploaded ShareMate-2.3.0-preview-debug.apk and SHA256SUMS.txt, then downloaded both assets and confirmed byte hashes. Prove It Works used actual APK metadata, emulator launch, and published downloads. Debug and unsigned release builds passed; 29 unit tests and six browser tests passed.

# User documentation refresh

- [x] Pin the behavior contract first. Use the existing README and shipped emulator UI as the documentation baseline. No application behavior changes.
- [x] Name the target shape. README contains user tasks and emulator screenshots; docs/TECHNICAL.md contains developer reference.
- [x] Subtract before you add. Move implementation details out of the README.
- [x] Move in small behavior-preserving steps. Delegate technical reference; capture clean emulator screens and rewrite user instructions.
- [x] Prove behavior is unchanged on the real artifact. Inspect screenshots, check source-backed instructions and local links, and run git diff --check.
- [x] Confirm the change is worth keeping. Users can find sharing and photo instructions without reading API or build details.
- [x] Rebase into small ordered commits. skip: documentation stays reviewable in the working tree; no PR requested.

Throughput checkpoint. Technical reference writing is independent of emulator capture. Main owns README, screenshots, and final checks. Code characterization and architecture workflows are skipped because this task changes documentation only.

Verified all local document links and three raw emulator PNG captures. Inspected each screenshot and matched user instructions to the shipped UI. Removed the old broken LICENSE link because this repository contains no license file. No application code changed.

# ShareMate implementation

- [x] Read the Principles section of the **poteto-mode** skill.
- [x] Phase A: Frame.
- [x] Phase B: Design the workflow.
- [x] Phase C: Run the loop.
- [x] Ground the sharing, Android intent, transfer, and slideshow paths.
- [x] Compare two session designs and choose the simplest complete contract.
- [x] Fix owner authentication bypass through compound `/api/auth/.../api/files` and mutation paths; add route regression coverage before guest links.
- [x] Replace invalid DLNA `/files/` image URLs with scoped receiver download URLs; keep unsupported Google photo casting out of the primary photo chooser.
- [x] Implement scoped sessions and their browser experience.
- [x] Implement Android receiving, ShareMate branding, and photo presentation entry points.
- [x] Implement cancellable browser transfers and retry.
- [x] Verify unit tests, APK, browser behavior, and Android behavior.
- [x] Phase D: Keep the audit trail.
- [x] Phase E: Verify and hand back.

Verified 29 debug unit tests, six browser queue tests, three real Android receiving tests, native share creation and incoming-copy UI, browser scope/revoke/conflicts/active-content protection, and native and browser photo presentation. Fixed a socket-test port reservation race by binding directly to port zero. Physical TV receiver compatibility is unverified. Google photo casting needs a custom receiver and is deferred. Opening a PR is skipped because this request is a local app improvement, with no publishing requested.

Definition of done: users can share a selected folder or files with expiring, permission-scoped access, revoke that access, receive Android shared content, cancel and retry browser uploads, and start a photo presentation. The app and web dashboard use ShareMate. Existing owner file-management and download behavior remains available. Casting is tested where receivers are available and otherwise reported as unverified.

Throughput checkpoint:

- Blocking first steps. Trace authorization and storage behavior, choose the share-session contract, and capture baseline tests before implementation.
- Independent workstreams. Browser transfer controls can be implemented separately from the coupled server and Android sharing workflow.
- Shared mutable state. Each writing worker uses an isolated worktree. Integrate patches sequentially. The main agent owns task records and final verification.
- Smallest safe decomposition. One worker owns server and Android session integration, one owns browser transfers. Branding and docs follow integration.

Scope is one local release across the Android UI, server, browser assets, tests, and documentation. Preserve the application ID and installed data. Do not deploy or publish.

# Positioning and rebrand investigation

- [x] Route through the **how** skill. For motivation questions, also route through the **why** skill.
- [x] Throughput checkpoint stays one line: `throughput checkpoint: n/a, read-only investigation`.
- [x] Produce the `how`-shaped output (Overview / Key Concepts / How It Works / Where Things Live / Gotchas), or a recommendation with a tradeoffs table if the request is a decision between alternatives.
- [x] Apply the **unslop** skill to the reply.
- [x] Inspect actual sharing, viewing, and casting behavior and limitations.
- [x] Recommend a Mate-series name, product promise, and prioritized improvements.

Proposed direction is ShareMate, local file and photo sharing through a browser, with photo presentation as a supporting workflow. FarmMate serves hobby and professional farming.

Implementation results:

- [x] Add selected-file or folder sharing sessions with explicit permissions and expiry.
- [x] Receive files through the Android Share menu.
- [x] Add a transfer queue with cancellation and retry.
- [x] Make folder photo presentation easy to reach and correct DLNA receiver URLs.
- [ ] Verify casting on actual receivers. No physical receiver is available in this session.
- [x] Remove the unsupported Google photo destination from the primary chooser. A custom receiver remains a separate future feature.
- [x] Correct the README's configurable-port claim. The current service defines fixed ports.

Validation for this investigation uses source inspection and the diff against main. Builds and device tests apply to a later implementation. No runtime compatibility claims were verified.

# Step-by-Step UI/UX Implementation Plan: FileMate (Android & WebUI)

## Phase 1: High-Impact Usability & Performance Fixes (Immediate)
- [x] **1. WebUI Thumbnail Performance & JS Bug Fix**
  - [x] 1.1 Use `/api/thumbnail` for list & grid preview thumbnails (preventing massive multi-MB RAW photo downloads) with graceful fallback.
  - [x] 1.2 Fix uncaught runtime JS `ReferenceError: closeToolsModal is not defined` on Escape key and click listeners.
- [x] **2. Android Sorting & Media Deletion Polish**
  - [x] 2.1 Fix descending sorting comparator in `FileBrowserViewModel.kt` so directories stay pinned to top on all sort criteria.
  - [x] 2.2 Fix photo deletion in `PreviewScreen.kt` so deleting an image transitions smoothly to the adjacent image instead of ejecting user back to the top of the file list.
- [x] **3. Android Native File Management Actions**
  - [x] 3.1 Add "New Folder" action with dialog in `FileBrowserScreen.kt`.
  - [x] 3.2 Expand `FileContextMenu` in Android to support "Rename", "File Details / Properties", and safe delete dialog.

## Phase 2: UX Streamlining & Productivity Features
- [x] **4. WebUI Table Clutter Reduction & Right-Click Context Menu**
  - [x] 4.1 Replace 6 colored badge buttons per row with a streamlined hover action set + `•••` action dropdown.
  - [x] 4.2 Add desktop-grade Right-Click Context Menu for both grid cards and table rows.
- [x] **5. WebUI Rename, ZIP Extraction & Shift-Select**
  - [x] 5.1 Add true "Rename" modal and connect `/api/rename`.
  - [x] 5.2 Add "Extract ZIP" button to `.zip` files calling `/api/unzip`.
  - [x] 5.3 Add Shift-click range selection for multi-file operations.
- [x] **6. Device Storage Gauge (Backend & WebUI)**
  - [x] 6.1 Add `StatFs` (freeBytes, totalBytes) to `/api/system` in `FileServerService.kt`.
  - [x] 6.2 Display storage usage bar in WebUI header (`💾 42.8 GB free of 128 GB`).

## Phase 3: Premium Polish & Delight (Apple / Linear Grade)
- [x] **7. Android Server Dashboard & Identity Enhancement**
  - [x] 7.1 Add persistent Server Status / Quick-Action card at the top of the Android main screen (IP, 1-tap QR, Start/Stop toggle).
  - [x] 7.2 Semantic colorful file-type icons in Android list/grid (PDF, Code, Audio, Video, Zip, APK).
- [x] **8. Slideshow Integration & Polish**
  - [x] 8.1 Wire up `PremiumSlideshowScreen.kt` with parallax transitions, timer selection, and presentation mode.
- [x] **9. WebUI Visual Polish**
  - [x] 9.1 Replace emojis with crisp vector SVG icons and enhance glassmorphic card styling.
- [x] **10. Verification & Validation**
  - [x] 10.1 Run unit tests (`./gradlew testDebugUnitTest`).
  - [x] 10.2 Assemble debug build (`./gradlew assembleDebug`).
  - [x] 10.3 Verify WebUI in browser.

## Phase 4: Dedicated Tools & Proxy Hub Refactoring
- [x] **11. Refactor Tools & Proxy Hub Architecture**
  - [x] 11.1 Rebrand `HostAndToolsContent` into `ToolsAndProxyHubContent`: Removed redundant giant start/stop button & duplicate QR code (now cleanly handled by home screen `ServerDashboardCard`).
  - [x] 11.2 Tab 0 (HTTP Proxy Suite): Active port 8081 status, 1-tap copy endpoint, developer CLI curl/export snippets, OS-specific setup guides (iOS, Android, macOS, Windows), and real-time Proxy bandwidth monitor.
  - [x] 11.3 Tab 1 (Storage Maintenance): Dynamic device capacity breakdown via `StatFs`, 1-click empty folder cleaner, 1-click desktop/OS junk purge (.DS_Store, Thumbs.db, *.tmp) with live progress and completion details dialog.
  - [x] 11.4 Tab 2 (Security & Network Diagnostics): WebUI security PIN manager with 1-tap copy and instant regenerate (`AuthHelper.generateNewPin()`), network adapter inspector (Wi-Fi, Hotspot, USB Tethering, Ethernet), live throughput monitor with counter reset, and media casting scanner.
  - [x] 11.5 TopAppBar action icon & Home Dashboard linkage: Updated icon from generic Settings to `Icons.Default.Build` ("Tools & Proxy Hub"), added quick "Proxy :8081" and "Storage Cleaner" action chips to home screen `ServerDashboardCard`.
  - [x] 11.6 Validation: Compile and run test suite (`./gradlew testDebugUnitTest` passed with 0 errors & `./gradlew assembleDebug` built successfully).

## Phase 5: Barcode Dialog Login Credentials & Auto-Login
- [x] **12. Barcode / QR Dialog Login Credentials & Auto-Login**
  - [x] 12.1 Update `script.js` to process `?pin=...` on startup, call `/api/auth?pin=...` to set session cookie, and clean the address bar with `history.replaceState`.
  - [x] 12.2 Update `QuickQrDialog` in `MainActivity.kt` to encode auto-login URL (`$url/?pin=$pin`), display credentials card (Username: `admin`, PIN badge, 1-tap copy, 1-tap regenerate), and clear instructions.
  - [x] 12.3 Connect `currentPin` from `AuthHelper` to `QuickQrDialog` in `WebFSScreen`.
  - [x] 12.4 Validation: Test unit tests and build debug APK (`testDebugUnitTest` & `assembleDebug` passed).

## Phase 6: Fix audited functional and UI/UX issues (September 2026)
**Objective:** Resolve all nine findings reported in the read-only audit without changing unrelated behavior.
- [x] Reproduce and fix incomplete HTTP request bodies; verify multi-read uploads with a regression test.
- [x] Make ZIP extraction avoid silent overwrites and partial results; test conflicts and failed archives.
- [x] Restrict native create/rename to single safe file names; test traversal inputs.
- [x] Replace comma-delimited web batch names with an unambiguous request format; test comma names for each affected action.
- [x] Correct nested-file QR download URLs; test nested paths.
- [x] Prevent stale browser listings and distinguish unreadable folders from empty folders; test rapid updates and error state.
- [x] Route the Tools server switch through the same permission gate and accurate running state as the dashboard.
- [x] Add a confirmation/preview before purging junk files.
- [x] Save resized images with an extension matching the encoded image and avoid unsupported-file resize actions.
- [x] Run focused regressions, full debug unit tests, debug build, JavaScript syntax check, and inspect final diff/status. Device UI verification pending: no `adb` available in this environment.

## Phase 7: Document and release audited fixes (September 2026)
**Objective:** Document the Phase 6 behavior, release it as a patch update, and commit only the related code and documentation.
- [x] Update the README with the user-visible safety behavior and web batch/API details.
- [x] Add a 2.1.1 changelog entry and bump Android versionName/versionCode to 2.1.1/9.
- [x] Re-run tests, build and syntax/diff checks after release metadata edits.
- [x] Stage only Phase 6/7 files, review staged diff, and commit; leave unrelated dependency edits unstaged.

---
## Review & Results Summary
- **Barcode / QR Dialog & Auto-Login:**
  - `QuickQrDialog` encodes the seamless auto-login URL (`http://$primaryIp:8080/?pin=$pin`) directly into the generated QR code.
  - WebUI `script.js` extracts `?pin=...` on initial load, exchanges it with `/api/auth` to set an authenticated HttpOnly session cookie, and immediately cleans the browser URL bar via `history.replaceState` so PIN is not leaked or saved in browser history.
  - Camera scans from mobile phones or tablets authenticate automatically with zero login popups.
  - For desktop PC browsers, `QuickQrDialog` now presents a dedicated **Login Credentials Card**: Username `admin` (with 1-tap copy), 4-digit PIN badge (with 1-tap copy and 1-tap regenerate button), and direct web URL.
- **Tools & Proxy Hub (Android):**
  - Dedicated modern modal focused 100% on utilities and proxy networking instead of duplicating the server start/stop dashboard.
  - **Tab 0 (HTTP Proxy Suite):** High-visibility Port 8081 endpoint card with 1-tap copy, live Tx/Rx bandwidth tracking, shell CLI snippets (`export http_proxy=...` and `curl -x ...`), and comprehensive client configuration guides for iOS, Android, macOS, and Windows.
  - **Tab 1 (Storage Maintenance):** Live Android `StatFs` storage capacity meter with % used progress bar, 1-click empty directory cleaner (with protected system folders safeguards), and 1-click OS junk purge (.DS_Store, Thumbs.db, Desktop.ini, *.tmp, *.bak) with live progress and detailed results dialog.
  - **Tab 2 (Network & Security Diagnostics):** WebUI 4-digit security PIN manager with 1-tap regenerate, active network adapter inspector with IP interface switcher (Wi-Fi, Hotspot, USB Tethering, Ethernet), live bandwidth monitor with reset button, and AirPlay/DLNA device discovery.
  - **Home Screen Dashboard Linkage:** Added 1-tap quick action chips directly on `ServerDashboardCard` for `Proxy :8081` and `Storage Cleaner`, plus updated TopAppBar action icon to `Icons.Default.Build`.
- **Android Usability:**
  - Pinned Server Dashboard Card at top of home screen with pulsing running status, active local IP pill, 1-tap URL copy, quick QR dialog, and live start/stop switch.
  - Directories strictly pinned to the top across all sort modes (Name, Size, Date - ascending & descending).
  - Deleting an image in fullscreen preview seamlessly transitions to adjacent photo instead of ejecting user.
  - Native "New Folder", "Rename", and "Properties" dialogs added to Compose UI.
  - Integrated `PremiumSlideshowScreen.kt` with parallax transitions, timer selector, and presentation controls into PreviewScreen & mini-player.
  - Color-coded file-type badges (PDF red, Audio purple, Archive orange, Code teal, APK green, Doc blue) replace generic gray icons in both grid and list.
- **WebUI Usability:**
  - Thumbnails load via `/api/thumbnail` for instantaneous directory rendering without downloading heavy camera RAW/JPEG originals.
  - Desktop-grade right-click context menu and clean `•••` action dropdowns replaced rows of 6 cluttered badge buttons.
  - Added true "Rename" and "Extract ZIP" modals wired to backend endpoints.
  - Added Shift-click range multi-selection.
  - Added real-time device storage meter in header using Android `StatFs`.
  - Replaced platform-inconsistent emojis with crisp inline SVG icons.
- **Validation:**
  - Unit tests: `25 actionable tasks, BUILD SUCCESSFUL`.
  - Debug APK: `assembleDebug BUILD SUCCESSFUL`.

## Phase 8: WebUI Authentication Resilience & Storage Error Diagnostics
- [x] **1. Backend Authentication & Directory Access Hardening (`FileServerService.kt`)**
  - [x] 1.1 Support `X-PIN` header and URL `?pin=` parameter across all API endpoints in addition to Basic Auth and Cookies.
  - [x] 1.2 Remove fragile `!folder.canRead()` directory check; check `folder.listFiles() == null` for explicit 403 permission error.
  - [x] 1.3 Add safe fallback `sharedRoot ?: Environment.getExternalStorageDirectory()` in `filesResponse` so folder listing never fails on null root.
- [x] **2. WebUI Auth Header Injection & Storage Persistence (`script.js`)**
  - [x] 2.1 Store active PIN in `sessionStorage` on QR code scan or manual entry; reuse across page reloads.
  - [x] 2.2 Inject `Authorization: Basic` and `X-PIN` headers on all outgoing `fetch()` calls.
  - [x] 2.3 Clear `tbody` loading row on error and display detailed error status with recovery instructions.
  - [x] 2.4 Add in-page PIN Unlock prompt if API encounters 401 Unauthorized.
- [x] **3. Verification & Build**
  - [x] 3.1 Run tests (`./gradlew testDebugUnitTest`).
  - [x] 3.2 Build APK (`./gradlew assembleDebug`).
# Android and Web UI working review

- [x] Route through the **how** skill. For motivation questions, also route through the **why** skill.
- [x] Throughput checkpoint stays one line: `throughput checkpoint: n/a, read-only investigation`.
- [x] Produce the `how`-shaped output (Overview / Key Concepts / How It Works / Where Things Live / Gotchas), or a recommendation with a tradeoffs table if the request is a decision between alternatives.
- [x] Apply the **unslop** skill to the reply.

Review scope. Assess the current Android app and owner/guest Web UI without changing application code. Inspect integration and comments, run unit and browser checks, and drive core flows against the Android server. Report verified behavior and defects with file references. No release or PR requested.

- [x] Inspect Android behavior and backend/frontend contracts independently.
- [x] Run Android tests/build and browser tests.
- [x] Verify Android launch and real HTTP/Web UI flows on an available emulator.
- [x] Record findings and verification limits.

# Fix Web UI grid images and selected ZIP download

- [x] Reproduce it yourself on the matching surface via the driver skill (Non-negotiables), even when a debug or instrumentation protocol says to ask the user to reproduce. Ask the user only with a stated, specific reason the control surface cannot reach the target, and only after driving it as far as it goes. If it won't reproduce directly, synthesize the trigger, tighten conditions, or instrument until it fires.
- [x] Binary-search the cause. Form the candidate hypotheses, then rule them out until one survives. Seed them with `how` over the affected subsystem and the **why** skill for regression history. Each pass, take the split that cuts the most remaining problem space, get runtime evidence, eliminate. When program state is unclear, add instrumentation or logging and read it as the code runs. Don't guess. Drive a long or stubborn hunt with Claude Code's `loop` skill. Confirm the surviving *mechanism* with runtime evidence before the step-3 architect/interrogate fan-out.
- [x] Plan the fix. If it crosses a function boundary, `architect` first. Delegate implementation to a subagent using your configured bug-fix model (default in poteto-mode's Models section) with a specific scope.
- [x] Verify on the same surface. The original repro now passes. "Inconclusive" or wrong-surface is not a pass. Flag it. Unit tests show branch behavior, not bug absence.
- [x] Stage the commits so the failing repro lands before the fix in git history. See the **tdd** skill for the failing-test-first cadence when the bug has a cheap local test path. Skip it when the test would be expensive, integration-heavy, or unclear.
  skip: preserve the fix and regression checks in the working tree; no commit requested.
- [x] Run **Opening a PR**.
  skip: no PR publication requested.

Throughput checkpoint. Frontend selection and backend image delivery investigations are independent. Main owns phone/browser reproduction, integration, and final device checks. Use disposable fixtures and preserve personal files.

Verified final APK on Samsung phone. All 37 Android tests and 10 browser tests pass. All six image fixtures load. Actual selected ZIP contains exactly two original images. SVG scripts are blocked. See tasks/webui-grid-zip-fix.md for reproduction and evidence. Existing signatures and ownership retained; no architecture rewrite or PR publication.

# ShareMate 2.3.1 release

- [x] Resolve the forge and dependency chain. GitHub CLI and Origin are absent. GitHub API and origin/main identify thawee/FileMate at d6512c9. Existing 2.3.0 release is a debug-signed prerelease.
- [x] Verify independently. Release verifier returned PASS. Both builds, all 37 Android tests, and 10 browser tests pass. The exact 2.3.1 APK runs on the Samsung phone; six images display, selected ZIP downloads, and SVG scripts are blocked.
- [x] Prepare the patch release. Version 2.3.1, code 13, changelog, README, and technical reference. Release APK signing certificate matches 2.3.0.
- [x] Publish the reviewed commit and version tag without rewriting history. main and v2.3.1 were pushed atomically at 32c3bcfc2403ba2f508526363a80cf00aa0bac96. Independent verifier returned PASS for this revision against v2.3.0.
- [x] Publish the APK and SHA256SUMS.txt and confirm the downloaded bytes and release metadata. Published https://github.com/thawee/FileMate/releases/tag/v2.3.1 as a prerelease. Both public downloads match the local SHA-256 hashes.

Throughput checkpoint. This is a versioned GitHub release, not a PR merge chain. PR topology, queue, and per-PR landing steps from Shipping are skipped because there are no PRs to land. Preserve the existing debug-signed preview/prerelease contract. The user explicitly authorized making a new release; no additional publication confirmation is needed.

Release APK is 24,170,852 bytes, version 2.3.1/code 13, with SHA-256 c8680417f7926793cc85c877c8d8222d4d692a08e33e3d8b02278fc8a4f1ee39. Its signing certificate matches the 2.3.0 preview. Prove It Works used the exact APK's metadata, matching bundled browser code, phone runtime checks, real ZIP byte comparisons, and downloaded public assets. Phone updated with app data preserved. Build, 37 Android tests, and 10 browser tests pass. Publication evidence is /private/tmp/sharemate-2.3.1-release/publication.log.
