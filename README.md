# BCA182 Activity Recognition

Real-time walking/running recognition using an RT-Spark (STM32) board, MQTT, a Flask REST API and an Android app.

## How it works
Sensor board → MQTT (`device/sensor_data`) → Flask backend (ML prediction + PostgreSQL) → REST API → Android app

## Folders
| Folder | What's inside |
|---|---|
| `mobile-app/` | Android app (Kotlin) showing live activity and history |
| `backend/` | Flask API, database, MQTT subscriber |
| `firmware/` | RT-Spark code that publishes sensor data |
| `ml/` | Model training and the trained model |
| `docs/` | Diagrams, API format, test screenshots |

## API
- `GET /api/activity/latest` – latest prediction
- `GET /api/activity/history?limit=N` – recent records

## Run the mobile app
1. Open `mobile-app/` in Android Studio and sync Gradle.
2. In `AppConfig.kt`, set `BASE_URL` to the backend laptop's IP, e.g. `http://192.168.1.10:5000`.
3. Phone and backend laptop must be on the same Wi-Fi.
4. Run on a phone (USB debugging on) or an emulator.

## Team
- Cassandra Khan P. Mendoza – Mobile app
- 

Section B187.1, MSU-IIT CCS
