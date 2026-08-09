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
