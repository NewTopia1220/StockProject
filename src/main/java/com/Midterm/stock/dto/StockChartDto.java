package com.Midterm.stock.dto;

import lombok.Data;
import java.util.List;

/**
 * 차트 데이터 응답 DTO
 * - Chart.js 에 바로 전달되는 구조
 */
@Data
public class StockChartDto {

    /** X축 레이블 목록 (날짜 또는 시각) ex) ["2026-04-01", "2026-04-02", ...] */
    private List<String> labels;

    /** Y축 종가/가격 목록 ex) ["75200", "76400", ...] */
    private List<String> closePrices;

    /** 거래량 목록 (추후 거래량 차트 활용 가능) */
    private List<String> volumes;
}