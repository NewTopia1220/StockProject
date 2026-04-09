"""
Build article-level training features from analyzed news data and daily prices.

Examples:
    python article_feature_builder.py
    python article_feature_builder.py --sqlite news_data.db --out article_features.csv
"""

from __future__ import annotations

import argparse
import sqlite3
from bisect import bisect_left
from datetime import timedelta
from pathlib import Path
from typing import Iterable

import numpy as np
import pandas as pd

try:
    import FinanceDataReader as fdr
except ImportError as exc:
    raise SystemExit(
        "FinanceDataReader is required. Install it with: pip install finance-datareader"
    ) from exc


CATEGORY_TO_CODE = {
    "반도체/AI": "005930",
    "IT/반도체": "005930",
    "삼성전자 (IT/반도체)": "005930",
    "2차전지": "373220",
    "LG에너지솔루션 (2차전지)": "373220",
    "바이오": "207940",
    "제약/바이오": "207940",
    "삼성바이오로직스 (제약/바이오)": "207940",
    "IT/플랫폼": "035420",
    "네이버 (IT/플랫폼)": "035420",
    "자동차/모빌리티": "005380",
    "현대차 (자동차/모빌리티)": "005380",
    "방산": "012450",
    "방산/우주항공": "012450",
    "한화에어로스페이스 (방산/우주항공)": "012450",
    "엔터/미디어": "352820",
    "하이브 (엔터/미디어)": "352820",
    "금융/밸류업": "105560",
    "KB금융 (금융/밸류업)": "105560",
}

CATEGORY_NORM = {
    "반도체/AI": "반도체/AI",
    "IT/반도체": "반도체/AI",
    "삼성전자 (IT/반도체)": "반도체/AI",
    "2차전지": "2차전지",
    "LG에너지솔루션 (2차전지)": "2차전지",
    "바이오": "제약/바이오",
    "제약/바이오": "제약/바이오",
    "삼성바이오로직스 (제약/바이오)": "제약/바이오",
    "IT/플랫폼": "IT/플랫폼",
    "네이버 (IT/플랫폼)": "IT/플랫폼",
    "자동차/모빌리티": "자동차/모빌리티",
    "현대차 (자동차/모빌리티)": "자동차/모빌리티",
    "방산": "방산/우주항공",
    "방산/우주항공": "방산/우주항공",
    "한화에어로스페이스 (방산/우주항공)": "방산/우주항공",
    "엔터/미디어": "엔터/미디어",
    "하이브 (엔터/미디어)": "엔터/미디어",
    "금융/밸류업": "금융/밸류업",
    "KB금융 (금융/밸류업)": "금융/밸류업",
}

SENTIMENT_MAP = {"호재": 1, "중립": 0, "악재": -1}
MARKET_OPEN_HOUR = 9
MARKET_CLOSE_HOUR = 15
MARKET_CLOSE_MINUTE = 30
PRICE_FEATURE_COLS = [
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


def normalize_columns(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    df.columns = [str(col).strip().lower() for col in df.columns]
    return df


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


def load_source_csv(path: str) -> pd.DataFrame:
    if not Path(path).exists():
        print(f"  [skip] missing file: {path}")
        return pd.DataFrame()
    df = pd.read_csv(path)
    df = normalize_columns(df)
    print(f"  [csv] {path}: {len(df)} rows")
    return df


def load_source_sqlite(path: str) -> pd.DataFrame:
    if not Path(path).exists():
        print(f"  [skip] missing file: {path}")
        return pd.DataFrame()
    with sqlite3.connect(path) as conn:
        df = pd.read_sql("SELECT * FROM news_data", conn)
    df = normalize_columns(df)
    print(f"  [sqlite] {path}: {len(df)} rows")
    return df


def load_news(
    oracle_company_csv: str,
    oracle_sector_csv: str,
    sqlite_db: str,
) -> pd.DataFrame:
    parts = [
        load_source_csv(oracle_company_csv),
        load_source_csv(oracle_sector_csv),
        load_source_sqlite(sqlite_db),
    ]
    parts = [part for part in parts if not part.empty]
    if not parts:
        raise RuntimeError("No input news source was found.")

    df = pd.concat(parts, ignore_index=True)
    df = df.drop_duplicates(subset="link")

    for column in [
        "link",
        "category",
        "title",
        "summary",
        "sentiment",
        "pub_date",
        "clickbait_prob",
        "article_type",
        "type_prob",
    ]:
        if column not in df.columns:
            df[column] = np.nan

    df["category"] = df["category"].astype(str).str.strip()
    df["stock_code"] = df["category"].map(CATEGORY_TO_CODE)
    df["category_norm"] = df["category"].map(CATEGORY_NORM)
    before = len(df)
    df = df[df["stock_code"].notna()].copy()
    print(f"  mapped categories: {len(df)} / {before} rows")

    df["pub_date"] = pd.to_datetime(df["pub_date"], errors="coerce")
    df = df.dropna(subset=["pub_date"]).sort_values("pub_date").reset_index(drop=True)

    df["stock_code"] = df["stock_code"].map(normalize_stock_code)
    df["title"] = df["title"].fillna("").astype(str)
    df["summary"] = df["summary"].fillna("").astype(str)
    df["sentiment"] = df["sentiment"].fillna("중립").astype(str)
    df["article_type"] = df["article_type"].fillna("").astype(str)
    df["clickbait_prob"] = pd.to_numeric(df["clickbait_prob"], errors="coerce").fillna(0.0)
    df["type_prob"] = pd.to_numeric(df["type_prob"], errors="coerce").fillna(0.0)

    return df


def enrich_article_features(df: pd.DataFrame) -> pd.DataFrame:
    df = df.copy()
    df["sentiment_score"] = df["sentiment"].map(SENTIMENT_MAP).fillna(0).astype(float)
    df["is_fact"] = (df["article_type"] == "사실형").astype(int)
    df["is_predict"] = (df["article_type"] == "예측형").astype(int)
    df["is_reasoning"] = (df["article_type"] == "추론형").astype(int)
    df["title_length"] = df["title"].str.len()
    df["summary_length"] = df["summary"].str.len()
    df["title_word_count"] = df["title"].str.split().str.len().fillna(0)
    df["summary_word_count"] = df["summary"].str.split().str.len().fillna(0)
    df["has_summary"] = (df["summary_length"] > 0).astype(int)
    df["pub_hour"] = df["pub_date"].dt.hour
    df["pub_minute"] = df["pub_date"].dt.minute
    df["pub_weekday"] = df["pub_date"].dt.weekday
    df["is_weekend"] = (df["pub_weekday"] >= 5).astype(int)
    df["is_before_open"] = (
        (df["pub_hour"] < MARKET_OPEN_HOUR)
        | ((df["pub_hour"] == MARKET_OPEN_HOUR) & (df["pub_minute"] == 0))
    ).astype(int)
    df["is_after_close"] = (
        (df["pub_hour"] > MARKET_CLOSE_HOUR)
        | (
            (df["pub_hour"] == MARKET_CLOSE_HOUR)
            & (df["pub_minute"] >= MARKET_CLOSE_MINUTE)
        )
    ).astype(int)
    return df


def build_recent_context(df: pd.DataFrame, lookback_days: int) -> pd.DataFrame:
    lookback_ns = pd.Timedelta(days=lookback_days).value
    chunks: list[pd.DataFrame] = []

    for _, group in df.sort_values("pub_date").groupby("stock_code", sort=False):
        group = group.sort_values("pub_date").copy()
        timestamps = group["pub_date"].astype("int64").to_numpy()
        sentiments = group["sentiment_score"].to_numpy()
        clickbait = group["clickbait_prob"].to_numpy()
        type_prob = group["type_prob"].to_numpy()
        is_fact = group["is_fact"].to_numpy()
        is_predict = group["is_predict"].to_numpy()
        is_reasoning = group["is_reasoning"].to_numpy()

        rows = []
        left = 0

        for i in range(len(group)):
            cutoff = timestamps[i] - lookback_ns
            while left < i and timestamps[left] < cutoff:
                left += 1

            start = left
            end = i
            count = end - start

            if count <= 0:
                rows.append(
                    {
                        "recent_article_count_3d": 0,
                        "recent_sentiment_mean_3d": 0.0,
                        "recent_sentiment_sum_3d": 0.0,
                        "recent_positive_ratio_3d": 0.0,
                        "recent_negative_ratio_3d": 0.0,
                        "recent_sentiment_intensity_3d": 0.0,
                        "recent_clickbait_mean_3d": 0.0,
                        "recent_type_prob_mean_3d": 0.0,
                        "recent_fact_ratio_3d": 0.0,
                        "recent_predict_ratio_3d": 0.0,
                        "recent_reasoning_ratio_3d": 0.0,
                        "hours_since_prev_article": float(lookback_days * 24),
                    }
                )
                continue

            window_sent = sentiments[start:end]
            positive_ratio = float((window_sent == 1).sum() / count)
            negative_ratio = float((window_sent == -1).sum() / count)
            previous_gap_hours = float((timestamps[i] - timestamps[end - 1]) / 3_600_000_000_000)

            rows.append(
                {
                    "recent_article_count_3d": int(count),
                    "recent_sentiment_mean_3d": float(window_sent.mean()),
                    "recent_sentiment_sum_3d": float(window_sent.sum()),
                    "recent_positive_ratio_3d": positive_ratio,
                    "recent_negative_ratio_3d": negative_ratio,
                    "recent_sentiment_intensity_3d": positive_ratio - negative_ratio,
                    "recent_clickbait_mean_3d": float(clickbait[start:end].mean()),
                    "recent_type_prob_mean_3d": float(type_prob[start:end].mean()),
                    "recent_fact_ratio_3d": float(is_fact[start:end].mean()),
                    "recent_predict_ratio_3d": float(is_predict[start:end].mean()),
                    "recent_reasoning_ratio_3d": float(is_reasoning[start:end].mean()),
                    "hours_since_prev_article": previous_gap_hours,
                }
            )

        chunk = pd.DataFrame(rows, index=group.index)
        chunks.append(chunk)

    context = pd.concat(chunks).sort_index()
    return context


def calc_rsi(series: pd.Series, period: int = 14) -> pd.Series:
    delta = series.diff()
    gain = delta.clip(lower=0).rolling(period).mean()
    loss = (-delta.clip(upper=0)).rolling(period).mean()
    rs = gain / loss.replace(0, np.nan)
    return 100 - (100 / (1 + rs))


def calc_macd(series: pd.Series, fast: int = 12, slow: int = 26, signal: int = 9):
    ema_fast = series.ewm(span=fast, adjust=False).mean()
    ema_slow = series.ewm(span=slow, adjust=False).mean()
    macd_line = ema_fast - ema_slow
    signal_line = macd_line.ewm(span=signal, adjust=False).mean()
    histogram = macd_line - signal_line
    return macd_line, signal_line, histogram


def calc_bollinger(series: pd.Series, period: int = 20, std_k: int = 2):
    ma = series.rolling(period).mean()
    std = series.rolling(period).std()
    upper = ma + std_k * std
    lower = ma - std_k * std
    width = (upper - lower).replace(0, np.nan)
    pct_b = (series - lower) / width
    return ma, upper, lower, pct_b


def fetch_price_frames(
    stock_codes: Iterable[str],
    start_date: pd.Timestamp,
    end_date: pd.Timestamp,
) -> dict[str, pd.DataFrame]:
    fetch_start = (start_date - timedelta(days=120)).strftime("%Y-%m-%d")
    fetch_end = (end_date + timedelta(days=14)).strftime("%Y-%m-%d")

    frames: dict[str, pd.DataFrame] = {}
    for code in sorted(set(stock_codes)):
        print(f"  fetching prices: {code}")
        raw = fdr.DataReader(code, fetch_start, fetch_end)
        if raw is None or raw.empty:
            print(f"    [skip] no price data for {code}")
            continue

        raw.index = pd.to_datetime(raw.index).normalize()
        price = pd.DataFrame(index=raw.index)
        price["close"] = raw["Close"]
        price["open"] = raw["Open"] if "Open" in raw.columns else np.nan
        price["high"] = raw["High"] if "High" in raw.columns else np.nan
        price["low"] = raw["Low"] if "Low" in raw.columns else np.nan
        price["volume"] = raw["Volume"] if "Volume" in raw.columns else np.nan
        price["stock_code"] = code

        close = price["close"]
        daily_ret = close.pct_change()

        price["ma_5"] = close.rolling(5).mean()
        price["ma_20"] = close.rolling(20).mean()
        price["ma_60"] = close.rolling(60).mean()
        price["close_to_ma5"] = (close - price["ma_5"]) / price["ma_5"]
        price["close_to_ma20"] = (close - price["ma_20"]) / price["ma_20"]
        price["ma_cross"] = np.sign(price["ma_5"] - price["ma_20"])

        price["rsi_14"] = calc_rsi(close, 14)
        price["rsi_zone"] = pd.cut(
            price["rsi_14"],
            bins=[0, 30, 50, 70, 100],
            labels=[-1, 0, 0, 1],
            ordered=False,
        ).astype(float)

        macd_line, signal_line, histogram = calc_macd(close)
        price["macd"] = macd_line
        price["macd_sig"] = signal_line
        price["macd_hist"] = histogram
        price["macd_cross"] = np.sign(histogram)

        _, _, _, pct_b = calc_bollinger(close)
        price["bb_pctb"] = pct_b

        price["volatility_5d"] = daily_ret.rolling(5).std()
        price["volatility_20d"] = daily_ret.rolling(20).std()
        price["volume_ma5"] = price["volume"].rolling(5).mean()
        price["volume_ratio"] = price["volume"] / price["volume_ma5"].replace(0, np.nan)

        price["return_before_1d"] = daily_ret
        price["return_before_3d"] = close.pct_change(3)
        price["return_before_5d"] = close.pct_change(5)

        price.index.name = "trade_date"
        price = price.reset_index()
        price["trade_date"] = pd.to_datetime(price["trade_date"]).dt.normalize()
        price["stock_code"] = price["stock_code"].map(normalize_stock_code)
        frames[code] = price

    if not frames:
        raise RuntimeError("No price data could be downloaded.")

    return frames


def resolve_trade_window(
    trade_dates: list[pd.Timestamp],
    pub_timestamp: pd.Timestamp,
    is_after_close: bool,
) -> tuple[int | None, int | None]:
    pub_day = pub_timestamp.normalize()
    pos = bisect_left(trade_dates, pub_day)
    same_day = pos < len(trade_dates) and trade_dates[pos] == pub_day

    if same_day and not is_after_close:
        anchor_idx = pos
    elif same_day:
        anchor_idx = pos + 1
    else:
        anchor_idx = pos

    context_idx = anchor_idx - 1
    if anchor_idx >= len(trade_dates) or context_idx < 0:
        return None, None
    return anchor_idx, context_idx


def build_article_dataset(
    articles: pd.DataFrame,
    price_frames: dict[str, pd.DataFrame],
    label_threshold: float,
) -> pd.DataFrame:
    rows = []

    for article in articles.itertuples(index=False):
        price_df = price_frames.get(article.stock_code)
        if price_df is None or price_df.empty:
            continue

        trade_dates = price_df["trade_date"].tolist()
        anchor_idx, context_idx = resolve_trade_window(
            trade_dates=trade_dates,
            pub_timestamp=article.pub_date,
            is_after_close=bool(article.is_after_close),
        )
        if anchor_idx is None or context_idx is None:
            continue

        context_row = price_df.iloc[context_idx]
        base_close = float(context_row["close"])
        if not np.isfinite(base_close) or base_close == 0:
            continue

        anchor_date = trade_dates[anchor_idx]
        context_date = trade_dates[context_idx]
        record = {
            "link": article.link,
            "stock_code": article.stock_code,
            "category": article.category,
            "category_norm": article.category_norm,
            "title": article.title,
            "summary": article.summary,
            "pub_date": article.pub_date,
            "context_trade_date": context_date,
            "anchor_trade_date": anchor_date,
            "anchor_delay_days": int((anchor_date - article.pub_date.normalize()).days),
            "sentiment": article.sentiment,
            "sentiment_score": article.sentiment_score,
            "clickbait_prob": article.clickbait_prob,
            "article_type": article.article_type,
            "type_prob": article.type_prob,
            "is_fact": article.is_fact,
            "is_predict": article.is_predict,
            "is_reasoning": article.is_reasoning,
            "title_length": article.title_length,
            "summary_length": article.summary_length,
            "title_word_count": article.title_word_count,
            "summary_word_count": article.summary_word_count,
            "has_summary": article.has_summary,
            "pub_hour": article.pub_hour,
            "pub_weekday": article.pub_weekday,
            "is_weekend": article.is_weekend,
            "is_before_open": article.is_before_open,
            "is_after_close": article.is_after_close,
            "recent_article_count_3d": article.recent_article_count_3d,
            "recent_sentiment_mean_3d": article.recent_sentiment_mean_3d,
            "recent_sentiment_sum_3d": article.recent_sentiment_sum_3d,
            "recent_positive_ratio_3d": article.recent_positive_ratio_3d,
            "recent_negative_ratio_3d": article.recent_negative_ratio_3d,
            "recent_sentiment_intensity_3d": article.recent_sentiment_intensity_3d,
            "recent_clickbait_mean_3d": article.recent_clickbait_mean_3d,
            "recent_type_prob_mean_3d": article.recent_type_prob_mean_3d,
            "recent_fact_ratio_3d": article.recent_fact_ratio_3d,
            "recent_predict_ratio_3d": article.recent_predict_ratio_3d,
            "recent_reasoning_ratio_3d": article.recent_reasoning_ratio_3d,
            "hours_since_prev_article": article.hours_since_prev_article,
        }

        for column in PRICE_FEATURE_COLS:
            record[column] = context_row.get(column, np.nan)

        for horizon in (1, 3, 5):
            target_idx = anchor_idx + (horizon - 1)
            if target_idx >= len(trade_dates):
                record[f"return_{horizon}d"] = np.nan
                record[f"label_{horizon}d"] = np.nan
                continue

            close_after = float(price_df.iloc[target_idx]["close"])
            ret = close_after / base_close - 1.0
            record[f"return_{horizon}d"] = ret
            record[f"label_{horizon}d"] = int(ret > label_threshold)

        rows.append(record)

    if not rows:
        raise RuntimeError("No article-level rows could be created after joining prices.")

    dataset = pd.DataFrame(rows).sort_values("pub_date").reset_index(drop=True)
    return dataset


def main(args: argparse.Namespace) -> None:
    print("=" * 60)
    print("[1/4] Load analyzed news data")
    print("=" * 60)
    articles = load_news(
        oracle_company_csv=args.oracle_company,
        oracle_sector_csv=args.oracle_sector,
        sqlite_db=args.sqlite,
    )

    print("\n" + "=" * 60)
    print("[2/4] Build article and context features")
    print("=" * 60)
    articles = enrich_article_features(articles)
    context = build_recent_context(articles, lookback_days=args.lookback_days)
    articles = pd.concat([articles, context], axis=1)
    print(f"  article rows: {len(articles)}")

    start_date = articles["pub_date"].min().normalize()
    end_date = articles["pub_date"].max().normalize()

    print("\n" + "=" * 60)
    print("[3/4] Fetch price context")
    print("=" * 60)
    price_frames = fetch_price_frames(
        stock_codes=articles["stock_code"].dropna().unique().tolist(),
        start_date=start_date,
        end_date=end_date,
    )

    print("\n" + "=" * 60)
    print("[4/4] Join article rows with price targets")
    print("=" * 60)
    dataset = build_article_dataset(
        articles=articles,
        price_frames=price_frames,
        label_threshold=args.label_threshold,
    )

    ordered_columns = [
        "link",
        "stock_code",
        "category",
        "category_norm",
        "title",
        "summary",
        "pub_date",
        "context_trade_date",
        "anchor_trade_date",
        "anchor_delay_days",
        "sentiment",
        "sentiment_score",
        "clickbait_prob",
        "article_type",
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
        *PRICE_FEATURE_COLS,
        "return_1d",
        "return_3d",
        "return_5d",
        "label_1d",
        "label_3d",
        "label_5d",
    ]
    dataset = dataset[[col for col in ordered_columns if col in dataset.columns]]
    dataset.to_csv(args.out, index=False, encoding="utf-8-sig")

    print(f"  saved: {args.out}")
    print(f"  shape: {dataset.shape}")
    print("  label_1d distribution:")
    print(dataset["label_1d"].value_counts(dropna=False).to_string())
    print("  rows by stock:")
    print(dataset["stock_code"].value_counts().to_string())


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--oracle-company", default="oracle_company.csv")
    parser.add_argument("--oracle-sector", default="oracle_sector.csv")
    parser.add_argument("--sqlite", default="news_data.db")
    parser.add_argument("--lookback-days", type=int, default=3)
    parser.add_argument(
        "--label-threshold",
        type=float,
        default=0.0,
        help="Return threshold for positive labels. Example: 0.003 means +0.3%%.",
    )
    parser.add_argument("--out", default="article_features.csv")
    main(parser.parse_args())
