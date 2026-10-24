"""
Train the walking/running classifier on WINDOWS of readings (time series).

Run from backend/iot_backend/:   python train_model.py

Why windows? A single reading can't show a pattern over time. A window of
WINDOW readings in a row lets the model learn the motion pattern (1D-CNN).

Why STEP = 5? The dataset is sampled at ~5 Hz (every 0.2 s) but the RT-Spark
publishes ~1 reading per second. Taking every 5th dataset sample makes the
training windows look like what the backend receives from the board.

Outputs (same file names app.py already loads):
    activity_model.h5   - trained model, input shape (WINDOW, 6)
    scaler.pkl          - StandardScaler for the 6 sensor channels
Units of the dataset: accelerometer in g, gyroscope in rad/s.
"""
import joblib
import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.preprocessing import StandardScaler

WINDOW = 10        # readings per window (about 10 s of board data)
STEP = 5           # keep every 5th dataset sample (5 Hz -> 1 Hz)
STRIDE = 2         # move forward this many readings between windows
TEST_FRACTION = 0.2
SEED = 42

np.random.seed(SEED)
tf.random.set_seed(SEED)

# ---------------------------------------------------------------- 1. load
df = pd.read_csv('dataset.csv')
df.columns = df.columns.str.strip().str.lower()

FEATURES = ['acceleration_x', 'acceleration_y', 'acceleration_z',
            'gyro_x', 'gyro_y', 'gyro_z']

# parse "13:51:15:847724020" (h:m:s:nanoseconds) into a real time
parts = df['time'].str.rsplit(':', n=1, expand=True)
df['t'] = pd.to_datetime(df['date'] + ' ' + parts[0] + '.' + parts[1].str[:6],
                         errors='coerce')
df['dt'] = df['t'].diff().dt.total_seconds()

# Rows are in recording order, but timestamps jitter slightly (about +-0.2 s),
# so only a real jump (> 1 s) or a change of activity/user/wrist starts a new recording.
new_seg = (
    (df['activity'] != df['activity'].shift())
    | (df['username'] != df['username'].shift())
    | (df['wrist'] != df['wrist'].shift())
    | (df['dt'].isna())
    | (df['dt'].abs() > 1.0)
)
df['segment'] = new_seg.cumsum()
print(f"Rows: {len(df)}, continuous segments: {df['segment'].nunique()}")
print(f"Median sample interval: {df['dt'].median():.3f} s")

# ------------------------------------------------------------ 2. windows
def make_windows(values, label):
    """values: (n, 6) array of one continuous segment."""
    out = []
    for phase in range(STEP):                      # use every phase so no data is wasted
        sub = values[phase::STEP]
        for start in range(0, len(sub) - WINDOW + 1, STRIDE):
            out.append(sub[start:start + WINDOW])
    return out, [label] * len(out)


# Split by whole recording (segment), not by random windows, so a test window never
# comes from a recording the model trained on.
segments = [(seg[FEATURES].values, int(seg['activity'].iloc[0]))   # 1 = running, 0 = walking
            for _, seg in df.groupby('segment') if len(seg) >= WINDOW * STEP]
rng = np.random.RandomState(SEED)
is_test = rng.rand(len(segments)) < TEST_FRACTION

X_train, y_train, X_test, y_test = [], [], [], []
for (values, label), test in zip(segments, is_test):
    w, l = make_windows(values, label)
    (X_test if test else X_train).extend(w)
    (y_test if test else y_train).extend(l)

X_train = np.array(X_train, dtype=np.float32)
X_test = np.array(X_test, dtype=np.float32)
y_train = np.array(y_train, dtype=np.float32)
y_test = np.array(y_test, dtype=np.float32)
print(f"Train windows: {X_train.shape}  Test windows: {X_test.shape}")
print(f"Running share  train: {y_train.mean():.2f}  test: {y_test.mean():.2f}")

# ------------------------------------------------------------- 3. scale
# Fit the scaler on training readings only, then apply to every time step.
scaler = StandardScaler()
scaler.fit(X_train.reshape(-1, 6))
joblib.dump(scaler, 'scaler.pkl')


def scale(x):
    n = x.shape[0]
    return scaler.transform(x.reshape(-1, 6)).reshape(n, WINDOW, 6).astype(np.float32)


X_train_s, X_test_s = scale(X_train), scale(X_test)

# -------------------------------------------------------------- 4. model
model = tf.keras.Sequential([
    tf.keras.layers.Input(shape=(WINDOW, 6)),
    tf.keras.layers.Conv1D(32, 3, activation='relu', padding='same'),
    tf.keras.layers.Conv1D(32, 3, activation='relu', padding='same'),
    tf.keras.layers.GlobalAveragePooling1D(),
    tf.keras.layers.Dropout(0.3),
    tf.keras.layers.Dense(16, activation='relu'),
    tf.keras.layers.Dense(1, activation='sigmoid'),
])
model.compile(optimizer='adam', loss='binary_crossentropy', metrics=['accuracy'])
model.summary()

# -------------------------------------------------------------- 5. train
model.fit(X_train_s, y_train, epochs=20, batch_size=64,
          validation_split=0.1, shuffle=True)

# ------------------------------------------------------ 6. evaluate/save
loss, accuracy = model.evaluate(X_test_s, y_test, verbose=0)
print(f"\nTest accuracy (unseen part of each recording): {accuracy * 100:.2f}%")

model.save('activity_model.h5')
print("Saved 'activity_model.h5' and 'scaler.pkl'.")
print(f"Remember: app.py must use WINDOW = {WINDOW}.")