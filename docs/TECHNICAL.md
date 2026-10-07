# ShareMate technical reference

This document covers the Android build, source structure, HTTP contracts, and implementation limits. The [README](../README.md) covers everyday use.

## Build configuration

The checked-in configuration is in [app/build.gradle.kts](../app/build.gradle.kts) and [gradle-wrapper.properties](../gradle/wrapper/gradle-wrapper.properties).

| Setting | Value |
| --- | --- |
| Application ID and namespace | `com.apincer.fileserver` |
| Minimum Android version | Android 8.0, API 26 |
| Target SDK | API 36 |
| Compile SDK | API 37 |
| Java and Kotlin toolchain | JDK 17 |
| Gradle wrapper | 9.6.0 |
| Android test runner | `androidx.test.runner.AndroidJUnitRunner` |

The ShareMate name preserves the existing application ID. The project directory and Gradle root project still use FileMate. Build dependencies and plugin versions are defined in [libs.versions.toml](../gradle/libs.versions.toml).

The build requires JDK 17 and an Android SDK installation with API 37. The SDK location comes from `local.properties` or the Android SDK environment configuration. Android Studio can import the root Gradle project. The wrapper supplies Gradle.

These commands run from the repository root:

| Command | Result |
| --- | --- |
| `./gradlew :app:testDebugUnitTest` | Local JVM tests |
| `./gradlew :app:assembleDebug` | Debug APK at `app/build/outputs/apk/debug/app-debug.apk` |
| `./gradlew :app:installDebug` | Debug app installed on a connected device or emulator |
| `./gradlew :app:assembleDebugAndroidTest` | Instrumented test APK |
| `./gradlew :app:connectedDebugAndroidTest` | Instrumented tests on connected devices or emulators |
| `node --test tools/verify/browser-*.test.cjs` | Browser upload queue, selection, and image fallback tests |

## Release artifacts

Version 2.3.1 uses Android version code 13. The GitHub preview APK comes from the debug build and uses the local Android debug signing key. The release build has no signing configuration and produces an unsigned APK. Updates require matching signing certificates.

## Launcher artwork

[generate-launcher-icons.swift](../tools/generate-launcher-icons.swift) defines the folder, wireless waves, and M artwork. It generates the adaptive foreground, a separate monochrome outline, and ten legacy PNG icons. The adaptive background is `ic_launcher_background.xml`.

To regenerate on macOS, run `swift tools/generate-launcher-icons.swift`. Add `--preview /tmp/sharemate-icons` to export color and monochrome previews.

## Source structure

The Android UI uses Jetpack Compose and Material 3. `FileServerService` owns the HTTP server, the proxy, and the in-memory guest share registry. Owner and guest browser interfaces use separate bundled assets.

The following table lists the main implementation files. Paths under the Android package are relative to `app/src/main/java/com/apincer/fileserver/`.

| Source | Responsibility |
| --- | --- |
| [MainActivity.kt](../app/src/main/java/com/apincer/fileserver/MainActivity.kt) | App entry point, server controls, Android share intents, and native navigation |
| [FileServerService.kt](../app/src/main/java/com/apincer/fileserver/FileServerService.kt) | Foreground service, owner routes, server lifecycle, and media scan notifications |
| [http/NioHttpServer.java](../app/src/main/java/com/apincer/fileserver/http/NioHttpServer.java) | Request parsing, body limits, connection handling, file responses, and HTTP byte ranges |
| [sharing/ShareSessionStore.kt](../app/src/main/java/com/apincer/fileserver/sharing/ShareSessionStore.kt) | Guest permissions, token registry, expiry, path checks, and file publication |
| [sharing/ShareRoutes.kt](../app/src/main/java/com/apincer/fileserver/sharing/ShareRoutes.kt) | Guest routes, owner share APIs, and owner authentication policy |
| [sharing/ShareControls.kt](../app/src/main/java/com/apincer/fileserver/sharing/ShareControls.kt) | Native share creation, QR codes, and revocation controls |
| [sharing/IncomingFiles.kt](../app/src/main/java/com/apincer/fileserver/sharing/IncomingFiles.kt) | Content URI reception and copies into a chosen destination |
| [sharing/PhotoShareLink.kt](../app/src/main/java/com/apincer/fileserver/sharing/PhotoShareLink.kt) | Single-photo guest URLs for DLNA receivers |
| [ui/browser/](../app/src/main/java/com/apincer/fileserver/ui/browser/) | Native file browser, previews, text editing, and directory state |
| [ui/PremiumSlideshowScreen.kt](../app/src/main/java/com/apincer/fileserver/ui/PremiumSlideshowScreen.kt) | Native photo presentation |
| [cast/](../app/src/main/java/com/apincer/fileserver/cast/) and [ui/CastComponents.kt](../app/src/main/java/com/apincer/fileserver/ui/CastComponents.kt) | AirPlay and DLNA discovery, delivery, and device selection |
| [HttpProxyServer.kt](../app/src/main/java/com/apincer/fileserver/HttpProxyServer.kt) | HTTP forwarding and CONNECT tunnels |
| [StorageMaintenanceHelper.kt](../app/src/main/java/com/apincer/fileserver/StorageMaintenanceHelper.kt) | Storage analysis, empty-folder cleanup, and junk file patterns |
| [ZipHelper.kt](../app/src/main/java/com/apincer/fileserver/ZipHelper.kt) | ZIP creation and extraction into a new destination |
| [ThumbnailHelper.kt](../app/src/main/java/com/apincer/fileserver/ThumbnailHelper.kt) | Image and video thumbnails |
| [TrafficMonitor.kt](../app/src/main/java/com/apincer/fileserver/TrafficMonitor.kt) | File server and proxy traffic counters |
| [assets/index.html](../app/src/main/assets/index.html), [style.css](../app/src/main/assets/style.css), and [script.js](../app/src/main/assets/script.js) | Owner browser dashboard and transfer queue |
| [assets/share.html](../app/src/main/assets/share.html), [share.css](../app/src/main/assets/share.css), and [share.js](../app/src/main/assets/share.js) | Guest file list, uploads, photo viewing, and slideshow |

The [Android manifest](../app/src/main/AndroidManifest.xml) declares storage access, network access, notifications, the foreground service, share intent reception, and FileProvider.

## Runtime and authentication

| Listener | Port | Authentication |
| --- | --- | --- |
| File server | `8080` | Owner PIN or a scoped guest token |
| HTTP proxy | `8081` | No PIN check in the proxy implementation |

Both ports are fixed. The proxy starts with the file server service. It is a forward proxy, separate from the owner file dashboard and guest share routes.

The foreground service holds a partial CPU wake lock while running. File responses use `FileChannel.transferTo()` and support HTTP byte-range requests. Upload request bodies are accumulated in memory before the route handler runs.

The service generates a new four-digit owner PIN at startup. [OwnerRoutePolicy](../app/src/main/java/com/apincer/fileserver/sharing/ShareRoutes.kt) accepts these credentials:

- HTTP Basic authentication with username `admin` and the current PIN as the password.
- An `X-PIN` header containing the current PIN.
- A `pin` cookie containing the current PIN.
- A `pin` query parameter containing the current PIN.

`GET /api/auth?pin=<PIN>` verifies the PIN and sets `pin=<PIN>; Path=/; SameSite=Lax`. The owner HTML response also sets this cookie after authenticated access. `/api/auth`, `/style.css`, `/script.js`, and `/marked.min.js` are public paths. Other owner routes require authentication, including `/api/health`.

Guest routes use the `/share/<token>/` namespace and do not use the owner PIN. Tokens grant the permission configured when the share was created. A guest token does not authenticate owner APIs.

The file server uses plain HTTP. PINs, cookies, files, and guest links are not encrypted in transit by this server. The four-digit PIN is intended for local owner access. It does not provide an internet-facing authentication system.

If a reverse proxy is placed in front of port 8080, it must preserve the original root paths. Owner route checks match complete paths, rather than a path fragment inside another route.

## Owner file APIs

Owner paths are relative to the current shared storage root. An empty `path` or `/` means the root. Query values use URL encoding.

The methods below are those used by the bundled dashboard. Legacy owner file handlers dispatch primarily by path and do not enforce every listed HTTP method. Share session and guest handlers enforce their methods.

| Endpoint | Dashboard method | Parameters and result |
| --- | --- | --- |
| `/api/auth` | `GET` | `pin`. Verifies the PIN and sets an owner cookie. |
| `/api/health` | `GET` | Returns `{"status":"ok"}`. |
| `/api/system` | `GET` | Device information, uptime, and storage capacity. |
| `/api/files` | `GET` | `path`, optional `showHidden=true`. Returns directory entries with names, sizes, type, and modification time. |
| `/api/upload` | `POST` | `path`, `filename`. Accepts a raw body or multipart form data. `X-File-Name` is a fallback for raw uploads. |
| `/api/download/<filename>` | `GET` | `path` identifies the parent folder. The URL filename must be a basename. Returns a file response with byte-range support. |
| `/api/delete` | `POST` | `path`, repeated `name`. Deletes files or directories recursively. A JSON body can also supply `path` and a `names` array. |
| `/api/move` | `POST` | `fromPath`, `targetPath`, repeated `name`. Refuses an existing destination in the normal handler flow. |
| `/api/rename` | `GET` | `path`, `oldName`, `newName`. Refuses an existing destination. |
| `/api/mkdir` | `POST` | `path`, `name`. Returns `409` if the destination exists. |
| `/api/thumbnail` | `GET` | `path` identifies a file. Returns a JPEG thumbnail when available. |
| `/api/download-zip` | `GET` | `path`, repeated `name`. Creates a ZIP download for the selected items. |
| `/api/unzip` | `POST` | `path`, repeated `name`. Extracts each ZIP into a new sibling folder named after the archive. |
| `/api/search` | `GET` | `path`, `q`. Recursively matches names and returns at most 100 results. |
| `/api/exif` | `GET` | `path` identifies a photo. Returns date and location text when available. Android geocoding can fall back to GPS coordinates. |
| `/api/qr` | `GET` | `path`, `name`, `isFolder`. Generates a QR image for an owner folder or download URL. This is not a guest share session. |

Batch requests repeat the `name` parameter for each complete filename. For example, `name=one.txt&name=two%2Cfinal.txt` identifies two files, one of which contains a comma. The legacy comma-separated `names` parameter remains supported.

Owner uploads accept request bodies up to 500 MiB, or `524288000` bytes. Multipart overhead counts toward the request body limit. Owner uploads can replace an existing file with the same name.

The owner browser queue records the destination when each upload is queued. It runs one active upload request and supports cancellation and retry. Cancellation cannot remove a file that the server has already received.

## Share session APIs

These endpoints require owner authentication.

| Endpoint | Method | Result |
| --- | --- | --- |
| `/api/shares` | `GET` | `{"shares":[...]}` with active share metadata. |
| `/api/shares` | `POST` | Creates a share from a JSON body and returns `201`. |
| `/api/shares/<id>` | `DELETE` | Revokes one share by metadata ID. Returns `404` if that ID is absent. |

A folder share accepts one of the modes `DOWNLOAD`, `UPLOAD`, or `DOWNLOAD_AND_UPLOAD`:

```json
{
	"folder": "/Download/Weekend photos",
	"mode": "DOWNLOAD_AND_UPLOAD",
	"lifetimeMinutes": 60
}
```

A selected-file share supports `DOWNLOAD` only:

```json
{
	"paths": ["/Pictures/portrait.jpg", "/Download/notes.txt"],
	"mode": "DOWNLOAD",
	"lifetimeMinutes": 15
}
```

The request uses either `folder` or `paths`. `paths` accepts 1 to 200 existing files with distinct basenames. Directories require a folder share. `lifetimeMinutes` accepts `15`, `60`, or `1440` and defaults to `60`. `mode` defaults to `DOWNLOAD`.

The creation response contains `id`, `label`, `mode`, `expiresAtMillis`, and `urlPath`. `urlPath` has the form `/share/<token>/`. List responses contain the metadata fields without the token URL. Share sessions remain in memory and disappear when the server stops or restarts.

## Guest APIs

All paths in this table are relative to `/share/<token>/`.

| Path | Method | Contract |
| --- | --- | --- |
| Empty path | `GET` | Guest HTML page. A missing trailing slash redirects to the slash form. |
| `share.css`, `share.js` | `GET` | Guest page assets. |
| `api/items` | `GET` | Optional `path` for a subfolder. Returns share metadata and `items`. Upload-only shares return an empty item list. |
| `download` | `GET` | `path` identifies a shared file. Requires download permission. |
| `upload` | `POST` | `name` identifies the new file. Requires upload permission and a raw request body with `Content-Length`. |

`api/items` returns `label`, `mode`, `expiresAtMillis`, `maxUploadBytes`, and `items`. Each item contains `name`, `path`, `isDirectory`, and `size`. Selected-file shares list only those files and do not accept a subfolder path.

Guest upload destinations are the folder selected by the owner. The upload API has no destination-path parameter. The limit is 20 MiB per file, or `20971520` bytes. A successful upload returns `201` with `{"name":"<filename>"}`.

| Status | Guest condition |
| --- | --- |
| `400` | Invalid path, invalid filename, incomplete body, or unsupported `Transfer-Encoding` |
| `403` | Permission denied or a symbolic link |
| `404` | Unknown guest route or unavailable shared item |
| `409` | The upload filename already exists |
| `410` | The share token is unknown, expired, or revoked |
| `411` | A guest POST lacks `Content-Length` |
| `413` | The declared upload body exceeds 20 MiB |

The NIO parser rejects oversized declared bodies before it accumulates the full payload. `Transfer-Encoding`, including chunked uploads, is unsupported.

## File and guest security limits

Guest tokens contain 32 random bytes from `SecureRandom`, encoded as URL-safe Base64. The registry stores SHA-256 token hashes. Links are bearer credentials, so anyone who receives a link has that share's permissions until expiry or revocation.

Guest path checks compare canonical paths and reject symbolic links, absolute paths, `.` and `..` segments, backslashes, and NUL characters. Selected-file shares resolve only the basenames recorded in their scope. Legacy owner file APIs use normalized paths to enforce their storage boundary. Those checks are not the same as the guest canonical-path checks.

Guest and Android incoming-file copies write to a temporary `.sharemate-` file before publication. A process-wide lock serializes publication by the app. An existing filename returns a conflict instead of being replaced. Another process writing the same storage directory is outside that lock.

Guest responses set `Cache-Control: no-store`, `Referrer-Policy: no-referrer`, `X-Content-Type-Options: nosniff`, and a restrictive content security policy. Raster images can display inline. Other downloads, including HTML, JavaScript, and SVG, use attachment disposition and a sandbox policy.

ZIP extraction refuses an existing destination and publishes a completed extraction into a new folder. Storage cleanup remains a native tool. It has no owner web API. Both cleanup actions skip the Android subtree before recursion, including a selected root inside Android and paths that resolve into it. Cleanup candidate counts exclude Android content; storage totals still include it. Junk cleanup permanently deletes matching files elsewhere after confirmation, including backup and temporary filename patterns.

Android share reception accepts `ACTION_SEND` and `ACTION_SEND_MULTIPLE` content URIs. It deduplicates the URIs and processes up to 200 files after the user chooses a destination. FileProvider supplies content URIs when the app launches external viewers.

## Casting implementation

The [CastingManager](../app/src/main/java/com/apincer/fileserver/cast/CastingManager.kt) discovers AirPlay receivers through Android network service discovery for `_airplay._tcp.`. It discovers DLNA AVTransport services through SSDP multicast and reads their advertised device descriptions.

| Receiver | Photo delivery | Dependency and limit |
| --- | --- | --- |
| DLNA | `SetAVTransportURI`, followed by `Play` | The receiver fetches a single-photo guest download URL from port 8080. The file server must be running and reachable from the TV. Each URL creates a one-hour download share. |
| AirPlay | HTTP `PUT` to the receiver's `/photo` endpoint | The app sends image bytes directly. Compatibility depends on the receiver's photo endpoint and access requirements. |
| Google Cast | No direct photo option in the current device chooser | [CastOptionsProvider](../app/src/main/java/com/apincer/fileserver/cast/CastOptionsProvider.kt) still configures the Default Media Receiver. The app does not include a custom photo receiver. |

A TV browser can open a guest photo folder and use the browser slideshow. The current implementation does not establish universal TV compatibility. Physical AirPlay and DLNA receivers require device-specific runtime verification.

## Verification tools

Local JVM tests live in [app/src/test/](../app/src/test/). They cover request body assembly, share scopes, authentication routes, filenames, ZIP extraction, directory state, image naming and MIME types, and network address selection. [Browser upload queue tests](../tools/verify/browser-upload-queue.test.cjs) and [grid selection and image fallback tests](../tools/verify/browser-file-grid.test.cjs) use Node's built-in test runner.

The [IncomingFilesInstrumentedTest](../app/src/androidTest/java/com/apincer/fileserver/sharing/IncomingFilesInstrumentedTest.kt) exercises Android content URI copies, filename conflicts, and failed reads. After installation of both APKs, its direct runner command is:

```sh
adb shell am instrument -w \
	-e class com.apincer.fileserver.sharing.IncomingFilesInstrumentedTest \
	com.apincer.fileserver.test/androidx.test.runner.AndroidJUnitRunner
```

[verify-sharemate.mjs](../tools/verify-sharemate.mjs) checks actual owner and guest HTTP routes against a running app. The Node runtime must provide `fetch`. The script creates a uniquely named fixture folder under `Download`, creates and revokes its shares, and removes that fixture folder afterward.

| Environment variable | Meaning |
| --- | --- |
| `SHAREMATE_PIN` | Required current owner PIN |
| `SHAREMATE_URL` | Server address, default `http://127.0.0.1:18080` |
| `SHAREMATE_PLAYWRIGHT` | Optional path to an installed Playwright package for real browser checks |
| `SHAREMATE_CHROME` | Optional browser executable path for Playwright |
| `SHAREMATE_SCREENSHOT` | Optional output path for the guest browser screenshot |

For an emulator with port forwarding, the commands are:

```sh
adb forward tcp:18080 tcp:8080
SHAREMATE_PIN='replace-with-current-pin' node tools/verify-sharemate.mjs
```

The HTTP checks cover owner route isolation, selected-file confinement, upload-only permissions, conflicts, traversal rejection, and revocation. Optional browser checks cover the guest page at phone width, served photo display, and active-content isolation with an owner cookie.
