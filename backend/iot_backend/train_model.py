import pandas as pd
import numpy as np
import tensorflow as tf
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import StandardScaler
import joblib

# 1. Load Dataset
df = pd.read_csv('dataset.csv')

print("Columns found in CSV:", list(df.columns))

# Clean column names by stripping spaces and lowering case
df.columns = df.columns.str.strip().str.lower()

# Map potential column names from Kaggle's run-or-walk dataset
feature_cols = []
possible_accel_x = ['acceleration_x', 'accel_x', 'acc_x']
possible_accel_y = ['acceleration_y', 'accel_y', 'acc_y']
possible_accel_z = ['acceleration_z', 'accel_z', 'acc_z']
possible_gyro_x  = ['gyro_x', 'gyroscope_x']
possible_gyro_y  = ['gyro_y', 'gyroscope_y']
possible_gyro_z  = ['gyro_z', 'gyroscope_z']

def find_col(possible_names):
    for col in possible_names:
        if col in df.columns:
            return col
    raise KeyError(f"Could not find any matching column from: {possible_names}")

ax_col = find_col(possible_accel_x)
ay_col = find_col(possible_accel_y)
az_col = find_col(possible_accel_z)
gx_col = find_col(possible_gyro_x)
gy_col = find_col(possible_gyro_y)
gz_col = find_col(possible_gyro_z)

# Extract Features & Target
X = df[[ax_col, ay_col, az_col, gx_col, gy_col, gz_col]].values
y = df['activity'].values

# 2. Scale Features
scaler = StandardScaler()
X_scaled = scaler.fit_transform(X)

# Save scaler for backend inference
joblib.dump(scaler, 'scaler.pkl')

# 3. Train-Test Split
X_train, X_test, y_train, y_test = train_test_split(X_scaled, y, test_size=0.2, random_state=42)

# 4. Build Lightweight Neural Network Model
model = tf.keras.Sequential([
    tf.keras.layers.Dense(32, activation='relu', input_shape=(6,)),
    tf.keras.layers.Dropout(0.2),
    tf.keras.layers.Dense(16, activation='relu'),
    tf.keras.layers.Dense(1, activation='sigmoid')
])

model.compile(optimizer='adam', loss='binary_crossentropy', metrics=['accuracy'])

# 5. Train Model
print("\nTraining movement classifier...")
model.fit(X_train, y_train, epochs=15, batch_size=64, validation_split=0.1)

# 6. Evaluate & Save
loss, accuracy = model.evaluate(X_test, y_test)
print(f"\nModel Test Accuracy: {accuracy * 100:.2f}%")

model.save('activity_model.h5')
print("Saved 'activity_model.h5' and 'scaler.pkl' successfully.")