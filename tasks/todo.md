# UI/UX Comprehensive Overhaul Plan

## 1. Android Native UI/UX Enhancements (Jetpack Compose)
- [x] **Back Navigation & Predictive Back**:
  - Add `BackHandler` in `FileBrowserScreen.kt` to intercept back gestures and navigate up directories (`viewModel.navigateUp()`) before exiting the app.
- [x] **Double-Tap to Zoom & Gallery Ergonomics**:
  - Upgrade `ZoomableImage` in `PreviewScreen.kt` with animated double-tap zoom (1x <-> 2.5x) alongside pinch-to-zoom and pan.
- [x] **Adaptive Responsive Grid**:
  - Replace `GridCells.Fixed(2)` with `GridCells.Adaptive(minSize = 130.dp)` in `FileBrowserScreen.kt` for tablets, foldables, and landscape orientation.
- [x] **Rich Media & Video/Audio Handling in Preview**:
  - In `WebFSScreen.kt` and `PreviewScreen.kt`, handle media types gracefully (image viewing, video/audio launch with FileProvider Intent fallback, thumbnail badges).
- [x] **Unsaved Changes Safeguard in Text Editor**:
  - Add confirmation dialog in `TextEditorScreen.kt` when exiting with unsaved changes.
- [x] **Layout Inset & Mini-Player Floating Bar Padding**:
  - Dynamically pad list and grid content when the floating cast/slideshow mini-player bar is active to avoid obscuring bottom items.
- [x] **Theme Polish & Material 3 Dynamic Theming**:
  - Connect `WebFSTheme` in `MainActivity.kt` with polished dark/light palettes and contrast.

## 2. Web Client UI/UX Enhancements (HTML / CSS / JS)
- [x] **Dedicated Sort Selector in Toolbar**:
  - Add a dedicated Sort dropdown in the header toolbar in `index.html` and `script.js` so users in Grid mode can sort without switching to List mode.
- [x] **Grid Card Action Hierarchy & Touch Targets**:
  - Reorganize action buttons in `.grid-card` with comfortable touch targets (40px+), refined icons, and mobile ergonomics.
- [x] **Mobile Responsive Layout & Table Polish**:
  - Optimize `.file-list` and header layout for mobile screens (< 768px) to eliminate awkward horizontal scrolling.
- [x] **Accessibility & ARIA Labels**:
  - Add explicit `aria-label` and `title` attributes to all icon buttons and interactive controls across `index.html` and `script.js`.
- [x] **Smooth Modal Transitions & Visual States**:
  - Add smooth closing animations and refined backdrop blur to modals (`#previewModal`, `#moveModal`, `#mkdirModal`, `#qrModal`).

## 3. Verification & Testing
- [x] Verify Android Kotlin compilation with `./gradlew compileDebugKotlin`.
- [x] Verify Web UI rendering, theme toggle, sorting, search, and responsive styling.
- [x] Run `./gradlew test` test suite.

## Review & Summary
- **Android App**: Implemented intuitive hierarchical back navigation with `BackHandler`, responsive adaptive columns, double-tap gallery zoom, external app media playback, unsaved changes safeguards in text editor, dynamic floating bottom bar padding, and Material 3 theme polish.
- **Web App**: Implemented dedicated sort dropdown in toolbar synced across table headers and grid mode, mobile-responsive layout for small viewports, 40px+ tap targets on cards, modal exit animations, and complete ARIA accessibility attributes.
