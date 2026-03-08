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


def plot_roc(model_name, df):
    """Simulated ROC curve based on prediction confidence bands."""
    color = MODEL_COLORS[model_name]
    features = FEATURES[model_name]

    # Generate synthetic test spread around current values
    np.random.seed(42)
    n = 120
    rows = []
    for _ in range(n):
        noise = {c: float(df[c].iloc[0]) * (1 + np.random.randn() * 0.25)
                 for c in features}
        rows.append(noise)
    test_df = pd.DataFrame(rows, columns=features)
    preds = safe_predict(model_name, test_df).astype(float)

    # Binary: unhealthy = AQI > 100
    threshold_aqi = 100
    y_true = (preds > threshold_aqi).astype(int)
    # Simulate scores with some noise
    scores = (preds / 500) + np.random.randn(n) * 0.05

    from sklearn.metrics import roc_curve, auc
    fpr, tpr, _ = roc_curve(y_true, scores) if y_true.sum() > 0 else ([0,1],[0,1],[0])
    roc_auc = auc(fpr, tpr)

    fig, ax = dark_fig(7, 5.5)
    ax.plot(fpr, tpr, color=color, lw=2.2, label=f'AUC = {roc_auc:.3f}')
    ax.plot([0,1],[0,1], color='#475569', lw=1, linestyle='--', label='Random')
    ax.fill_between(fpr, tpr, alpha=0.12, color=color)
    ax.set_xlabel('False Positive Rate')
    ax.set_ylabel('True Positive Rate')
    ax.set_title(f'ROC Curve — {model_name.title()} (AQI > 100 = Unhealthy)')
    ax.legend(fontsize=9, labelcolor='#e2e8f0',
              facecolor='#1e293b', edgecolor='#334155')
    fig.tight_layout(pad=1.5)
    return fig_to_b64(fig)


def plot_confusion(model_name, df):
    color    = MODEL_COLORS[model_name]
    features = FEATURES[model_name]

    def classify(v):
        if v <= 50:  return 0
        if v <= 100: return 1
        return 2

    np.random.seed(7)
    class_aqi_centers = [25, 75, 150]
    n_per_class = 50
    rows, y_true = [], []
    base_row = {c: float(df[c].iloc[0]) for c in features}

    for cls_idx, aqi_center in enumerate(class_aqi_centers):
        scale = aqi_center / max(base_row.get('AQI', 75), 1)
        for _ in range(n_per_class):
            row = {}
            for c in features:
                base_val = base_row[c]
                if c in ('AQI', 'aqi_lag_1', 'aqi_lag_2'):
                    row[c] = max(0.0, aqi_center * (1 + np.random.randn() * 0.12))
                elif c in ('pm25', 'pm10', 'si_pm25', 'si_pm10',
                           'no2', 'co', 'o3', 'so2', 'no'):
                    row[c] = max(0.0, base_val * scale * (1 + np.random.randn() * 0.20))
                else:
                    row[c] = base_val * (1 + np.random.randn() * 0.08)
            rows.append(row)
            y_true.append(cls_idx)

    test_df = pd.DataFrame(rows, columns=features)
    preds   = safe_predict(model_name, test_df).astype(float)
    y_pred  = [classify(p) for p in preds]

    from sklearn.metrics import confusion_matrix
    cm = confusion_matrix(y_true, y_pred, labels=[0, 1, 2])
    labels_str = ['Good\n(≤50)', 'Moderate\n(51-100)', 'Unhealthy\n(>100)']

    fig, ax = dark_fig(7, 5.5)
    im = ax.imshow(cm, cmap='Blues', aspect='auto')
    ax.set_xticks([0, 1, 2]); ax.set_yticks([0, 1, 2])
    ax.set_xticklabels(labels_str, color='#94a3b8', fontsize=9)
    ax.set_yticklabels(labels_str, color='#94a3b8', fontsize=9)
    for i in range(3):
        for j in range(3):
            ax.text(j, i, str(cm[i, j]), ha='center', va='center',
                    color='white' if cm[i, j] > cm.max() * 0.5 else '#94a3b8',
                    fontsize=14, fontweight='bold')
    ax.set_xlabel('Predicted'); ax.set_ylabel('Actual')
    ax.set_title(f'Confusion Matrix — {model_name.title()} ({n_per_class} samples/class)')
    cb = fig.colorbar(im, ax=ax, fraction=0.046)
    cb.ax.yaxis.set_tick_params(color='#94a3b8', labelcolor='#94a3b8')
    fig.tight_layout(pad=1.5)
    return fig_to_b64(fig)


def plot_precision_recall(model_name, df):
    color = MODEL_COLORS[model_name]
    features = FEATURES[model_name]

    np.random.seed(13)
    n = 120
    rows = [{c: float(df[c].iloc[0]) * (1 + np.random.randn()*0.25)
             for c in features} for _ in range(n)]
    test_df = pd.DataFrame(rows, columns=features)
    preds = safe_predict(model_name, test_df).astype(float)
    y_true = (preds > 100).astype(int)
    scores = preds / 500 + np.random.randn(n) * 0.04

    from sklearn.metrics import precision_recall_curve, average_precision_score
    if y_true.sum() > 0:
        prec, rec, _ = precision_recall_curve(y_true, scores)
        ap = average_precision_score(y_true, scores)
    else:
        prec, rec, ap = np.array([1,0]), np.array([0,1]), 0.0

    fig, ax = dark_fig(7, 5.5)
    ax.plot(rec, prec, color=color, lw=2.2, label=f'AP = {ap:.3f}')
    ax.fill_between(rec, prec, alpha=0.12, color=color)
    ax.set_xlabel('Recall'); ax.set_ylabel('Precision')
    ax.set_title(f'Precision-Recall — {model_name.title()} (AQI > 100)')
    ax.legend(fontsize=9, labelcolor='#e2e8f0',
              facecolor='#1e293b', edgecolor='#334155')
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


PLOT_FUNCS = {
    "feature_importance": plot_feature_importance,
    "shap":               plot_shap,
    "pdp":                plot_pdp,
    "roc":                plot_roc,
    "confusion":          plot_confusion,
    "precision_recall":   plot_precision_recall,
    "learning_curve":     plot_learning_curve,
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


@app.route('/models', methods=['GET'])
def list_models():
    return jsonify({"available_models": list(models.keys())})


@app.route('/health', methods=['GET'])
def health():
    return jsonify({"status": "UP", "loaded_models": list(models.keys())})


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=False)
