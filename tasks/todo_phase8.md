# Phase 8: UI/UX & Functional Improvement Plan (September 2026)

## Scope
Fix all 10 findings from the September 2026 UX audit. Ordered by priority.

---

## P0 – Quick Wins (≤ 1 hour each)

- [ ] **A1: Bundle marked.min.js locally**
  - Download `marked.min.js` to `app/src/main/assets/`
  - Update `index.html` line 260 to `<script src="marked.min.js">`

- [ ] **A2: Slideshow page-dot animation**
  - Use `animateDpAsState` for dot width (8→24dp)
  - Use `animateColorAsState` for dot color in `PremiumSlideshowScreen.kt`

- [ ] **A3: Zoom state reset between image pages**
  - In `ZoomableImage`, accept `isCurrentPage: Boolean`
  - `LaunchedEffect(!isCurrentPage) { scale=1f; offset=Zero }`
  - Pass `page == pagerState.settledPage` from `PreviewScreen`

---

## P1 – High Impact (2–4 hours each)

- [x] **B1: Android multi-select batch mode**
  - Add `selectedPaths: StateFlow<Set<String>>` + `toggleSelection()` + `clearSelection()` + `selectAll()` to `FileBrowserViewModel`
  - Show checkbox overlay on grid/list items when any item is selected
  - Floating batch toolbar: Delete, Share, Move when selection > 0
  - Long-press enters selection mode; tap item in selection mode toggles

- [x] **B2: Resize presets dialog**
  - Before resizing, show `AlertDialog` with options: 75%, 50%, 25% + Quality (High/Medium)
  - Apply chosen scale factor instead of hardcoded `/ 2`

- [x] **B3: Text editor — Find bar**
  - Toggle-able find bar below `TopAppBar` in `TextEditorScreen`
  - Highlight all matches with `SpanStyle` in `AnnotatedString`
  - Prev / Next navigation buttons + "X of Y" counter
  - Dismiss with Escape / X button

---

## P2 – Web UI Polish & Reliability

- [x] **C1: Web UI accessibility**
  - Add `aria-live="polite"` to `#toastContainer`
  - Add `role="link"` to clickable file name spans
  - Add `aria-modal="true"` and focus-trap on modals (first focusable element on open, restore on close)
  - Add `aria-sort` to sortable `<th>` headers

- [ ] **C2: Port hardcoded at 8080 — configurable**
  - Add `SharedPreferences` key `server_port` (default 8080)
  - Port picker in Tools & Proxy Hub → new "Server Config" mini-section
  - Pass port to `FileServerService` intent and update all hardcoded `:8080` refs

---

## Skipped (out of scope / need external library)
- Syntax highlighting in text editor (needs CodeEditor / Sora-Editor library integration — separate PR)

---

## Verification
- [ ] Run `./gradlew testDebugUnitTest`
- [ ] Run `./gradlew assembleDebug`
- [x] JS syntax check: `node --check assets/script.js`
- [ ] Update `CHANGELOG.md` + bump version to 2.2.0 / versionCode 10
