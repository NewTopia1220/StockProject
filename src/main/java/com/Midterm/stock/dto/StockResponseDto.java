package com.Midterm.stock.dto;

import lombok.Data;

@Data
public class StockResponseDto {
    // 종목
    private String stockName;   // 종목명
    private String currentPrice; // 현재가
    private String openPrice;    // 시가
    private String highPrice;    // 고가
    private String lowPrice;     // 저가
    private String volume;       // 거래량

    // 코스피용 추가
    private String priceChange;  // 전일 대비
    private String changeRate;   // 등락률
}

