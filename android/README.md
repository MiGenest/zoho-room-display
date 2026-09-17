# Room Display Android App — Setup

1. Open Android Studio → **New Project** → **Empty Activity (Compose)**.
   Package name: `com.example.roomdisplay` (or change it, and update it in `MainActivity.kt` too).
2. Replace the generated `app/build.gradle.kts` with the one in this folder, and `AndroidManifest.xml` / `MainActivity.kt` the same way.
3. In `MainActivity.kt`, set `STATUS_URL` to your deployed Worker's URL (`.../api/status`).
4. Sync Gradle, run on the tablet.
5. It goes fullscreen automatically on launch; green = FREE, red = BUSY.

## Notes

- No settings screen, no login — the URL is hardcoded on purpose, since this is one tablet for one room. If you later want a settings screen to change the URL without rebuilding, that's a small addition to `MainActivity.kt`.
- If the tablet ever shows a permanently gray "..." screen, the Worker call is failing — check `STATUS_URL` first, then hit the URL directly from a browser to see the raw JSON/error.
