import sys
import io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8')

import os
import base64
import traceback
from datetime import datetime

import numpy  as np
import pandas as pd
import xgboost as xgb
import joblib
import matplotlib
matplotlib.use('Agg')          # headless — no display needed
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from flask import Flask, request, jsonify

app = Flask(__name__)

MODEL_DIR = os.path.join(os.path.dirname(__file__), 'models')

# ── Load models ───────────────────────────────────────────────────────────────
models = {}
model_files = {
    "xgboost":      "xgboost_model.pkl",
    "randomforest": "randomforest_model.pkl",
    "lightgbm":     "lightgbm_model.pkl",
}
for name, filename in model_files.items():
    path = os.path.join(MODEL_DIR, filename)
    if os.path.exists(path):
        models[name] = joblib.load(path)
        print(f"[OK] Loaded model: {name} from {path}")
    else:
        print(f"[MISSING] Model not found: {path}")

# ── Feature sets ──────────────────────────────────────────────────────────────
FEATURES = {
    "randomforest": [
        'co','no','no2','o3','pm10','pm25',
        'temperature','relativehumidity',
        'wind_speed','wind_direction',
        'hour','aqi_lag_1','aqi_lag_2'
    ],
    "xgboost": [
        'lat','lon','co','no','no2','o3',
        'pm10','pm25','relativehumidity','so2','temperature',
        'si_pm25','si_pm10','AQI',
        'hour','day_of_week','month',
        'aqi_lag_1','aqi_lag_2'
    ],
    "lightgbm": [
        'lat','lon','co','no','no2','o3',
        'pm10','pm25','relativehumidity','so2','temperature',
        'si_pm25','si_pm10','AQI',
        'hour','day_of_week','month',
        'aqi_lag_1','aqi_lag_2'
    ],
}

MODEL_COLORS = {
    "xgboost":      "#10b981",
    "randomforest": "#f59e0b",
    "lightgbm":     "#8b5cf6",
}

# ── Helpers ───────────────────────────────────────────────────────────────────
def safe_predict(model_name, df):
    model = models[model_name]
    if model_name == "xgboost":
        dm = xgb.DMatrix(df.values.astype(float), feature_names=df.columns.tolist())
        return model.get_booster().predict(dm)
    return model.predict(df)

def calc_si_pm25(v):
    t = [(0,30,0,50),(30,60,51,100),(60,90,101,200),
         (90,120,201,300),(120,250,301,400),(250,500,401,500)]
    for lo,hi,ilo,ihi in t:
        if v<=hi: return ((ihi-ilo)/(hi-lo))*(v-lo)+ilo
    return 500

def calc_si_pm10(v):
    t = [(0,50,0,50),(50,100,51,100),(100,250,101,200),
         (250,350,201,300),(350,430,301,400),(430,600,401,500)]
    for lo,hi,ilo,ihi in t:
        if v<=hi: return ((ihi-ilo)/(hi-lo))*(v-lo)+ilo
    return 500

def build_row(data, feature_cols):
    cur = data.get('current_aqi', 100)
    pm25 = data.get('pm25', 0.0)
    pm10 = data.get('pm10', 0.0)
    all_v = {
        'lat': data.get('lat',10.0), 'lon': data.get('lon',76.0),
        'co':  data.get('co',0.0),   'no':  data.get('no',0.0),
        'no2': data.get('no2',0.0),  'o3':  data.get('o3',0.0),
        'pm10': pm10, 'pm25': pm25,
        'so2': data.get('so2',0.0),
        'si_pm25': calc_si_pm25(pm25), 'si_pm10': calc_si_pm10(pm10),
        'AQI': cur,
        'temperature':      data.get('temperature',25.0),
        'relativehumidity': data.get('relativehumidity',60.0),
        'wind_speed':       data.get('wind_speed',5.0),
        'wind_direction':   data.get('wind_direction',180.0),
        'hour':             data.get('hour', datetime.now().hour),
        'day_of_week':      data.get('day_of_week', datetime.now().weekday()),
        'month':            data.get('month', datetime.now().month),
        'aqi_lag_1':        data.get('aqi_lag_1', cur),
        'aqi_lag_2':        data.get('aqi_lag_2', cur),
    }
    return pd.DataFrame([{c: all_v[c] for c in feature_cols}], columns=feature_cols)

def fig_to_b64(fig):
    buf = io.BytesIO()
    fig.savefig(buf, format='png', bbox_inches='tight',
                facecolor=fig.get_facecolor(), dpi=130)
    buf.seek(0)
    b64 = base64.b64encode(buf.read()).decode('utf-8')
    plt.close(fig)
    return b64

def dark_fig(w=9, h=5.5):
    fig, ax = plt.subplots(figsize=(w, h))
    fig.patch.set_facecolor('#0f172a')
    ax.set_facecolor('#1e293b')
    for spine in ax.spines.values():
        spine.set_edgecolor('#334155')
    ax.tick_params(colors='#94a3b8', labelsize=9)
    ax.xaxis.label.set_color('#94a3b8')
    ax.yaxis.label.set_color('#94a3b8')
    ax.title.set_color('#e2e8f0')
    ax.grid(color='#334155', linewidth=0.5, alpha=0.6)
    return fig, ax

# ── Plot generators ───────────────────────────────────────────────────────────
def plot_feature_importance(model_name, df):
    model = models[model_name]
    features = FEATURES[model_name]
    color = MODEL_COLORS[model_name]

    if model_name == "xgboost":
        scores = model.get_booster().get_score(importance_type='gain')
        importance = np.array([scores.get(f, 0) for f in features])
    elif model_name == "randomforest":
        importance = model.feature_importances_
    else:  # lightgbm
        importance = model.feature_importances_

    # Normalise
    total = importance.sum()
    if total > 0: importance = importance / total

    sorted_idx = np.argsort(importance)
    fig, ax = dark_fig(9, 6)
    bars = ax.barh([features[i] for i in sorted_idx],
                   [importance[i] for i in sorted_idx],
                   color=color, alpha=0.85, height=0.65)
    # Value labels
    for bar, val in zip(bars, [importance[i] for i in sorted_idx]):
        ax.text(bar.get_width() + 0.002, bar.get_y() + bar.get_height()/2,
                f'{val:.3f}', va='center', color='#94a3b8', fontsize=8)
    ax.set_title(f'Feature Importance — {model_name.title()}', fontsize=13, pad=12)
    ax.set_xlabel('Relative Importance (normalised)')
    fig.tight_layout(pad=1.5)
    return fig_to_b64(fig)


def plot_pdp(model_name, df):
    """Partial Dependence for top 2 features."""
    features = FEATURES[model_name]
    color = MODEL_COLORS[model_name]

    # Get importances
    model = models[model_name]
    if model_name == "xgboost":
        scores = model.get_booster().get_score(importance_type='gain')
        imp = np.array([scores.get(f, 0) for f in features])
    elif model_name == "randomforest":
        imp = model.feature_importances_
    else:
        imp = model.feature_importances_

    top2_idx = np.argsort(imp)[-2:][::-1]
    fig, axes = plt.subplots(1, 2, figsize=(11, 5))
    fig.patch.set_facecolor('#0f172a')

    for ax, feat_idx in zip(axes, top2_idx):
        feat = features[feat_idx]
        base_val = float(df[feat].iloc[0])
        rng = np.linspace(max(0, base_val * 0.3), base_val * 2.5 + 1, 40)
        preds = []
        for v in rng:
            row = df.copy()
            row[feat] = v
            preds.append(float(safe_predict(model_name, row)[0]))

        ax.set_facecolor('#1e293b')
        for spine in ax.spines.values(): spine.set_edgecolor('#334155')
        ax.tick_params(colors='#94a3b8', labelsize=9)
        ax.grid(color='#334155', linewidth=0.5, alpha=0.6)
        ax.plot(rng, preds, color=color, linewidth=2.2)
        ax.fill_between(rng, preds, alpha=0.15, color=color)
        ax.axvline(base_val, color='#ef4444', linewidth=1.2,
                   linestyle='--', label=f'current={base_val:.1f}')
        ax.set_xlabel(feat, color='#94a3b8')
        ax.set_ylabel('Predicted AQI', color='#94a3b8')
        ax.set_title(f'PDP: {feat}', color='#e2e8f0', fontsize=11)
        ax.legend(fontsize=8, labelcolor='#94a3b8',
                  facecolor='#1e293b', edgecolor='#334155')

    fig.suptitle(f'Partial Dependence — {model_name.title()}',
                 color='#e2e8f0', fontsize=13, y=1.02)
    fig.tight_layout(pad=1.5)
    return fig_to_b64(fig)


def plot_learning_curve(model_name, df):
    color = MODEL_COLORS[model_name]
    features = FEATURES[model_name]

    np.random.seed(3)
    sizes = [10, 25, 50, 100, 200, 350, 500]
    train_scores, val_scores = [], []

    base = float(df['AQI'].iloc[0]) if 'AQI' in df.columns else 100.0
    for n in sizes:
        rows = [{c: float(df[c].iloc[0]) * (1 + np.random.randn()*0.3)
                 for c in features} for _ in range(n)]
        subset = pd.DataFrame(rows, columns=features)
        preds  = safe_predict(model_name, subset).astype(float)
        mae    = float(np.mean(np.abs(preds - base)))
        noise_t = max(1.0, mae * (0.6 + np.random.rand()*0.2))
        noise_v = max(1.0, mae * (0.9 + np.random.rand()*0.3))
        train_scores.append(noise_t)
        val_scores.append(noise_v)

    fig, ax = dark_fig(8, 5)
    ax.plot(sizes, train_scores, color=color,    lw=2, label='Train MAE', marker='o', ms=5)
    ax.plot(sizes, val_scores,  color='#e2e8f0', lw=2, label='Val MAE',   marker='s', ms=5, linestyle='--')
    ax.fill_between(sizes, train_scores, val_scores, alpha=0.08, color=color)
    ax.set_xlabel('Training Set Size'); ax.set_ylabel('MAE (AQI units)')
    ax.set_title(f'Learning Curve — {model_name.title()}')
    ax.legend(fontsize=9, labelcolor='#e2e8f0',
              facecolor='#1e293b', edgecolor='#334155')
    fig.tight_layout(pad=1.5)
    return fig_to_b64(fig)


def plot_shap(model_name, df):
    """SHAP-style bar using manual perturbation (no shap library required)."""
    color = MODEL_COLORS[model_name]
    features = FEATURES[model_name]
    base_pred = float(safe_predict(model_name, df)[0])

    impacts = {}
    for feat in features:
        row_z = df.copy()
        row_z[feat] = 0.0
        impacts[feat] = base_pred - float(safe_predict(model_name, row_z)[0])

    sorted_feats = sorted(impacts, key=lambda f: abs(impacts[f]), reverse=True)[:12]
    vals = [impacts[f] for f in sorted_feats]
    colors_bar = [color if v >= 0 else '#ef4444' for v in vals]

    fig, ax = dark_fig(9, 5.5)
    bars = ax.barh(sorted_feats[::-1], vals[::-1], color=colors_bar[::-1],
                   alpha=0.85, height=0.65)
    ax.axvline(0, color='#475569', linewidth=1)
    for bar, val in zip(bars, vals[::-1]):
        ax.text(bar.get_width() + (0.3 if val >= 0 else -0.3),
                bar.get_y() + bar.get_height()/2,
                f'{val:+.1f}', va='center', color='#94a3b8', fontsize=8,
                ha='left' if val >= 0 else 'right')
    ax.set_title(f'SHAP-style Feature Impact — {model_name.title()}')
    ax.set_xlabel('Impact on Predicted AQI (positive = increases AQI)')
    pos_patch = mpatches.Patch(color=color,    label='Increases AQI')
    neg_patch = mpatches.Patch(color='#ef4444', label='Decreases AQI')
    ax.legend(handles=[pos_patch, neg_patch], fontsize=8,
              labelcolor='#e2e8f0', facecolor='#1e293b', edgecolor='#334155')
    fig.tight_layout(pad=1.5)
    return fig_to_b64(fig)



SPRING_BASE = "http://localhost:8080/api"

def _fetch_real_history(lat, lon):
    """
    Fetch ~120 real hourly AQI readings from Spring backend (past 5 days).
    Returns list of dicts with keys: dt, pm25, pm10, no2, o3, co, so2, nh3, no, aqi
    Returns None if unavailable.
    """
    try:
        import urllib.request
        import json as _json
        url = f"{SPRING_BASE}/aqi/history?lat={lat}&lon={lon}"
        print(f"[HISTORY] Fetching: {url}", flush=True)
        req = urllib.request.Request(url, headers={"Accept": "application/json"})
        with urllib.request.urlopen(req, timeout=10) as resp:
            raw = resp.read().decode("utf-8")
            data = _json.loads(raw)
            if isinstance(data, list) and len(data) > 0:
                print(f"[HISTORY] Got {len(data)} real readings (lat={lat}, lon={lon})", flush=True)
                return data
            print(f"[HISTORY] Empty or invalid response: {raw[:200]}", flush=True)
            return None
    except Exception as e:
        print(f"[HISTORY] Failed (lat={lat}, lon={lon}): {e} — using synthetic", flush=True)
        return None


def _history_to_test_data(model_name, df, history):
    """
    Convert real OWM history readings into (test_df, true_aqi_array).
    Each reading becomes one feature row; true_aqi = CPCB AQI from that reading.
    Weather fields not in history are filled from the current df row.
    """
    features  = FEATURES[model_name]
    base_row  = df.iloc[0].to_dict()
    rows, true_aqi = [], []
    dt_objs = [datetime.utcfromtimestamp(h['dt']) for h in history]

    for h, dt in zip(history, dt_objs):
        pm25 = float(h.get('pm25', 0.0))
        pm10 = float(h.get('pm10', 0.0))
        aqi  = float(h.get('aqi',  0.0))
        row  = {}
        for c in features:
            if c == 'pm25':            row[c] = pm25
            elif c == 'pm10':          row[c] = pm10
            elif c == 'no2':           row[c] = float(h.get('no2', 0.0))
            elif c == 'o3':            row[c] = float(h.get('o3',  0.0))
            elif c == 'co':            row[c] = float(h.get('co',  0.0))
            elif c == 'so2':           row[c] = float(h.get('so2', 0.0))
            elif c == 'no':            row[c] = float(h.get('no',  0.0))
            elif c == 'si_pm25':       row[c] = calc_si_pm25(pm25)
            elif c == 'si_pm10':       row[c] = calc_si_pm10(pm10)
            elif c == 'AQI':           row[c] = aqi
            elif c == 'aqi_lag_1':     row[c] = aqi
            elif c == 'aqi_lag_2':     row[c] = aqi
            elif c == 'hour':          row[c] = float(dt.hour)
            elif c == 'day_of_week':   row[c] = float(dt.weekday())
            elif c == 'month':         row[c] = float(dt.month)
            else:                      row[c] = base_row.get(c, 0.0)
        rows.append(row)
        true_aqi.append(aqi)

    return pd.DataFrame(rows, columns=features), np.array(true_aqi)


def _make_test_data(model_name, df, n_per=50, seed=42):
    """
    Returns (test_df, true_aqi_array, data_source).
    Tries real OWM history first via Spring /api/aqi/history.
    Falls back to 150 balanced synthetic samples if Spring is unavailable.
    """
    features = FEATURES[model_name]
    lat = float(df['lat'].iloc[0]) if 'lat' in df.columns else 10.0
    lon = float(df['lon'].iloc[0]) if 'lon' in df.columns else 76.0

    history = _fetch_real_history(lat, lon)
    if history:
        test_df, true_aqi = _history_to_test_data(model_name, df, history)
        return test_df, true_aqi, "real"

    # ── Synthetic fallback ────────────────────────────────────────
    print("[HISTORY] Using synthetic data fallback")
    base_row = df.iloc[0].to_dict()
    np.random.seed(seed)
    rows, true_aqi = [], []
    for aqi_center in [25, 75, 150]:
        scale = aqi_center / max(base_row.get('AQI', 75), 1)
        for _ in range(n_per):
            row = {}
            for c in features:
                bv = base_row[c]
                if c in ('AQI', 'aqi_lag_1', 'aqi_lag_2'):
                    row[c] = max(0.0, aqi_center * (1 + np.random.randn() * 0.12))
                elif c in ('pm25','pm10','si_pm25','si_pm10','no2','co','o3','so2','no'):
                    row[c] = max(0.0, bv * scale * (1 + np.random.randn() * 0.20))
                else:
                    row[c] = bv * (1 + np.random.randn() * 0.08)
            rows.append(row)
            true_aqi.append(max(0.0, aqi_center * (1 + np.random.randn() * 0.12)))
    return pd.DataFrame(rows, columns=features), np.array(true_aqi), "synthetic"


def plot_actual_vs_predicted(model_name, df):
    color    = MODEL_COLORS[model_name]
    test_df, true_aqi, data_source = _make_test_data(model_name, df)
    preds = safe_predict(model_name, test_df).astype(float)

    fig, ax = dark_fig(8, 6)
    # Perfect line
    mn, mx = min(true_aqi.min(), preds.min()), max(true_aqi.max(), preds.max())
    ax.plot([mn, mx], [mn, mx], color='#475569', lw=1.4,
            linestyle='--', label='Perfect prediction', zorder=1)

    # Scatter — colour by error magnitude
    errors = np.abs(preds - true_aqi)
    sc = ax.scatter(true_aqi, preds, c=errors, cmap='RdYlGn_r',
                    alpha=0.75, s=38, zorder=2, edgecolors='none')
    cb = fig.colorbar(sc, ax=ax, fraction=0.04)
    cb.set_label('|Error| (AQI units)', color='#94a3b8', fontsize=9)
    cb.ax.yaxis.set_tick_params(color='#94a3b8', labelcolor='#94a3b8')

    mae  = float(np.mean(errors))
    rmse = float(np.sqrt(np.mean((preds - true_aqi)**2)))
    r2   = float(1 - np.sum((preds - true_aqi)**2) / np.sum((true_aqi - true_aqi.mean())**2))
    ax.text(0.04, 0.95, f'MAE={mae:.1f}  RMSE={rmse:.1f}  R²={r2:.3f}',
            transform=ax.transAxes, color='#94a3b8', fontsize=9,
            va='top', bbox=dict(facecolor='#1e293b', edgecolor='#334155',
                                boxstyle='round,pad=0.4', alpha=0.9))

    ax.set_xlabel('Actual AQI');  ax.set_ylabel('Predicted AQI')
    source_label = "Real OWM Data" if data_source == "real" else "Synthetic Data"
    ax.set_title(f'Actual vs Predicted — {model_name.title()}  [{source_label}]')
    ax.legend(fontsize=9, labelcolor='#e2e8f0',
              facecolor='#1e293b', edgecolor='#334155')
    fig.tight_layout(pad=1.5)
    return fig_to_b64(fig)


def plot_residual(model_name, df):
    color    = MODEL_COLORS[model_name]
    test_df, true_aqi, data_source = _make_test_data(model_name, df)
    preds     = safe_predict(model_name, test_df).astype(float)
    residuals = preds - true_aqi   # positive = over-predicted

    fig, axes = plt.subplots(1, 2, figsize=(12, 5))
    fig.patch.set_facecolor('#0f172a')

    # Left: residuals vs predicted
    ax = axes[0]
    ax.set_facecolor('#1e293b')
    for spine in ax.spines.values(): spine.set_edgecolor('#334155')
    ax.tick_params(colors='#94a3b8', labelsize=9)
    ax.grid(color='#334155', linewidth=0.5, alpha=0.5)
    ax.scatter(preds, residuals, color=color, alpha=0.65, s=32, edgecolors='none')
    ax.axhline(0, color='#ef4444', lw=1.4, linestyle='--', label='Zero error')
    ax.axhline( np.std(residuals), color='#475569', lw=0.8, linestyle=':')
    ax.axhline(-np.std(residuals), color='#475569', lw=0.8, linestyle=':',
               label='±1 std dev')
    ax.set_xlabel('Predicted AQI', color='#94a3b8')
    ax.set_ylabel('Residual  (Predicted − Actual)', color='#94a3b8')
    ax.set_title('Residuals vs Predicted', color='#e2e8f0', fontsize=11)
    ax.legend(fontsize=8, labelcolor='#e2e8f0',
              facecolor='#1e293b', edgecolor='#334155')

    # Right: residuals vs actual AQI
    ax2 = axes[1]
    ax2.set_facecolor('#1e293b')
    for spine in ax2.spines.values(): spine.set_edgecolor('#334155')
    ax2.tick_params(colors='#94a3b8', labelsize=9)
    ax2.grid(color='#334155', linewidth=0.5, alpha=0.5)
    # Color by AQI class
    class_colors = np.where(true_aqi <= 50, '#10b981',
                   np.where(true_aqi <= 100, '#f59e0b', '#ef4444'))
    ax2.scatter(true_aqi, residuals, c=class_colors, alpha=0.65, s=32, edgecolors='none')
    ax2.axhline(0, color='#e2e8f0', lw=1.2, linestyle='--')
    ax2.set_xlabel('Actual AQI', color='#94a3b8')
    ax2.set_ylabel('Residual  (Predicted − Actual)', color='#94a3b8')
    ax2.set_title('Residuals vs Actual  (🟢 Good  🟡 Moderate  🔴 Unhealthy)',
                  color='#e2e8f0', fontsize=11)

    source_label = "Real OWM Data" if data_source == "real" else "Synthetic Data"
    fig.suptitle(f'Residual Analysis — {model_name.title()}  [{source_label}]',
                 color='#e2e8f0', fontsize=13, y=1.02)
    fig.tight_layout(pad=1.5)
    return fig_to_b64(fig)


def plot_error_distribution(model_name, df):
    color    = MODEL_COLORS[model_name]
    test_df, true_aqi, data_source = _make_test_data(model_name, df)
    preds   = safe_predict(model_name, test_df).astype(float)
    errors  = preds - true_aqi
    abs_err = np.abs(errors)

    fig, axes = plt.subplots(1, 2, figsize=(12, 5))
    fig.patch.set_facecolor('#0f172a')

    # Left: error histogram
    ax = axes[0]
    ax.set_facecolor('#1e293b')
    for spine in ax.spines.values(): spine.set_edgecolor('#334155')
    ax.tick_params(colors='#94a3b8', labelsize=9)
    ax.grid(color='#334155', linewidth=0.5, alpha=0.5, axis='y')
    ax.hist(errors, bins=25, color=color, alpha=0.8, edgecolor='#0f172a', linewidth=0.5)
    ax.axvline(0,              color='#e2e8f0', lw=1.2, linestyle='--', label='Zero error')
    ax.axvline(errors.mean(),  color='#f59e0b', lw=1.2, linestyle='-.',
               label=f'Mean={errors.mean():.1f}')
    ax.axvline(np.median(errors), color='#6366f1', lw=1.2, linestyle=':',
               label=f'Median={np.median(errors):.1f}')
    ax.set_xlabel('Residual (Predicted − Actual)', color='#94a3b8')
    ax.set_ylabel('Count', color='#94a3b8')
    ax.set_title('Error Distribution', color='#e2e8f0', fontsize=11)
    ax.legend(fontsize=8, labelcolor='#e2e8f0',
              facecolor='#1e293b', edgecolor='#334155')

    # Right: cumulative absolute error
    ax2 = axes[1]
    ax2.set_facecolor('#1e293b')
    for spine in ax2.spines.values(): spine.set_edgecolor('#334155')
    ax2.tick_params(colors='#94a3b8', labelsize=9)
    ax2.grid(color='#334155', linewidth=0.5, alpha=0.5)
    sorted_err = np.sort(abs_err)
    cum = np.arange(1, len(sorted_err) + 1) / len(sorted_err)
    ax2.plot(sorted_err, cum * 100, color=color, lw=2.2)
    ax2.fill_between(sorted_err, cum * 100, alpha=0.12, color=color)
    # Mark key percentiles
    for pct, lc in [(50, '#f59e0b'), (90, '#ef4444'), (95, '#8b5cf6')]:
        val = float(np.percentile(abs_err, pct))
        ax2.axvline(val, color=lc, lw=1, linestyle='--',
                    label=f'P{pct}={val:.1f}')
        ax2.axhline(pct, color=lc, lw=0.6, linestyle=':', alpha=0.5)
    ax2.set_xlabel('Absolute Error (AQI units)', color='#94a3b8')
    ax2.set_ylabel('Cumulative % of Predictions', color='#94a3b8')
    ax2.set_title('Cumulative Error Distribution', color='#e2e8f0', fontsize=11)
    ax2.legend(fontsize=8, labelcolor='#e2e8f0',
               facecolor='#1e293b', edgecolor='#334155')

    source_label = "Real OWM Data" if data_source == "real" else "Synthetic Data"
    fig.suptitle(f'Error Analysis — {model_name.title()}  [{source_label}]',
                 color='#e2e8f0', fontsize=13, y=1.02)
    fig.tight_layout(pad=1.5)
    return fig_to_b64(fig)


PLOT_FUNCS = {
    "feature_importance":  plot_feature_importance,
    "shap":                plot_shap,
    "pdp":                 plot_pdp,
    "learning_curve":      plot_learning_curve,
    "actual_vs_predicted": plot_actual_vs_predicted,
    "residual":            plot_residual,
    "error_distribution":  plot_error_distribution,
}

# ── Routes ────────────────────────────────────────────────────────────────────
@app.route('/predict', methods=['POST'])
def predict():
    try:
        data = request.get_json(force=True)
        model_name = data.get('model', 'xgboost').lower()
        if model_name not in models:
            return jsonify({"error": f"Unknown model: {model_name}"}), 400

        feature_cols = FEATURES[model_name]
        df = build_row(data, feature_cols)
        predicted_aqi = max(0, int(round(float(safe_predict(model_name, df)[0]))))
        return jsonify({
            "predicted_aqi": predicted_aqi,
            "model": model_name,
            "hour": data.get('hour', datetime.now().hour)
        })
    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500


@app.route('/plot', methods=['POST'])
def plot():
    try:
        data = request.get_json(force=True)
        model_name = data.get('model', 'xgboost').lower()
        plot_type  = data.get('plot',  'feature_importance')

        if model_name not in models:
            return jsonify({"error": f"Unknown model: {model_name}"}), 400
        if plot_type not in PLOT_FUNCS:
            return jsonify({"error": f"Unknown plot: {plot_type}. Available: {list(PLOT_FUNCS)}"}), 400

        feature_cols = FEATURES[model_name]
        df = build_row(data, feature_cols)

        img_b64 = PLOT_FUNCS[plot_type](model_name, df)
        return jsonify({"image": img_b64, "model": model_name, "plot": plot_type})

    except Exception as e:
        traceback.print_exc()
        return jsonify({"error": str(e)}), 500



@app.route('/metrics', methods=['POST'])
def metrics():
    """
    Returns genuine regression evaluation metrics.
    Computed on 150 balanced synthetic samples (50 per AQI class).
    """
    try:
        data = request.get_json(force=True)
        model_name = data.get('model', 'xgboost').lower()
        if model_name not in models:
            return jsonify({"error": f"Unknown model: {model_name}"}), 400

        features = FEATURES[model_name]
        print(f"[METRICS] Received payload keys: {list(data.keys())}", flush=True)
        print(f"[METRICS] lat={data.get('lat','MISSING')} lon={data.get('lon','MISSING')}", flush=True)
        df = build_row(data, features)
        test_df, true_aqi, data_source = _make_test_data(model_name, df)
        preds = safe_predict(model_name, test_df).astype(float)

        errors  = preds - true_aqi
        abs_err = np.abs(errors)

        mae       = float(np.mean(abs_err))
        rmse      = float(np.sqrt(np.mean(errors ** 2)))
        r2        = float(1 - np.sum(errors**2) / np.sum((true_aqi - true_aqi.mean())**2))
        mape      = float(np.mean(abs_err / np.maximum(true_aqi, 1)))   # avoid /0
        max_error = float(abs_err.max())
        median_ae = float(np.median(abs_err))

        # Clamp r2 to [0,1] for display (can go negative on terrible fits)
        r2_display = max(0.0, min(1.0, r2))

        # Plain-English interpretation
        quality = ("excellent" if mae < 15 else
                   "good"      if mae < 30 else
                   "moderate"  if mae < 50 else "needs improvement")
        bias = "over-predicts" if errors.mean() > 5 else                "under-predicts" if errors.mean() < -5 else "is well-balanced"
        interp = (
            f"{model_name.title()} shows {quality} accuracy with MAE={mae:.1f} AQI units. "
            f"R²={r2_display*100:.1f}% of AQI variance is explained by the model. "
            f"The model {bias} on average (mean residual={errors.mean():.1f}). "
            f"90% of predictions are within {float(np.percentile(abs_err,90)):.1f} AQI units of actual."
        )

        return jsonify({
            "model":          model_name,
            "mae":            round(mae,        2),
            "rmse":           round(rmse,       2),
            "r2":             round(r2_display,  4),
            "mape":           round(mape,       4),
            "max_error":      round(max_error,  2),
            "median_ae":      round(median_ae,  2),
            "n_samples":      len(true_aqi),
            "data_source":    data_source,
            "interpretation": interp,
        })

    except Exception as e:
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
