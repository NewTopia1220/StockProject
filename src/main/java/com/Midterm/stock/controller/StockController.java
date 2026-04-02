package com.Midterm.stock.controller;

import com.Midterm.stock.dto.StockChartDto;
import com.Midterm.stock.dto.StockResponseDto;
import com.Midterm.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;


    @GetMapping("/stockMain")
    public String index() {
        return "stock/index";
    }

    // 종목 조회
    @GetMapping("/stockInquiry")
    public String getStock(@RequestParam String code, Model model) {
        StockResponseDto stockInfo = stockService.getCurrentPrice(code);
        StockChartDto chartData = stockService.getDailyPrice(code);
        StockResponseDto kospiInfo = stockService.getKospiIndex(); // 코스피


        model.addAttribute("stockInfo", stockInfo);
        model.addAttribute("chartData", chartData);
        model.addAttribute("stockCode", code);
        model.addAttribute("kospiInfo", kospiInfo);
        return "stock/stockInquiry";
    }

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

    // AJAX용 코스피 API
    @GetMapping("/api/kospi")
    @ResponseBody
    public StockResponseDto getKospiApi() {
        return stockService.getKospiIndex();
    }
}