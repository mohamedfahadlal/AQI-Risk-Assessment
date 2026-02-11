import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from sklearn.model_selection import train_test_split
from sklearn.metrics import mean_squared_error, r2_score, mean_absolute_error
from xgboost import XGBRegressor
from lightgbm import LGBMRegressor

# 1. Load the data
df = pd.read_csv(r"D:\Github\Java AQI\dataset new\kerala_aqi_complete.csv")

# 2. Select features and target
# We exclude non-numeric/identifier columns like city and datetime
features = [
    'lat', 'lon', 'co', 'no', 'no2', 'o3', 'pm10', 'pm25',
    'relativehumidity', 'so2', 'temperature', 'si_pm25', 'si_pm10',
    'AQI', 'hour', 'day_of_week', 'month', 'aqi_lag_1', 'aqi_lag_2'
]
target = 'target_next_hour_aqi'

# Remove rows with missing values in the selected features
data = df[features + [target]].dropna()

X = data[features]
y = data[target]

# 3. Split the data (80% Train, 20% Test)
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

# 4. Initialize and Train XGBoost
xgb_model = XGBRegressor(n_estimators=100, learning_rate=0.1, max_depth=6, random_state=42)
xgb_model.fit(X_train, y_train)
y_pred_xgb = xgb_model.predict(X_test)

# 5. Initialize and Train LightGBM
lgb_model = LGBMRegressor(n_estimators=100, learning_rate=0.1, max_depth=6, random_state=42)
lgb_model.fit(X_train, y_train)
y_pred_lgb = lgb_model.predict(X_test)

# 6. Evaluate Models
def print_metrics(name, y_true, y_pred):
    mae = mean_absolute_error(y_true, y_pred)
    rmse = np.sqrt(mean_squared_error(y_true, y_pred))
    r2 = r2_score(y_true, y_pred)
    print(f"{name} -> RMSE: {rmse:.4f}, R2 Score: {r2:.4f}")
    print(f"{name} -> MAE: {mae:.4f}")

print_metrics("XGBoost", y_test, y_pred_xgb)
print_metrics("LightGBM", y_test, y_pred_lgb)
###############################################################

#7. Visualization of Actual vs Predicted AQI


# ---- Cap extreme AQI values for better visualization ----
cap_value = 500
y_test_cap = np.clip(y_test.values, 0, cap_value)
y_pred_xgb_cap = np.clip(y_pred_xgb, 0, cap_value)
y_pred_lgb_cap = np.clip(y_pred_lgb, 0, cap_value)

# ---- Moving average smoothing function ----
def moving_avg(series, window=50):
    return pd.Series(series).rolling(window=window).mean()

# ---- Create smoothed series ----
y_test_smooth = moving_avg(y_test_cap)
y_xgb_smooth = moving_avg(y_pred_xgb_cap)
y_lgb_smooth = moving_avg(y_pred_lgb_cap)


#Small step visualization (every point)
plt.figure(figsize=(15,6))

plt.plot(y_test_smooth, label="Actual AQI", linewidth=2)
plt.plot(y_xgb_smooth, label="XGBoost Prediction", linestyle="--")
plt.plot(y_lgb_smooth, label="LightGBM Prediction", linestyle=":")

plt.title("Smoothed Actual vs Predicted AQI (Capped at 500)", fontsize=14)
plt.xlabel("Time Steps (Test Set)")
plt.ylabel("AQI")
plt.ylim(0, 500)
plt.legend()
plt.grid(alpha=0.3)
plt.tight_layout()
plt.show()
plt.figure(figsize=(15,6))

plt.plot(y_test_smooth, label="Actual AQI", linewidth=2)
plt.plot(y_xgb_smooth, label="XGBoost Prediction", linestyle="--")
plt.plot(y_lgb_smooth, label="LightGBM Prediction", linestyle=":")

plt.title("Smoothed Actual vs Predicted AQI (Capped at 500)", fontsize=14)
plt.xlabel("Time Steps (Test Set)")
plt.ylabel("AQI")
plt.ylim(0, 500)
plt.legend()
plt.grid(alpha=0.3)
plt.tight_layout()
plt.show()

# Downsampled visualization


step = 10  # show every 10th point

plt.figure(figsize=(15,6))

plt.plot(y_test_smooth[::step], label="Actual AQI", linewidth=2)
plt.plot(y_xgb_smooth[::step], label="XGBoost Prediction", linestyle="--")
plt.plot(y_lgb_smooth[::step], label="LightGBM Prediction", linestyle=":")

plt.title("Smoothed Actual vs Predicted AQI (Downsampled)", fontsize=14)
plt.xlabel("Time Steps (Test Set)")
plt.ylabel("AQI")
plt.ylim(0, 150)
plt.legend()
plt.grid(alpha=0.3)
plt.tight_layout()
plt.show()
error_xgb = y_test_cap - y_pred_xgb_cap
error_lgb = y_test_cap - y_pred_lgb_cap

plt.figure(figsize=(15,5))
plt.plot(pd.Series(error_xgb).rolling(50).mean(), label="XGBoost Error")
plt.plot(pd.Series(error_lgb).rolling(50).mean(), label="LightGBM Error")

plt.axhline(0, color='black', linewidth=1)
plt.title("Prediction Error Over Time (Smoothed)")
plt.xlabel("Time Steps")
plt.ylabel("Error (Actual - Predicted AQI)")
plt.legend()
plt.grid(alpha=0.3)
plt.tight_layout()
plt.show()
plt.figure(figsize=(6,6))
plt.scatter(y_test_cap, y_pred_xgb_cap, alpha=0.3, label="XGBoost")
plt.scatter(y_test_cap, y_pred_lgb_cap, alpha=0.3, label="LightGBM")

plt.plot([0,150],[0,150], color='red', linestyle='--')
plt.xlabel("Actual AQI")
plt.ylabel("Predicted AQI")
plt.title("Actual vs Predicted AQI Scatter")
plt.legend()
plt.grid(alpha=0.3)
plt.show()


# 7. Visualization of Feature Importance for XGBoost
plt.figure(figsize=(10, 6))
importances = xgb_model.feature_importances_
indices = np.argsort(importances)[::-1]
plt.title("XGBoost Feature Importance")
plt.bar(range(X.shape[1]), importances[indices], align="center", color='teal')
plt.xticks(range(X.shape[1]), [features[i] for i in indices], rotation=90)
plt.tight_layout()
plt.show()
# 8. Visualization of Feature Importance for LightGBM
plt.figure(figsize=(10, 6))
importances = lgb_model.feature_importances_
indices = np.argsort(importances)[::-1]
plt.title("LightGBM Feature Importance")
plt.bar(range(X.shape[1]), importances[indices], align="center", color='orange')
plt.xticks(range(X.shape[1]), [features[i] for i in indices], rotation=90)
plt.tight_layout()
plt.show()

###############################################################
# 9. Actual vs Predicted Plot for XGBoost
plt.figure(figsize=(8, 6))
plt.scatter(y_test, y_pred_xgb, alpha=0.4, color='blue')
plt.plot([y_test.min(), y_test.max()], [y_test.min(), y_test.max()], 'r--', lw=2)
plt.xlabel('Actual AQI (Next Hour)')
plt.ylabel('Predicted AQI (Next Hour)')
plt.title('XGBoost: Actual vs Predicted')
plt.grid(True)
plt.show()
# 10. Actual vs Predicted Plot for LightGBM
plt.figure(figsize=(8, 6))
plt.scatter(y_test, y_pred_lgb, alpha=0.4, color='green')
plt.plot([y_test.min(), y_test.max()], [y_test.min(), y_test.max()], 'r--', lw=2)
plt.xlabel('Actual AQI (Next Hour)')
plt.ylabel('Predicted AQI (Next Hour)')
plt.title('LightGBM: Actual vs Predicted')
plt.grid(True)
plt.show()


###############################################################
plt.figure(figsize=(14,5))

plt.plot(y_test_smooth, label="Actual AQI", linewidth=2)
plt.plot(y_xgb_smooth, label="XGBoost Prediction", linestyle="--")

plt.title("Actual vs Predicted AQI — XGBoost", fontsize=13)
plt.xlabel("Time Steps (Test Set)")
plt.ylabel("AQI")
plt.ylim(0, 150)
plt.legend()
plt.grid(alpha=0.3)
plt.tight_layout()
plt.show()

plt.figure(figsize=(14,5))

plt.plot(y_test_smooth, label="Actual AQI", linewidth=2)
plt.plot(y_lgb_smooth, label="LightGBM Prediction", linestyle=":")

plt.title("Actual vs Predicted AQI — LightGBM", fontsize=13)
plt.xlabel("Time Steps (Test Set)")
plt.ylabel("AQI")
plt.ylim(0, 150)
plt.legend()
plt.grid(alpha=0.3)
plt.tight_layout()
plt.show()
###############################################################

# 13. Prediction Error Distribution for XGBoost
plt.figure(figsize=(8, 6))
plt.hist(residuals_xgb, bins=50, color='cyan', alpha=0.7)
plt.title('XGBoost: Prediction Error Distribution')
plt.xlabel('Prediction Error (Residuals)')
plt.ylabel('Frequency')
plt.grid(True)
plt.show()
# 14. Prediction Error Distribution for LightGBM
plt.figure(figsize=(8, 6))
plt.hist(residuals_lgb, bins=50, color='magenta', alpha=0.7)
plt.title('LightGBM: Prediction Error Distribution')
plt.xlabel('Prediction Error (Residuals)')
plt.ylabel('Frequency')
plt.grid(True)
plt.show()


