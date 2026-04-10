package com.Midterm.stock.dto;

import lombok.Data;

/**
 * 주식 / 지수 / 환율 응답 공통 DTO
 */
@Data
public class StockResponseDto {
    private String stockCode;
    private String stockName;
    private String currentPrice;
    private String openPrice;
    private String highPrice;
    private String lowPrice;
    private String volume;
    private String priceChange;
    private String changeRate;
}
