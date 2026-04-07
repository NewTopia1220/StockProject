package com.Midterm.stock.controller;

import com.Midterm.stock.dto.StockChartDto;
import com.Midterm.stock.dto.StockResponseDto;
import com.Midterm.stock.service.StockService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class StockController {

    @Autowired
    private final StockService stockService;


    @GetMapping("/stockMain")
    public String index() {
        return "stock/index";
    }

    // 종목 조회
//    @GetMapping("/stockInquiry")
//    public String getStock(@RequestParam String code, Model model) {
//        StockResponseDto stockInfo = stockService.getCurrentPrice(code);
//        StockChartDto chartData = stockService.getDailyPrice(code);
//        StockResponseDto kospiInfo = stockService.getKospiIndex(); // 코스피
//        model.addAttribute("kospiChartData", stockService.getKospiChart()); // 코스피 차트
//
//
//        model.addAttribute("stockInfo", stockInfo);
//        model.addAttribute("chartData", chartData);
//        model.addAttribute("stockCode", code);
//        model.addAttribute("kospiInfo", kospiInfo);
//        return "stock/stockInquiry";
//    }

    // API 엔드포인트 추가
    @GetMapping("/api/stock/{code}")
    @ResponseBody
    public StockResponseDto getStockApi(@PathVariable String code) {
        return stockService.getCurrentPrice(code);
    }

    @GetMapping("/api/stock/{code}/chart")
    @ResponseBody
    public StockChartDto getChartApi(@PathVariable String code) {
        return stockService.getDailyPrice(code);
    }

    // 검색
    @GetMapping("/api/stock/search")
    @ResponseBody
    public List<Map<String, String>> searchStock(@RequestParam String keyword) {
        try {
            return stockService.searchStock(keyword);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    // AJAX용 코스피 API
    @GetMapping("/api/kospi")
    @ResponseBody
    public StockResponseDto getKospiApi() {
        return stockService.getKospiIndex();
    }

    @GetMapping("/api/kospi/chart")
    @ResponseBody
    public StockChartDto getKospiChartApi() {
        return stockService.getKospiChart();
    }


    @GetMapping("/api/kosdaq")
    @ResponseBody
    public StockResponseDto getKosdaqApi() {
        return stockService.getKosdaqIndex();
    }

    @GetMapping("/api/exchange")
    @ResponseBody
    public StockResponseDto getExchangeApi(
            @RequestParam(defaultValue = "USD") String currency) {
        return stockService.getExchangeRate(currency);
    }

    // 시간별
    @GetMapping("/api/stock/{code}/time")
    @ResponseBody
    public StockChartDto getTimeChart(@PathVariable String code) {
        // 장 외 시간엔 일별로 대체
        if (!isMarketOpen()) {
            return stockService.getDailyPrice(code);
        }
        return stockService.getTimePrice(code);
    }

    // 분별
    @GetMapping("/api/stock/{code}/minute")
    @ResponseBody
    public StockChartDto getMinuteChart(@PathVariable String code) {
        if (!isMarketOpen()) {
            return stockService.getDailyPrice(code);
        }
        return stockService.getMinutePrice(code);
    }

    // 장 운영시간 체크
    private boolean isMarketOpen() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int day = now.getDayOfWeek().getValue(); // 1=월 ~ 7=일
        if (day >= 6) return false; // 주말
        int time = now.getHour() * 100 + now.getMinute();
        return time >= 900 && time <= 1530;
    }

    // DB 저장 확인용 (나중에 지워도됌)
    @GetMapping("/api/stock/refresh")
    @ResponseBody
    public String refreshStocks() {
        try {
            stockService.refreshStockListToDB();
            return "완료: " + "DB 갱신 요청됨";
        } catch (Exception e) {
            return "오류: " + e.getMessage();
        }
    }

    // 등락률 관련
    @GetMapping("/api/stock/top-fluctuation")
    @ResponseBody
    public List<StockResponseDto> getTopFluctuation() {
        return stockService.getTopFluctuation();
    }


}