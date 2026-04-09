package com.Midterm.stock.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * predict.py stdout JSON → Java DTO
 *
 * predict.py 출력 예시:
 * {
 *   "stock_code": "005930",
 *   "stock_name": "삼성전자",
 *   "date": "2026-04-09",
 *   "prediction": "상승",
 *   "prediction_int": 1,
 *   "probability": 0.5862,
 *   "confidence": "낮음 (참고만)",
 *   "article_count": 293,
 *   "sentiment_mean": 0.939,
 *   "recent_articles": [...],
 *   "model_meta": { "cv_auc": 0.6079, "n_train": 124, "warning": "..." }
 * }
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiPredictionDto {

    @JsonProperty("stock_code")
    private String stockCode;

    @JsonProperty("stock_name")
    private String stockName;

    @JsonProperty("date")
    private String date;

    /** "상승" | "하락" | null */
    @JsonProperty("prediction")
    private String prediction;

    /** 1=상승, 0=하락 */
    @JsonProperty("prediction_int")
    private Integer predictionInt;

    /** 0.0 ~ 1.0 */
    @JsonProperty("probability")
    private Double probability;

    /** "높음" | "중간" | "낮음 (참고만)" */
    @JsonProperty("confidence")
    private String confidence;

    @JsonProperty("article_count")
    private Integer articleCount;

    @JsonProperty("sentiment_mean")
    private Double sentimentMean;

    @JsonProperty("clickbait_mean")
    private Double clickbaitMean;

    @JsonProperty("type_prob_mean")
    private Double typeProbMean;

    @JsonProperty("fact_ratio")
    private Double factRatio;

    @JsonProperty("volatility_20d")
    private Double volatility20d;

    @JsonProperty("sentiment_intensity")
    private Double sentimentIntensity;

    @JsonProperty("recent_articles")
    private List<Map<String, String>> recentArticles;

    @JsonProperty("model_meta")
    private Map<String, Object> modelMeta;

    /** 오류 메시지 (predict.py에서 error 반환 시) */
    @JsonProperty("error")
    private String error;

    @JsonProperty("message")
    private String message;

    // ── 편의 메서드 ──────────────────────────────────────────

    /** 예측 결과가 유효한지 */
    public boolean isValid() {
        return error == null && prediction != null;
    }

    /** 확률을 % 정수로 반환 (UI 표시용) */
    public int getProbabilityPercent() {
        if (probability == null) return 0;
        return (int) Math.round(probability * 100);
    }

    /** 상승 예측인지 */
    public boolean isUp() {
        return "상승".equals(prediction);
    }

    /** CV AUC (model_meta에서 추출) */
    public double getCvAuc() {
        if (modelMeta == null) return 0.0;
        Object v = modelMeta.get("cv_auc");
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
    }

    /** 학습 샘플 수 */
    public int getNTrain() {
        if (modelMeta == null) return 0;
        Object v = modelMeta.get("n_train");
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    /** 경고 메시지 */
    public String getWarning() {
        if (modelMeta == null) return "";
        Object v = modelMeta.get("warning");
        return v != null ? v.toString() : "";
    }
}
