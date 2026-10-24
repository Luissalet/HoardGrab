# Hoard Grab

Android app that downloads videos from X (Twitter) and Instagram straight into the phone gallery. It is the mobile counterpart of the Links hoard downloader: the same engine (yt-dlp, H.264 + AAC first so every player opens the file) and the same idea of reusing a signed-in session through cookies.

## What it does

- **Share → Hoard Grab** from the X or Instagram app: the download starts in the background with a progress notification, and the app does not open.
- **Paste a link** in the app (it auto-fills a copied X/Instagram link that is not downloaded yet) and tap *Descargar*.
- Saves to `Movies/Hoard Grab` (photos to `Pictures/Hoard Grab`), so it shows up in Google Photos / the gallery. Posts with several videos save all of them.
- **Accounts**: sign in to X and Instagram inside the app (username and password; Google sign-in does not work inside an embedded browser). The session is used for private accounts, sensitive content and rate limits. Public posts work without signing in.
- **Queue**: share several links in a row; they download one after another. A notification lets you cancel.
- **History** with thumbnails: tap to play; the ⋮ menu shares the file, opens the original post, copies the link, removes it from the list or deletes the file.
- **yt-dlp updates itself** every 24 h before a download, and retries once after updating when a download fails. *Actualizar yt-dlp* forces it.
- Readable errors in Spanish (no video in the post, needs sign-in, deleted post, rate limit, no connection).

## Install on the phone

1. On the phone: Settings → About phone → tap *Build number* 7 times; then Settings → System → Developer options → *USB debugging* on.
2. Plug the phone into the PC and accept the "Allow USB debugging" prompt.
3. Double-click `instalar.cmd`. It builds the APK, installs it and opens the app.

Requirements on the PC: Android Studio (its bundled JDK and the Android SDK).

## Build details

- Kotlin, Android views + Material 3, minSdk 29, targetSdk 36.
- Engine: [youtubedl-android](https://github.com/JunkFood02/youtubedl-android) 0.18.1 (embedded Python, yt-dlp and ffmpeg).
- One APK per ABI: `app-arm64-v8a-release.apk` (phones, ~64 MB) and `app-x86_64-release.apk` (emulator).
- Release builds are signed with the local debug key so they install over USB with no keystore setup.

## Code map

| File | Role |
| --- | --- |
| `MainActivity.kt` | Link field, paste, accounts, current download, history |
| `ShareActivity.kt` | Receives shared links and queues them |
| `DownloadService.kt` | Foreground queue, progress notification, retry after update, saving |
| `Engine.kt` | yt-dlp setup, update, download options, error classification |
| `Cookies.kt` | WebView sessions → Netscape `cookies.txt` for yt-dlp |
| `LoginActivity.kt` | In-app sign-in for X and Instagram |
| `Store.kt` | Shared state, MediaStore saving, download history |
| `Links.kt` | Extracts the URL from shared text and normalises mirror domains |

## Limits

- Only what yt-dlp supports for X and Instagram: stories and highlights need a signed-in session; photo-only posts are not the target (yt-dlp may save nothing).
- No Play Store distribution: it is installed from the PC.
