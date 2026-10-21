# Activity Recognition App (Kotlin)

Shows the latest recognized activity (walking/running) and a history list from the backend REST API.

## Run it
1. Android Studio → **Open** → select this folder → wait for Gradle sync.
2. Run on your Android phone or emulator. It starts in **mock mode** (no backend needed).

## Switch to the real backend
Edit `app/src/main/java/com/example/activityrecognition/AppConfig.kt`:
- `USE_MOCK_DATA = false`
- `BASE_URL = "http://<laptop-ip>:5000"` (phone and laptop must be on the same Wi-Fi; no localhost)

## If the JSON format differs
Only `RecordParser.kt` needs changing.

## Files
- `MainActivity.kt` – screen, auto-refresh, animations, details dialog
- `ActivityRepository.kt` – interface; `MockRepository.kt` / `ApiRepository.kt` (Volley)
- `RecordParser.kt` – JSON → `ActivityRecord`
- `HistoryAdapter.kt`, `ActivityStyle.kt`, `TimeFormat.kt`, `AppConfig.kt`
