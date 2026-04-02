package com.Midterm.stock.dto;

import lombok.Data;
import java.util.List;

@Data
public class StockChartDto {
    private List<String> labels;      // 날짜 목록
    private List<String> closePrices; // 종가 목록
    private List<String> volumes;     // 거래량 목록
}