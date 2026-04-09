"""
Train an article-level model from article_features.csv.

Examples:
    python article_train_model.py
    python article_train_model.py --features article_features.csv --model article_lgbm_model.pkl
"""

from __future__ import annotations

import argparse
import json
import pickle
import warnings

import numpy as np
import pandas as pd
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix, roc_auc_score
from sklearn.model_selection import TimeSeriesSplit
from sklearn.preprocessing import LabelEncoder

warnings.filterwarnings("ignore")

try:
    import lightgbm as lgb
except ImportError as exc:
    raise SystemExit("lightgbm is required. Install it with: pip install lightgbm") from exc


DEFAULT_FEATURE_COLS = [
    "sentiment_score",
    "clickbait_prob",
    "type_prob",
    "is_fact",
    "is_predict",
    "is_reasoning",
    "title_length",
    "summary_length",
    "title_word_count",
    "summary_word_count",
    "has_summary",
    "pub_hour",
    "pub_weekday",
    "is_weekend",
    "is_before_open",
    "is_after_close",
    "anchor_delay_days",
    "recent_article_count_3d",
    "recent_sentiment_mean_3d",
    "recent_sentiment_sum_3d",
    "recent_positive_ratio_3d",
    "recent_negative_ratio_3d",
    "recent_sentiment_intensity_3d",
    "recent_clickbait_mean_3d",
    "recent_type_prob_mean_3d",
    "recent_fact_ratio_3d",
    "recent_predict_ratio_3d",
    "recent_reasoning_ratio_3d",
    "hours_since_prev_article",
    "close",
    "ma_5",
    "ma_20",
    "ma_60",
    "close_to_ma5",
    "close_to_ma20",
    "ma_cross",
    "rsi_14",
    "rsi_zone",
    "macd",
    "macd_sig",
    "macd_hist",
    "macd_cross",
    "bb_pctb",
    "volatility_5d",
    "volatility_20d",
    "volume_ratio",
    "return_before_1d",
    "return_before_3d",
    "return_before_5d",
]


def normalize_stock_code(value: object) -> str | None:
    if pd.isna(value):
        return None
    text = str(value).strip()
    if not text:
        return None
    if text.endswith(".0"):
        text = text[:-2]
    digits = "".join(ch for ch in text if ch.isdigit())
    if digits:
        return digits.zfill(6)
    return text


def load_features(path: str, target_col: str) -> pd.DataFrame:
    df = pd.read_csv(path, dtype={"stock_code": str})
    df["stock_code"] = df["stock_code"].map(normalize_stock_code)
    df["pub_date"] = pd.to_datetime(df["pub_date"], errors="coerce")
    df = df.dropna(subset=["pub_date"]).sort_values("pub_date").reset_index(drop=True)
    df = df.dropna(subset=[target_col]).reset_index(drop=True)
    print(f"  rows: {len(df)}")
    print(f"  period: {df['pub_date'].min()} -> {df['pub_date'].max()}")
    print(f"  target distribution:\n{df[target_col].value_counts().to_string()}")
    print(f"  stock rows:\n{df['stock_code'].value_counts().to_string()}")
    return df


def preprocess(
    df: pd.DataFrame,
    feature_cols: list[str],
    target_col: str,
) -> tuple[np.ndarray, np.ndarray, list[str], LabelEncoder, pd.DataFrame]:
    df = df.copy()
    encoder = LabelEncoder()
    df["stock_code_enc"] = encoder.fit_transform(df["stock_code"])
    all_feature_cols = feature_cols + ["stock_code_enc"]

    for column in all_feature_cols:
        df[column] = pd.to_numeric(df[column], errors="coerce")
    df[all_feature_cols] = df[all_feature_cols].fillna(0.0)

    X = df[all_feature_cols].to_numpy(dtype=float)
    y = pd.to_numeric(df[target_col], errors="coerce").astype(int).to_numpy()
    return X, y, all_feature_cols, encoder, df


def calc_pos_weight(y: np.ndarray) -> float:
    neg = int((y == 0).sum())
    pos = int((y == 1).sum())
    if pos == 0:
        return 1.0
    ratio = neg / pos
    print(f"  class ratio (0:1) = {neg}:{pos} -> scale_pos_weight={ratio:.3f}")
    return ratio


def train_lgbm(
    X: np.ndarray,
    y: np.ndarray,
    feature_names: list[str],
    n_splits: int,
) -> tuple[object, object | None, list[float], list[float]]:
    params = {
        "objective": "binary",
        "metric": "binary_logloss",
        "boosting_type": "gbdt",
        "num_leaves": 31,
        "learning_rate": 0.05,
        "feature_fraction": 0.8,
        "bagging_fraction": 0.8,
        "bagging_freq": 5,
        "scale_pos_weight": calc_pos_weight(y),
        "min_child_samples": 5,
        "verbose": -1,
        "random_state": 42,
    }

    splitter = TimeSeriesSplit(n_splits=n_splits)
    cv_scores: list[float] = []
    cv_aucs: list[float] = []
    best_model = None
    best_auc = -1.0

    print(f"\n  TimeSeriesSplit(n_splits={n_splits})")
    for fold, (train_idx, val_idx) in enumerate(splitter.split(X), start=1):
        X_train, X_val = X[train_idx], X[val_idx]
        y_train, y_val = y[train_idx], y[val_idx]

        if len(np.unique(y_train)) < 2 or len(np.unique(y_val)) < 2:
            print(f"  fold {fold}: skipped because a class is missing")
            continue

        train_set = lgb.Dataset(X_train, label=y_train, feature_name=feature_names)
        val_set = lgb.Dataset(X_val, label=y_val, feature_name=feature_names)
        model = lgb.train(
            params,
            train_set,
            num_boost_round=500,
            valid_sets=[val_set],
            callbacks=[lgb.early_stopping(50, verbose=False), lgb.log_evaluation(-1)],
        )

        prob = model.predict(X_val)
        pred = (prob > 0.5).astype(int)
        acc = accuracy_score(y_val, pred)
        try:
            auc = roc_auc_score(y_val, prob)
        except ValueError:
            auc = 0.5

        cv_scores.append(float(acc))
        cv_aucs.append(float(auc))
        print(f"  fold {fold}: acc={acc:.4f}, auc={auc:.4f}, train={len(train_idx)}, val={len(val_idx)}")

        if auc > best_auc:
            best_auc = auc
            best_model = model

    if not cv_scores:
        raise RuntimeError("Cross-validation could not run. Check data size and label balance.")

    print(f"\n  cv acc mean={np.mean(cv_scores):.4f} std={np.std(cv_scores):.4f}")
    print(f"  cv auc mean={np.mean(cv_aucs):.4f} std={np.std(cv_aucs):.4f}")

    full_set = lgb.Dataset(X, label=y, feature_name=feature_names)
    final_model = lgb.train(
        params,
        full_set,
        num_boost_round=best_model.best_iteration if best_model is not None else 100,
        callbacks=[lgb.log_evaluation(-1)],
    )

    return final_model, best_model, cv_scores, cv_aucs


def print_feature_importance(model: object, feature_names: list[str]) -> pd.DataFrame:
    importance = model.feature_importance(importance_type="gain")
    fi = pd.DataFrame({"feature": feature_names, "importance": importance})
    fi = fi.sort_values("importance", ascending=False).reset_index(drop=True)
    print("\n  feature importance:")
    for row in fi.head(20).itertuples(index=False):
        print(f"    {row.feature:30s} {row.importance:.2f}")
    return fi


def evaluate(model: object, X: np.ndarray, y: np.ndarray) -> None:
    prob = model.predict(X)
    pred = (prob > 0.5).astype(int)
    print("\n  full-train reference metrics")
    print(f"  accuracy: {accuracy_score(y, pred):.4f}")
    try:
        print(f"  auc:      {roc_auc_score(y, prob):.4f}")
    except ValueError:
        pass
    print("  classification report:")
    print(classification_report(y, pred, target_names=["down(0)", "up(1)"]))
    print("  confusion matrix:")
    print(confusion_matrix(y, pred))


def save_model(
    model: object,
    encoder: LabelEncoder,
    feature_cols: list[str],
    out_path: str,
    meta: dict,
) -> None:
    payload = {
        "model": model,
        "label_encoder": encoder,
        "feature_cols": feature_cols,
        "meta": meta,
    }
    with open(out_path, "wb") as handle:
        pickle.dump(payload, handle)
    print(f"\n  saved model: {out_path}")

    meta_path = out_path.replace(".pkl", "_meta.json")
    with open(meta_path, "w", encoding="utf-8") as handle:
        json.dump(meta, handle, ensure_ascii=False, indent=2)
    print(f"  saved meta: {meta_path}")


def main(args: argparse.Namespace) -> None:
    print("=" * 60)
    print("[1/4] Load article features")
    print("=" * 60)
    df = load_features(args.features, target_col=args.target)
    if len(df) < 20:
        raise SystemExit(f"Not enough rows to train: {len(df)}")

    feature_cols = [col for col in DEFAULT_FEATURE_COLS if col in df.columns]
    missing = [col for col in DEFAULT_FEATURE_COLS if col not in df.columns]
    if missing:
        print(f"  missing feature columns (skipped): {missing}")

    print("\n" + "=" * 60)
    print("[2/4] Preprocess")
    print("=" * 60)
    X, y, all_feature_cols, encoder, df_proc = preprocess(
        df=df,
        feature_cols=feature_cols,
        target_col=args.target,
    )
    print(f"  X shape: {X.shape}")
    print(f"  y shape: {y.shape}")

    print("\n" + "=" * 60)
    print("[3/4] Train LightGBM")
    print("=" * 60)
    final_model, best_model, cv_scores, cv_aucs = train_lgbm(
        X=X,
        y=y,
        feature_names=all_feature_cols,
        n_splits=args.cv_splits,
    )
    fi = print_feature_importance(final_model, all_feature_cols)

    print("\n" + "=" * 60)
    print("[4/4] Evaluate and save")
    print("=" * 60)
    evaluate(final_model, X, y)

    meta = {
        "target": args.target,
        "feature_cols": all_feature_cols,
        "cv_acc_mean": float(np.mean(cv_scores)),
        "cv_acc_std": float(np.std(cv_scores)),
        "cv_auc_mean": float(np.mean(cv_aucs)),
        "cv_auc_std": float(np.std(cv_aucs)),
        "n_samples": int(len(df_proc)),
        "stock_classes": encoder.classes_.tolist(),
        "label_distribution": df_proc[args.target].value_counts().to_dict(),
        "feature_importance": fi.set_index("feature")["importance"].to_dict(),
    }
    save_model(
        model=final_model,
        encoder=encoder,
        feature_cols=all_feature_cols,
        out_path=args.model,
        meta=meta,
    )

    print("\n  training complete")
    print(f"  cv auc: {np.mean(cv_aucs):.4f}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--features", default="article_features.csv")
    parser.add_argument("--model", default="article_lgbm_model.pkl")
    parser.add_argument("--target", default="label_1d")
    parser.add_argument("--cv-splits", type=int, default=3)
    main(parser.parse_args())
