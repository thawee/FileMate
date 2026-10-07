# Web UI grid and ZIP fixes

The owner Web UI counted mirrored list and grid checkboxes as separate files. Selecting two images produced duplicate filename parameters and HTTP 500 with `Zip error: duplicate entry: first.png`. Selection now uses one entry per actual filename. Mirrored controls synchronize by numeric index, and HTML attributes preserve literal filenames.

When a thumbnail was unavailable, filenames containing an apostrophe broke the original-image fallback. A valid SVG named `owner's art.svg` reproduced `Unexpected identifier 's'` and a broken grid image. The fallback now reads its URL from an HTML data attribute instead of inserting a filename into JavaScript text.

The same SVG then returned HTTP 200 with `application/octet-stream` and still failed to display. The server now maps SVG, BMP, and ICO to browser image MIME types. These formats already belong to the Web UI's image list. No API signatures changed.

The independent review identified active SVG content as a consequence of inline image delivery. A harmless standalone SVG reproduced script execution and one owner API request before protection. Every SVG file response now sets sandbox CSP and nosniff, keyed to the file extension. The final browser reproduction displays the SVG image but executes no script and makes no owner API call. Guest SVG downloads retain their existing attachment and sandbox policies.

The Fix Root Causes principle led to correcting duplicate selection and original-image delivery. Model the Domain led to a Set of selected filenames rather than counting DOM controls. Boundary Discipline placed SVG protection in the HTTP file response. Prove It Works led to real phone-browser checks of fallback images, archive contents, and blocked SVG scripts.

## Reproduction and regression evidence

- `/private/tmp/filemate-grid-before.log` contains the original duplicate-entry HTTP 500.
- `/private/tmp/filemate-grid-fallback-before.log` contains the original fallback syntax error and broken SVG.
- `/private/tmp/filemate-grid-mime-before.log` records the original MIME response and the successful ZIP after the frontend fix.
- Four Node regressions failed against the original frontend and passed after the fix. The six existing upload-queue tests also pass.
- Two JVM MIME regressions failed before the three MIME mappings were added.
- A read-only sample of 24 existing camera images loaded successfully before the fix. That check does not prove every intermittent image failure shares these causes.

The real-device test uses unique temporary folders under Download and deletes only its own fixtures. Phone app updates preserve existing app data. The server restarts after an APK update and generates a new PIN.

## Final verification

- The final APK builds and is installed on the Samsung phone at `192.168.1.51:41255` with app data preserved.
- All 37 Android JVM tests pass. All 10 browser behavior tests pass. JavaScript syntax and `git diff --check` pass.
- The real browser displays all six fixtures covering PNG, SVG, BMP, ICO, and apostrophe filenames. SVG and ICO thumbnail requests return expected 404 responses before successful original-image fallback.
- Selecting two image checkboxes reports two files. The actual ZIP button downloads HTTP 200. The archive contains exactly `first.png` and `owner's photo.png`, each byte-identical to the original fixtures, with no ZIP integrity error.
- The SVG sandbox check returns `executed: false` and `apiCalls: 0`. The independent review has no remaining concern in scope.
- Verification fixtures were removed. The phone server remains running. Browser clients need to reload and use the current PIN after the update.

Final evidence is in `/private/tmp/filemate-grid-protected-build.log`, `/private/tmp/filemate-grid-tests-final.log`, `/private/tmp/filemate-grid-protected-runtime.log`, and `/private/tmp/filemate-grid-zip-integrity.log`. The inspected browser screenshot is `/private/tmp/filemate-grid-after.png`. The rerunnable device check is `/private/tmp/filemate-grid-zip-repro.cjs` and requires `SHAREMATE_PIN` and optionally `SHAREMATE_URL`. It uses the locally installed Playwright package and Chrome executable.
