# Android and Web UI review, 7 October 2026

Core sharing works on the Android 16 emulator and the Samsung SM-S931B connected at `192.168.1.51:41255`. Both devices run ShareMate 2.3.0. The current debug APK was installed on the emulator. The phone's existing 2.3.0 installation was tested without an update. This review does not establish Android 8 through 10 compatibility or universal feature coverage.

The native app browses storage and starts a foreground HTTP service. The owner dashboard manages files through authenticated APIs. Guest pages use separate token-scoped routes with download and upload permissions. Source references are in `docs/TECHNICAL.md`.

## Verified behavior

- The debug APK builds. A fresh `:app:testDebugUnitTest --rerun-tasks` run passes all 35 tests with zero failures, errors, or skips.
- All six Node browser upload-queue tests pass.
- Both devices launch the native app and show an active server and populated storage browser.
- `tools/verify-sharemate.mjs` passes on both devices. It checks owner route isolation, selected-file scope, upload-only privacy, conflict handling, folder confinement, concurrent publication, revocation, and cleanup of its unique verification folder.
- Headless Chrome renders the real guest page at a 390-pixel viewport without horizontal overflow or JavaScript errors. Its slideshow loads a served image on both devices.
- Shared HTML downloads do not execute scripts or call owner APIs even when the browser has an owner cookie.
- The authenticated owner dashboard renders a real emulator folder and a guest-uploaded ordinary text file without JavaScript errors.

The full guest/browser checks use ADB forwards. A separate read-only check from this computer reaches `http://192.168.1.51:8080` directly over Wi-Fi and receives authenticated HTTP 200 responses for health, root listing, and owner HTML. Phone testing creates and deletes only a uniquely named verification folder under Download. Existing personal files are not modified. The emulator server was restored to its original stopped state. The phone server remains running as it was before the review.

## Open findings

| Priority | Finding | Evidence and limits |
| --- | --- | --- |
| High | Android 8 and 9 storage access is missing. | `AndroidManifest.xml:4` declares all-files access but neither legacy read nor write storage permission. `MainActivity.kt:264` starts the server below API 30 without requesting legacy storage access. `FileBrowserViewModel.kt:131` lists shared storage directly. Static inference. No API 26 through 29 device was tested. Android 10 also needs a scoped-storage compatibility review. |
| High | Owner filename rendering permits HTML attribute injection. | `script.js:295` escapes JavaScript strings but not double-quoted HTML attributes. `script.js:1565` inserts that result into `data-name`, and several inline handlers use the same value. A harmless injected listing name sets `window.reviewFilenameExecuted=true` when its checkbox receives a mouseover. Confirmed in Chrome using the real owner renderer. The guest upload of that quoted filename returned HTTP 500 on emulator storage. An end-to-end stored guest exploit was not reproduced. |
| Medium | Partial incoming-share failures lose the retry selection. | `MainActivity.kt:522` displays failures, then `:525` calls `onIncomingHandled()` unconditionally. The original URI list is cleared even when some files failed. Static finding. The user must share those files again after a conflict or source-read failure. |
| Medium | Fresh owner URLs cannot show the custom PIN form. | An unauthenticated emulator request to `/` returns HTTP 401 JSON with a Basic challenge. `FileServerService.kt:213` checks authentication before serving the shell, and `ShareRoutes.kt:10` excludes the shell from public assets. QR authentication and browser Basic authentication remain usable. The JavaScript PIN recovery form cannot handle the first unauthenticated shell request. |
| Conditional | Owner path checks do not reject symlink targets. | `FileServerService.kt:922` normalizes lexical paths rather than resolving canonical paths. A readable symlink inside the root can therefore point outside it. No symlink exploit was reproduced on Android shared storage. Guest path checks reject symlinks. |
| Low | Routine list and system URLs duplicate the owner PIN in their query. | `script.js:1352` and `:1450` append the PIN although authenticated requests also send headers. URL diagnostics can expose this value. No actual log leak was observed. |

The comment review changed no files. Its text-editor comment mismatch does not establish a functional defect and is omitted from the findings.

## Evidence

Logs are local scratch artifacts.

- `/private/tmp/filemate-review-gradle.log`
- `/private/tmp/filemate-review-tests-fresh.log`
- `/private/tmp/filemate-review-browser.log`
- `/private/tmp/filemate-review-runtime.log`
- `/private/tmp/filemate-review-phone-runtime.log`
- `/private/tmp/filemate-review-owner.log`
- `/private/tmp/filemate-review-wifi.log`

Browser screenshots are `/private/tmp/filemate-review-phone-guest.png`, `/private/tmp/filemate-review-guest.png`, and `/private/tmp/filemate-review-owner.png`. The owner screenshot shows an injected in-memory listing, not a filename successfully stored on Android.

The Prove It Works principle changed verification from build-only evidence to real-device HTTP and browser checks. The Unslop skill keeps reproduced behavior separate from static findings. No application code, release, or PR was changed.
