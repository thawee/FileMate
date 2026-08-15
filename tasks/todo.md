# App Update Plan: Full Feature Enhancement

## Objective
Implement a suite of features to improve the FileServer app's security, usability, and functionality.

## 1. Security (Basic Auth / PIN)
- [x] Create `AuthHelper.kt` to generate a random 4-digit PIN when the server starts.
- [x] Update `FileServerService.kt` `handleRoute` to require a valid `Authorization` header or cookie (except for public assets).
- [x] Expose the current PIN via `AuthHelper` to display in `MainActivity`.

## 2. ZIP & Unzip (Batch Downloads)
- [x] Create `ZipHelper.kt` with robust `zipFiles` and `unzipFile` methods.
- [x] Add `/api/download-zip` to `FileServerService.kt`.
- [x] Add `/api/unzip` to `FileServerService.kt`.

## 3. Media Thumbnails
- [x] Create `ThumbnailHelper.kt` to generate optimized 100x100 `Bitmap` previews for images/videos.
- [x] Add `/api/thumbnail?path=...` in `FileServerService.kt` that returns JPEG bytes.

## 4. Dedicated Rename API
- [x] Add `/api/rename?path=...&oldName=...&newName=...` in `FileServerService.kt`.

## 5. Recursive File Search
- [x] Add `/api/search?q=...&path=...` in `FileServerService.kt` that recursively searches directories.

## 6. Android App UI Polish
- [x] Update `MainActivity.kt` to display the server IP, PIN, and a QR Code (using ZXing) for quick connection.
- [x] Add `WakeLock` to `FileServerService` to keep the CPU awake during active file transfers.

*Note: NioHttpServer already fully supports HTTP 206 Partial Content (Range requests) via `createFileResponse()`, so streaming works natively!*

## 7. Media Casting (AirPlay + DLNA)
- [x] Add dependencies for OkHttp (for AirPlay HTTP PUTs) and Cling (for DLNA/UPnP discovery).
- [x] Create `CastingManager.kt` interface with `discover()`, `connect()`, `showImage()`, and `disconnect()`.
- [x] Implement `AirPlayCaster.kt` using `NsdManager` for discovery and `OkHttp` to push JPEG bytes to `/photo`.
- [x] Implement `DlnaCaster.kt` using `Cling` to send `SetAVTransportURI` and `Play` commands via SOAP.
- [x] Update `MainActivity.kt` UI with a "Cast" button, a device discovery dialog, and slideshow controls (Next/Previous).
- [x] Provide the `NioHttpServer` URLs to `DlnaCaster` when sending images.
## 8. Web Slideshow EXIF Info (Date & Location)
- [x] Add `androidx.exifinterface` dependency to Gradle.
- [x] Create `/api/exif` endpoint in `FileServerService.kt` to extract tags and reverse-geocode coordinates.
- [x] Update `index.html` and `style.css` with a sleek overlay for EXIF info.
- [x] Update `script.js` to fetch and render EXIF data during slideshow playback.
