"""
predict.py — Spring PageController → Python 호출용 예측 스크립트
출력: JSON (stdout) → Spring에서 파싱

사용법:
    python predict.py --code 005930
    python predict.py --code 005930 --date 2026-04-09
    python predict.py --code 005930 --model lgbm_model.pkl

Spring PageController에서:
    ProcessBuilder pb = new ProcessBuilder(pythonPath, "predict.py", "--code", stockCode);
    // stdout에서 JSON 파싱
"""

import argparse
import json
import pickle
import sys
import sqlite3
from datetime import datetime, timedelta
from pathlib import Path

import numpy as np
import pandas as pd

try:
    import FinanceDataReader as fdr
except ImportError:
    print(json.dumps({"error": "finance-datareader 미설치"}))
    sys.exit(1)


# feature_builder.py와 동일한 매핑
CATEGORY_TO_CODE = {
    "반도체/AI":             "005930",
    "IT/반도체":             "005930",
    "삼성전자 (IT/반도체)":  "005930",
    "2차전지":               "373220",
    "LG에너지솔루션 (2차전지)": "373220",
    "바이오":                "207940",
    "제약/바이오":           "207940",
    "삼성바이오로직스 (제약/바이오)": "207940",
    "IT/플랫폼":             "035420",
    "네이버 (IT/플랫폼)":   "035420",
    "자동차/모빌리티":       "005380",
    "현대차 (자동차/모빌리티)": "005380",
    "방산":                  "012450",
    "방산/우주항공":         "012450",
    "한화에어로스페이스 (방산/우주항공)": "012450",
    "엔터/미디어":           "352820",
    "하이브 (엔터/미디어)": "352820",
    "금융/밸류업":           "105560",
    "KB금융 (금융/밸류업)": "105560",
}

CODE_TO_NAME = {
    "005930": "삼성전자",
    "373220": "LG에너지솔루션",
    "207940": "삼성바이오로직스",
    "035420": "네이버",
    "005380": "현대차",
    "012450": "한화에어로스페이스",
    "352820": "하이브",
    "105560": "KB금융",
}


# ================================================================
# [1] 모델 로드
# ================================================================
def load_model(model_path: str) -> dict:
    with open(model_path, "rb") as f:
        return pickle.load(f)


# ================================================================
# [2] 최근 뉴스 피처 생성 (오늘 기준 최근 3일)
# ================================================================
def get_news_features(
    stock_code: str,
    target_date: str,
    sqlite_db: str   = "news_data.db",
    oracle_company:  str = "oracle_company.csv",
    oracle_sector:   str = "oracle_sector.csv",
    days_back: int   = 3,
) -> dict:
    """target_date 기준 최근 days_back일 뉴스를 집계해서 피처 반환"""

    date_end   = pd.to_datetime(target_date)
    date_start = date_end - timedelta(days=days_back)

    # 해당 종목의 카테고리 목록
    target_cats = [cat for cat, code in CATEGORY_TO_CODE.items() if code == stock_code]

    dfs = []
    # Oracle CSV
    for path in [oracle_company, oracle_sector]:
        if Path(path).exists():
            df = pd.read_csv(path)
            df.columns = [c.lower() for c in df.columns]
            dfs.append(df)

    # SQLite
    if Path(sqlite_db).exists():
        conn = sqlite3.connect(sqlite_db)
        df_sq = pd.read_sql("SELECT * FROM news_data", conn)
        conn.close()
        df_sq = df_sq.drop(columns=["created_at"], errors="ignore")
        dfs.append(df_sq)

    if not dfs:
        return {}

    df_all = pd.concat(dfs, ignore_index=True).drop_duplicates(subset="link")
    df_all["pub_date"] = pd.to_datetime(df_all["pub_date"], errors="coerce")

    # 종목 + 기간 필터
    df_filtered = df_all[
        (df_all["category"].isin(target_cats)) &
        (df_all["pub_date"] >= date_start) &
        (df_all["pub_date"] <= date_end)
    ].copy()

    if len(df_filtered) == 0:
        return {}

    # 수치화
    sentiment_map = {"호재": 1, "중립": 0, "악재": -1}
    df_filtered["sentiment_score"] = df_filtered["sentiment"].map(sentiment_map).fillna(0)
    df_filtered["clickbait_prob"]  = pd.to_numeric(df_filtered["clickbait_prob"], errors="coerce").fillna(0)
    df_filtered["type_prob"]       = pd.to_numeric(df_filtered["type_prob"],      errors="coerce").fillna(0)
    df_filtered["is_fact"]         = (df_filtered["article_type"] == "사실형").astype(int)
    df_filtered["is_predict"]      = (df_filtered["article_type"] == "예측형").astype(int)
    df_filtered["is_reasoning"]    = (df_filtered["article_type"] == "추론형").astype(int)

    n     = len(df_filtered)
    pos_n = (df_filtered["sentiment_score"] == 1).sum()
    neg_n = (df_filtered["sentiment_score"] == -1).sum()

    return {
        "article_count":       n,
        "sentiment_mean":      df_filtered["sentiment_score"].mean(),
        "sentiment_sum":       df_filtered["sentiment_score"].sum(),
        "positive_count":      int(pos_n),
        "negative_count":      int(neg_n),
        "neutral_count":       int(n - pos_n - neg_n),
        "positive_ratio":      pos_n / n,
        "negative_ratio":      neg_n / n,
        "sentiment_intensity": (pos_n - neg_n) / n,
        "clickbait_mean":      df_filtered["clickbait_prob"].mean(),
        "type_prob_mean":      df_filtered["type_prob"].mean(),
        "fact_ratio":          df_filtered["is_fact"].mean(),
        "predict_ratio":       df_filtered["is_predict"].mean(),
        "reasoning_ratio":     df_filtered["is_reasoning"].mean(),
        "recent_articles":     df_filtered[["title", "sentiment", "pub_date"]]
                                .sort_values("pub_date", ascending=False)
                                .head(5)
                                .to_dict("records"),
    }


# ================================================================
# [3] 주가 기술적 지표 계산 (feature_builder와 동일 로직)
# ================================================================
def _rsi(series, period=14):
    delta = series.diff()
    gain  = delta.clip(lower=0).rolling(period).mean()
    loss  = (-delta.clip(upper=0)).rolling(period).mean()
    rs    = gain / loss.replace(0, np.nan)
    return 100 - (100 / (1 + rs))

def _macd_hist(series, fast=12, slow=26, signal=9):
    macd = series.ewm(span=fast, adjust=False).mean() - series.ewm(span=slow, adjust=False).mean()
    return macd - macd.ewm(span=signal, adjust=False).mean()

def _bb_pctb(series, period=20, std_k=2):
    ma  = series.rolling(period).mean()
    std = series.rolling(period).std()
    return (series - (ma - std_k * std)) / (2 * std_k * std).replace(0, np.nan)

def get_price_features(stock_code: str, target_date: str) -> dict:
    try:
        # MA60 워밍업 포함 충분히 앞선 날짜부터
        start = (pd.to_datetime(target_date) - timedelta(days=120)).strftime("%Y-%m-%d")
        raw   = fdr.DataReader(stock_code, start, target_date)
        raw.index = pd.to_datetime(raw.index)

        close  = raw["Close"]
        volume = raw["Volume"] if "Volume" in raw.columns else pd.Series(dtype=float)

        ma5  = close.rolling(5).mean()
        ma20 = close.rolling(20).mean()
        ma60 = close.rolling(60).mean()

        def safe(val):
            return float(val) if (val is not None and not np.isnan(float(val))) else 0.0

        last = close.iloc[-1]
        prev = close.iloc[-2] if len(close) > 1 else last

        vol_ma5 = volume.rolling(5).mean().iloc[-1] if len(volume) > 0 else np.nan

        return {
            "close":            safe(last),
            "close_to_ma5":     safe((last - ma5.iloc[-1])  / ma5.iloc[-1])  if not np.isnan(ma5.iloc[-1])  else 0.0,
            "close_to_ma20":    safe((last - ma20.iloc[-1]) / ma20.iloc[-1]) if not np.isnan(ma20.iloc[-1]) else 0.0,
            "ma_cross":         safe(np.sign(ma5.iloc[-1] - ma20.iloc[-1])),
            "rsi_14":           safe(_rsi(close, 14).iloc[-1]),
            "rsi_zone":         safe(np.sign(_rsi(close, 14).iloc[-1] - 50)),
            "macd_hist":        safe(_macd_hist(close).iloc[-1]),
            "macd_cross":       safe(np.sign(_macd_hist(close).iloc[-1])),
            "bb_pctb":          safe(_bb_pctb(close).iloc[-1]),
            "volatility_5d":    safe(close.pct_change().rolling(5).std().iloc[-1]),
            "volatility_20d":   safe(close.pct_change().rolling(20).std().iloc[-1]),
            "volume_ratio":     safe(volume.iloc[-1] / vol_ma5) if len(volume) > 0 and not np.isnan(vol_ma5) else 1.0,
            "return_before_1d": safe(close.pct_change().iloc[-1]),
            "return_before_3d": safe(close.pct_change(3).iloc[-1]),
            "return_before_5d": safe(close.pct_change(5).iloc[-1]),
        }
    except Exception as e:
        return {k: 0.0 for k in [
            "close", "close_to_ma5", "close_to_ma20", "ma_cross",
            "rsi_14", "rsi_zone", "macd_hist", "macd_cross", "bb_pctb",
            "volatility_5d", "volatility_20d", "volume_ratio",
            "return_before_1d", "return_before_3d", "return_before_5d",
        ]}


# ================================================================
# [4] 예측
# ================================================================
def predict(
    payload: dict,
    stock_code: str,
    target_date: str,
    sqlite_db: str = "news_data.db",
    oracle_company: str = "oracle_company.csv",
    oracle_sector: str = "oracle_sector.csv",
) -> dict:
    model_obj    = payload["model"]
    le           = payload["label_encoder"]
    feature_cols = payload["feature_cols"]
    meta         = payload["meta"]

    # 종목 인코딩
    if stock_code in le.classes_:
        stock_code_enc = int(le.transform([stock_code])[0])
    else:
        stock_code_enc = -1  # 미학습 종목

    news_feat  = get_news_features(
        stock_code,
        target_date,
        sqlite_db=sqlite_db,
        oracle_company=oracle_company,
        oracle_sector=oracle_sector,
    )
    price_feat = get_price_features(stock_code, target_date)

    if not news_feat:
        return {
            "stock_code":  stock_code,
            "stock_name":  CODE_TO_NAME.get(stock_code, stock_code),
            "date":        target_date,
            "prediction":  None,
            "probability": None,
            "confidence":  "데이터 없음",
            "message":     f"최근 3일 내 뉴스 없음 (종목코드: {stock_code})",
            "articles":    [],
        }

    # 피처 벡터 구성
    feature_map = {**news_feat, **price_feat, "stock_code_enc": stock_code_enc}
    X = np.array([[feature_map.get(col, 0.0) for col in feature_cols]])

    prob   = float(model_obj.predict(X)[0])
    pred   = int(prob > 0.5)
    conf   = abs(prob - 0.5) * 2  # 0~1 확신도

    if conf > 0.6:
        confidence_label = "높음"
    elif conf > 0.3:
        confidence_label = "중간"
    else:
        confidence_label = "낮음 (참고만)"

    return {
        "stock_code":     stock_code,
        "stock_name":     CODE_TO_NAME.get(stock_code, stock_code),
        "date":           target_date,
        "prediction":     "상승" if pred == 1 else "하락",
        "prediction_int": pred,
        "probability":    round(prob, 4),
        "confidence":     confidence_label,
        "article_count":  news_feat.get("article_count", 0),
        "sentiment_mean": round(news_feat.get("sentiment_mean", 0), 3),
        "sentiment_intensity": round(news_feat.get("sentiment_intensity", 0), 3),
        "clickbait_mean": round(news_feat.get("clickbait_mean", 0), 2),
        "type_prob_mean": round(news_feat.get("type_prob_mean", 0), 2),
        "fact_ratio": round(news_feat.get("fact_ratio", 0), 3),
        "volatility_20d": round(price_feat.get("volatility_20d", 0), 4),
        "recent_articles": [
            {
                "title":     a["title"],
                "sentiment": a["sentiment"],
                "date":      str(a["pub_date"])[:10],
            }
            for a in news_feat.get("recent_articles", [])
        ],
        "model_meta": {
            "cv_auc":   round(meta.get("cv_auc_mean", 0), 4),
            "n_train":  meta.get("n_samples", 0),
            "warning":  "데이터 부족으로 신뢰도 낮을 수 있음" if meta.get("n_samples", 0) < 200 else "",
        },
    }


# ================================================================
# [메인]
# ================================================================
def main(args):
    # 모델 로드
    if not Path(args.model).exists():
        result = {"error": f"모델 파일 없음: {args.model}. train_model.py 먼저 실행하세요."}
        print(json.dumps(result, ensure_ascii=False))
        sys.exit(1)

    payload = load_model(args.model)

    # 날짜 기본값: 오늘
    target_date = args.date or datetime.now().strftime("%Y-%m-%d")

    result = predict(
        payload,
        args.code,
        target_date,
        sqlite_db=args.sqlite,
        oracle_company=args.oracle_company,
        oracle_sector=args.oracle_sector,
    )
    print(json.dumps(result, ensure_ascii=False, default=str))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--code",  required=True,              help="종목코드 (예: 005930)")
    parser.add_argument("--date",  default=None,               help="예측 기준일 (기본: 오늘)")
    parser.add_argument("--model", default="lgbm_model.pkl",   help="모델 파일")
    parser.add_argument("--sqlite",default="news_data.db",     help="SQLite DB 경로")
    parser.add_argument("--oracle-company", default="oracle_company.csv")
    parser.add_argument("--oracle-sector",  default="oracle_sector.csv")
    args = parser.parse_args()
    main(args)
