from flask import Flask, request, jsonify
import joblib
import numpy as np
import os
from datetime import datetime

app = Flask(__name__)

# Load the best model (XGBoost)
MODEL_PATH = os.path.join(os.path.dirname(__file__), 'models', 'xgboost_model.pkl')
model = joblib.load(MODEL_PATH)
print(f"Model loaded from: {MODEL_PATH}")

@app.route('/predict', methods=['POST'])
def predict():
    """
    Expects JSON:
    {
        "co": 200.5,
        "no": 1.2,
        "no2": 15.3,
        "nox": 16.5,
        "o3": 40.1,
        "pm10": 60.2,
        "pm25": 35.1,
        "so2": 5.0,
        "temperature": 28.5,
        "relativehumidity": 75.0,
        "wind_speed": 10.5,
        "wind_direction": 180.0,
        "aqi_lag_1": 138,
        "aqi_lag_2": 130
    }
    Returns:
    {
        "predicted_aqi": 145,
        "hour": 15,
        "model": "xgboost"
    }
    """
    try:
        data = request.get_json()

        now = datetime.now()
        hour        = data.get('hour',          now.hour)
        day_of_week = data.get('day_of_week',   now.weekday())
        month       = data.get('month',         now.month)

        # Feature order must match training
        features = np.array([[
            data.get('co',               0.0),
            data.get('no',               0.0),
            data.get('no2',              0.0),
            data.get('nox',              0.0),
            data.get('o3',               0.0),
            data.get('pm10',             0.0),
            data.get('pm25',             0.0),
            data.get('so2',              0.0),
            data.get('temperature',      25.0),
            data.get('relativehumidity', 60.0),
            data.get('wind_speed',       5.0),
            data.get('wind_direction',   180.0),
            hour,
            day_of_week,
            month,
            data.get('aqi_lag_1',        data.get('current_aqi', 100)),
            data.get('aqi_lag_2',        data.get('current_aqi', 100)),
        ]])

        predicted = model.predict(features)[0]
        predicted_aqi = max(0, int(round(predicted)))

        return jsonify({
            "predicted_aqi": predicted_aqi,
            "hour": hour,
            "model": "xgboost"
        })

    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "UP", "model": "xgboost"})


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=False)
