import json
import math
from collections import deque
import psycopg2
import numpy as np
import joblib
import tensorflow as tf
import paho.mqtt.client as mqtt
from flask import Flask, jsonify, request

app = Flask(__name__)

# --- Load ML Model & Scaler ---
print("Loading trained activity model and scaler...")
model = tf.keras.models.load_model('activity_model.h5')
scaler = joblib.load('scaler.pkl')

# --- Windowed (time-series) prediction settings ---
# WINDOW must match WINDOW in train_model.py.
WINDOW = 10
recent_readings = deque(maxlen=WINDOW)   # last WINDOW readings, in dataset units

# The RT-Spark sends RAW ICM20608 sensor counts. The model was trained on the
# Kaggle dataset: accelerometer in g, gyroscope in rad/s. Convert before predicting.
RAW_SENSOR_VALUES = True
ACCEL_LSB_PER_G = 16384.0     # +-2 g range (az is ~16000 when the board lies flat)
GYRO_LSB_PER_DPS = 16.4       # +-2000 dps range. If the driver uses +-250 dps, use 131.0

# --- PostgreSQL Setup ---
DB_CONFIG = {
    "host": "localhost",
    "database": "activity_data",
    "user": "admin",
    "password": "password",
    "port": 5432
}

def init_db():
    """Initializes the database table for storing activity logs."""
    try:
        conn = psycopg2.connect(**DB_CONFIG)
        cur = conn.cursor()
        cur.execute("""
            CREATE TABLE IF NOT EXISTS activity_logs (
                id SERIAL PRIMARY KEY,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                activity VARCHAR(50) NOT NULL,
                ax FLOAT, ay FLOAT, az FLOAT,
                gx FLOAT, gy FLOAT, gz FLOAT
            );
        """)
        conn.commit()
        cur.close()
        conn.close()
        print("Database initialized successfully.")
    except Exception as e:
        print(f"Database initialization error: {e}")

init_db()

# --- ML Inference ---
def to_model_units(sensor_data):
    """Returns [ax, ay, az, gx, gy, gz] in g and rad/s (the training units)."""
    ax = float(sensor_data.get('ax', 0.0))
    ay = float(sensor_data.get('ay', 0.0))
    az = float(sensor_data.get('az', 0.0))
    gx = float(sensor_data.get('gx', 0.0))
    gy = float(sensor_data.get('gy', 0.0))
    gz = float(sensor_data.get('gz', 0.0))
    if RAW_SENSOR_VALUES:
        ax, ay, az = ax / ACCEL_LSB_PER_G, ay / ACCEL_LSB_PER_G, az / ACCEL_LSB_PER_G
        k = math.pi / 180.0 / GYRO_LSB_PER_DPS      # raw counts -> rad/s
        gx, gy, gz = gx * k, gy * k, gz * k
    return [ax, ay, az, gx, gy, gz]


def predict_activity(sensor_data):
    """
    Adds the newest reading to a sliding window of the last WINDOW readings and
    predicts the activity from the whole window (uses temporal information).
    Expected dict keys: ax, ay, az, gx, gy, gz
    """
    recent_readings.append(to_model_units(sensor_data))

    window = list(recent_readings)
    # Right after startup the window isn't full yet: pad it by repeating the oldest reading.
    while len(window) < WINDOW:
        window.insert(0, window[0])

    scaled = scaler.transform(np.array(window))            # (WINDOW, 6)
    scaled = scaled.reshape(1, WINDOW, 6).astype(np.float32)

    # Output probability > 0.5 = Running, else Walking
    prediction = model.predict(scaled, verbose=0)[0][0]
    return "running" if prediction > 0.5 else "walking"

# --- MQTT Callbacks ---
def on_connect(client, userdata, flags, reason_code, properties):
    print(f"Connected to MQTT Broker (Code: {reason_code})")
    client.subscribe("device/sensor_data")

def on_message(client, userdata, msg):
    try:
        payload = json.loads(msg.payload.decode())
        activity = predict_activity(payload)
        
        # Save to PostgreSQL
        conn = psycopg2.connect(**DB_CONFIG)
        cur = conn.cursor()
        cur.execute("""
            INSERT INTO activity_logs (activity, ax, ay, az, gx, gy, gz)
            VALUES (%s, %s, %s, %s, %s, %s, %s);
        """, (
            activity,
            payload.get('ax', 0.0), payload.get('ay', 0.0), payload.get('az', 0.0),
            payload.get('gx', 0.0), payload.get('gy', 0.0), payload.get('gz', 0.0)
        ))
        conn.commit()
        cur.close()
        conn.close()
        print(f"Logged Real-Time Prediction: [{activity.upper()}] | Data: {payload}")
    except Exception as e:
        print(f"Error processing MQTT message: {e}")

# Start MQTT Client
mqtt_client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
mqtt_client.on_connect = on_connect
mqtt_client.on_message = on_message
mqtt_client.connect("localhost", 1883, 60)
mqtt_client.loop_start()

# --- REST API Endpoints for Android App ---
@app.route('/api/activity/latest', methods=['GET'])
def get_latest_activity():
    """Returns the most recent activity log."""
    try:
        conn = psycopg2.connect(**DB_CONFIG)
        cur = conn.cursor()
        cur.execute("SELECT timestamp, activity, ax, ay, az, gx, gy, gz FROM activity_logs ORDER BY id DESC LIMIT 1;")
        row = cur.fetchone()
        cur.close()
        conn.close()
        
        if row:
            return jsonify({
                "timestamp": str(row[0]),
                "activity": row[1],
                "accelerometer": {"x": row[2], "y": row[3], "z": row[4]},
                "gyroscope": {"x": row[5], "y": row[6], "z": row[7]}
            })
        return jsonify({"message": "No activity data recorded yet"}), 404
    except Exception as e:
        return jsonify({"error": str(e)}), 500

@app.route('/api/activity/history', methods=['GET'])
def get_activity_history():
    """Returns historical activity records (default limit: 20)."""
    limit = request.args.get('limit', default=20, type=int)
    try:
        conn = psycopg2.connect(**DB_CONFIG)
        cur = conn.cursor()
        cur.execute("SELECT id, timestamp, activity FROM activity_logs ORDER BY id DESC LIMIT %s;", (limit,))
        rows = cur.fetchall()
        cur.close()
        conn.close()
        
        history = [{"id": r[0], "timestamp": str(r[1]), "activity": r[2]} for r in rows]
        return jsonify(history)
    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True, use_reloader=False)