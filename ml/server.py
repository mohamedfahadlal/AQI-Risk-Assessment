import sys
import io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

from flask import Flask, request, jsonify
import joblib
import pandas as pd
import xgboost as xgb
import os
from datetime import datetime

app = Flask(__name__)

MODEL_DIR = os.path.join(os.path.dirname(__file__), 'models')

models = {}
model_files = {
    "xgboost":      "xgboost_model.pkl",
    "randomforest": "randomforest_model.pkl",
    "lightgbm":     "lightgbm_model.pkl"
}

for name, filename in model_files.items():
    path = os.path.join(MODEL_DIR, filename)
    if os.path.exists(path):
        models[name] = joblib.load(path)
        print(f"[OK] Loaded model: {name} from {path}")
    else:
        print(f"[MISSING] Model not found: {path}")

# Each model has its own feature set matching training
FEATURES = {
    "randomforest": [
        'co', 'no', 'no2', 'o3',
        'pm10', 'pm25',
        'temperature', 'relativehumidity',
        'wind_speed', 'wind_direction',
        'hour',
        'aqi_lag_1', 'aqi_lag_2'
    ],
    "xgboost": [
        'lat', 'lon', 'co', 'no', 'no2', 'o3',
        'pm10', 'pm25', 'relativehumidity', 'so2', 'temperature',
        'si_pm25', 'si_pm10', 'AQI',
        'hour', 'day_of_week', 'month',
        'aqi_lag_1', 'aqi_lag_2'
    ],
    "lightgbm": [
        'lat', 'lon', 'co', 'no', 'no2', 'o3',
        'pm10', 'pm25', 'relativehumidity', 'so2', 'temperature',
        'si_pm25', 'si_pm10', 'AQI',
        'hour', 'day_of_week', 'month',
        'aqi_lag_1', 'aqi_lag_2'
    ]
}

def calc_si_pm25(pm25):
    table = [(0,30,0,50),(30,60,51,100),(60,90,101,200),
             (90,120,201,300),(120,250,301,400),(250,500,401,500)]
    for lo, hi, ilo, ihi in table:
        if pm25 <= hi:
            return ((ihi-ilo)/(hi-lo))*(pm25-lo)+ilo
    return 500

def calc_si_pm10(pm10):
    table = [(0,50,0,50),(50,100,51,100),(100,250,101,200),
             (250,350,201,300),(350,430,301,400),(430,600,401,500)]
    for lo, hi, ilo, ihi in table:
        if pm10 <= hi:
            return ((ihi-ilo)/(hi-lo))*(pm10-lo)+ilo
    return 500


@app.route('/predict', methods=['POST'])
def predict():
    try:
        data = request.get_json()

        model_name = data.get('model', 'xgboost').lower()
        if model_name not in models:
            return jsonify({"error": f"Model '{model_name}' not loaded. Available: {list(models.keys())}"}), 400

        model = models[model_name]
        feature_cols = FEATURES[model_name]

        now = datetime.now()
        hour        = data.get('hour',        now.hour)
        day_of_week = data.get('day_of_week', now.weekday())
        month       = data.get('month',       now.month)
        current_aqi = data.get('current_aqi', 100)

        all_values = {
            'lat':              data.get('lat',              10.0),
            'lon':              data.get('lon',              76.0),
            'co':               data.get('co',               0.0),
            'no':               data.get('no',               0.0),
            'no2':              data.get('no2',              0.0),
            'nox':              data.get('nox', data.get('no2', 0.0)),
            'o3':               data.get('o3',               0.0),
            'pm10':             data.get('pm10',             0.0),
            'pm25':             data.get('pm25',             0.0),
            'so2':              data.get('so2',              0.0),
            'si_pm25':          calc_si_pm25(data.get('pm25', 0.0)),
            'si_pm10':          calc_si_pm10(data.get('pm10', 0.0)),
            'AQI':              current_aqi,
            'temperature':      data.get('temperature',      25.0),
            'relativehumidity': data.get('relativehumidity', 60.0),
            'wind_speed':       data.get('wind_speed',       5.0),
            'wind_direction':   data.get('wind_direction',   180.0),
            'hour':             hour,
            'day_of_week':      day_of_week,
            'month':            month,
            'aqi_lag_1':        data.get('aqi_lag_1', current_aqi),
            'aqi_lag_2':        data.get('aqi_lag_2', current_aqi),
        }

        row = {col: all_values[col] for col in feature_cols}
        features_df = pd.DataFrame([row], columns=feature_cols)

        # XGBoost: build DMatrix from numpy values then explicitly assign
        # feature_names so the booster's internal validation passes
        if model_name == "xgboost":
            booster = model.get_booster()
            feature_names = feature_cols  # already the correct ordered list
            dmatrix = xgb.DMatrix(
                features_df[feature_names].values.astype(float),
                feature_names=feature_names
            )
            predicted = booster.predict(dmatrix)[0]
        else:
            predicted = model.predict(features_df)[0]

        predicted_aqi = max(0, int(round(float(predicted))))

        return jsonify({
            "predicted_aqi": predicted_aqi,
            "model":         model_name,
            "hour":          hour
        })

    except Exception as e:
        import traceback
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


@app.route('/models', methods=['GET'])
def list_models():
    return jsonify({"available_models": list(models.keys())})

@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "UP", "loaded_models": list(models.keys())})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=False)