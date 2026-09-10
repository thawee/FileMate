# Lessons Learned - Shared Server

## Lessons & Best Practices

1. **Verify Backend Endpoints Against Frontend API Calls**:
   - **Pattern**: The web frontend JS triggered `POST /api/upload?path=...`, but the Android Kotlin backend lacked the `/api/upload` route in `handleRoute()`, leading to silent 404 upload failures.
   - **Rule**: Always map every `fetch()` and `XMLHttpRequest` call in frontend assets to a corresponding backend endpoint handler before considering feature complete.

2. **Server Payload Size Limits for File Uploads**:
   - **Pattern**: Custom NIO servers often default `maxRequestSize` to small buffers (e.g., 2MB), causing file uploads to fail with `413 Payload Too Large`.
   - **Rule**: Set explicit maximum request payload limits suitable for file transfers (e.g. 500MB) on the HTTP server instance.

3. **Strict Path Normalization for Local File Servers**:
   - **Pattern**: Resolving query paths using naive string concatenation or `File.parentFile` without checking root bounds leaves local file servers vulnerable to Directory Traversal (`../`) attacks.
   - **Rule**: Always normalize paths using `toPath().toAbsolutePath().normalize()` and check `targetPath.startsWith(rootPath)` before reading, writing, or deleting files.

4. **Cross-Platform Path Separators in Filename Parsing**:
   - **Pattern**: Windows clients send backslashes (`\`) in paths, which are not treated as path separators on macOS/Linux `File(name).name`.
   - **Rule**: Always replace `\` with `/` prior to extracting `File(filename).name` to ensure cross-platform path safety.

5. **Non-Blocking UI Feedback**:
   - **Pattern**: Using browser `alert()` popups blocks thread execution and degrades UX.
   - **Rule**: Implement a modern Toast notification system for non-intrusive, styled user feedback.

6. **Jetpack Compose Two-Way Pager Synchronization**:
   - **Pattern**: Two uncoordinated `LaunchedEffect` blocks synchronizing `PagerState` with a global `MutableStateFlow` can create a violent ping-pong loop (continuous left-right shaking/sliding) if `initialPage` does not match the external flow or if intermediate animations trigger `settledPage`.
   - **Rule**: Always align external state to `targetIndex` prior to opening a pager. Guard external-to-pager animations with `currentPage != target && targetPage != target`, and sync pager-to-external state using `snapshotFlow { pagerState.settledPage }` with strict inequality checks. Also guarantee autoplay flags (`isPlaying`) are reset on entry/exit.

7. **Network Interface Filtering & Reactive IP Discovery for Local Servers**:
   - **Pattern**: Enumerating `NetworkInterface`s without filtering out cellular carrier interfaces (`rmnet`, `ccmni`, `pdp`, `wwan`) allows internal carrier CGNAT IPs (e.g., `10.x.x.x` or `100.x.x.x`) to be advertised as local file server URLs. Inbound traffic to cellular IPs is blocked by carriers and Android firewall. Additionally, caching IPs with static Compose `remember { ... }` prevents discovering newly enabled Hotspot interfaces when toggled in system settings.
   - **Rule**: Always exclude cellular WAN interfaces from local server IP advertisements, prioritize SoftAP/Hotspot interfaces (`ap`, `softap`, `swlan`, `192.168.43.1`), and use reactive lifecycle (`ON_RESUME`) + `ConnectivityManager.NetworkCallback` listeners to dynamically refresh server URLs when networks change.

8. **Full-Width Containers for Critical Network Identifiers**:
   - **Pattern**: Placing dynamic network URLs or IP addresses alongside multiple action buttons in a single cramped `Row` with `maxLines = 1` and `TextOverflow.Ellipsis` causes URLs (e.g., `http://10.112.12.215:8080`) to truncate into `http://10.112....` on mobile screens.
   - **Rule**: Always render critical, user-copyable strings (URLs, IPs, tokens) inside dedicated full-width container blocks with `softWrap = true`, keeping auxiliary action buttons (Refresh, Copy) in a header row.
