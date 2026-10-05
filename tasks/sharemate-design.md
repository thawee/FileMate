# ShareMate sharing design

ShareMate shares selected files and folders with nearby devices through a browser. Photo presentation is a supporting workflow. Owner file management remains available behind the owner's PIN.

## Usage contract

The owner chooses files or a folder, chooses download access, upload access, or both, and selects a finite lifetime. ShareMate returns a guest link and QR code. Guests can use only that share's content and permissions. The owner can revoke a share. Stopping the server discards active shares.

Sharing a set of files exposes only those files. Sharing a folder exposes its descendants. Upload access writes only to the chosen folder and never replaces existing files. Upload-only guests cannot list existing content. Photo presentation delivers image bytes, so recipients can save images they view.

Android receiving accepts content URIs through the system Share menu. The user confirms a destination before ShareMate copies the content. Failed copies do not publish partial files. Browser transfers retain their destination when queued and have queued, uploading, succeeded, failed, and cancelled states.

## Ownership and boundaries

A pure Kotlin share store owns grants, permission modes, canonical scope resolution, expiry, and revocation. FileServerService adapts exact guest routes to that store and file responses. Guest routes terminate in a dedicated dispatcher and page. They do not reuse owner authorization or owner mutation handlers.

Native UI and owner-authenticated share management use the same store. The service clears it on shutdown. Guest resources contain no external dependencies. Guest responses disable caching and referrers. Guest tokens are random bearer capabilities and do not carry the owner PIN.

The browser upload queue owns upload task state and permits only one active request. Android intent handling owns incoming content. Neither changes a guest grant implicitly.

## Synthesis decision

Candidate A uses a separate guest namespace. Candidate B extends existing owner APIs with a guest principal. The independent design judge preferred A for authorization isolation, maintainability, and preservation of owner behavior. Adopt A, with B's distinction between selected file entries and a folder tree. Avoid B's root-scoped guest cookie and handler-wide guest mode.

The review found that current owner routing combines a public authentication substring with protected route suffixes. Exact public and API route matching is a required prerequisite. Add regression checks for composed paths such as `/api/auth/x/api/files`.

Path tokens keep this local implementation small. They remain bearer secrets in browser history. The response headers and absence of external resources reduce accidental forwarding. They do not encrypt local HTTP traffic.

## Verification contract

Verify expiry, revocation, selected-file confinement, canonical symlink boundaries, traversal rejection, upload-only confidentiality, concurrent filename conflicts, and owner-route isolation. Exercise actual HTTP guest routes as well as the pure domain model.

Verify the Android brand, sharing controls, content receiving, and local photo entry on the emulator. Exercise the real browser for downloads, uploads, cancellation, and retry. Hardware casting compatibility remains unverified unless a receiver is available.

## Accepted implementation constraints

Publish completed uploads using one app-owned publication lock and a move without replacement. This prevents competing ShareMate writes from replacing an existing file. External filesystem writers can still race publication; the Android shared-storage provider does not offer the portable hard-link strategy considered during design.

DLNA image URLs must resolve to a selected-file guest download route. The old `/files/` URL has no corresponding file-serving route. Receiver links must not carry the owner PIN. AirPlay can push image bytes without starting the file server.

Google's default receiver handles audio and video. An image-gallery receiver requires a custom receiver, as documented in the [Google Cast receiver guide](https://developers.google.com/cast/docs/web_receiver). Do not present the unimplemented Google photo sender as a working destination. Retain browser presentation and AirPlay or DLNA image paths for this release.
