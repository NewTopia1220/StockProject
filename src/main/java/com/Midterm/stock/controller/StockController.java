package com.Midterm.stock.controller;

import com.Midterm.stock.dto.AiPredictionDto;
import com.Midterm.stock.dto.StockChartDto;
import com.Midterm.stock.dto.StockResponseDto;
import com.Midterm.stock.service.stock.ExchangeService;
import com.Midterm.stock.service.stock.StockAiService;
import com.Midterm.stock.service.stock.StockPriceService;
import com.Midterm.stock.service.stock.StockSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class StockController {

    private final StockPriceService stockPriceService;
    private final StockSearchService stockSearchService;
    private final ExchangeService exchangeService;
    private final StockAiService stockAiService;

    @GetMapping("/api/stock/{code}")
    @ResponseBody
    public StockResponseDto getStockApi(@PathVariable String code) {
        return stockPriceService.getCurrentPrice(code);
    }

    @GetMapping("/api/stock/{code}/chart")
    @ResponseBody
    public StockChartDto getChartApi(@PathVariable String code) {
        return stockPriceService.getDailyPrice(code);
    }

    @GetMapping("/api/stock/{code}/time")
    @ResponseBody
    public StockChartDto getTimeChart(@PathVariable String code) {
        if (!isMarketOpen()) {
            return stockPriceService.getDailyPrice(code);
        }
        return stockPriceService.getTimePrice(code);
    }

    @GetMapping("/api/stock/{code}/minute")
    @ResponseBody
    public StockChartDto getMinuteChart(@PathVariable String code) {
        if (!isMarketOpen()) {
            return stockPriceService.getDailyPrice(code);
        }
        return stockPriceService.getMinutePrice(code);
    }

    @GetMapping("/api/stock/search")
    @ResponseBody
    public List<Map<String, String>> searchStock(@RequestParam String keyword) {
        try {
            return stockSearchService.searchStock(keyword);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @GetMapping("/api/stock/top-fluctuation")
    @ResponseBody
    public List<StockResponseDto> getTopFluctuation() {
        return stockPriceService.getTopFluctuation();
    }

    @GetMapping("/api/stock/top-trade")
    @ResponseBody
    public List<StockResponseDto> getTopTrade() {
        List<StockResponseDto> result = stockPriceService.getTopByTradeAmount();
        if (result == null || result.isEmpty()) {
            return stockPriceService.getTopFluctuation();
        }
        return result;
    }

    @GetMapping("/api/stock/top-fluctuation-full")
    @ResponseBody
    public List<StockResponseDto> getTopFluctuationFull() {
        return stockPriceService.getTopFluctuation();
    }

    @GetMapping("/api/stock/refresh")
    @ResponseBody
    public String refreshStocks() {
        try {
            stockSearchService.refreshStockListToDB();
            return "DB 갱신 완료";
        } catch (Exception e) {
            return "오류: " + e.getMessage();
        }
    }

    @GetMapping("/api/stock/{code}/ai")
    @ResponseBody
    public AiPredictionDto getAiPrediction(@PathVariable String code) {
        return stockAiService.predict(code);
    }

    @GetMapping("/api/kospi")
    @ResponseBody
    public StockResponseDto getKospiApi() {
        return stockPriceService.getKospiIndex();
    }

    @GetMapping("/api/kospi/chart")
    @ResponseBody
    public StockChartDto getKospiChartApi() {
        return stockPriceService.getKospiChart();
    }

    @GetMapping("/api/kospi/time")
    @ResponseBody
    public StockChartDto getKospiTimeChartApi() {
        if (!isMarketOpen()) {
            return stockPriceService.getKospiChart();
        }
        return stockPriceService.getKospiTimeChart();
    }

    @GetMapping("/api/kospi/minute")
    @ResponseBody
    public StockChartDto getKospiMinuteChartApi() {
        if (!isMarketOpen()) {
            return stockPriceService.getKospiChart();
        }
        return stockPriceService.getKospiMinuteChart();
    }

    @GetMapping("/api/kosdaq")
    @ResponseBody
    public StockResponseDto getKosdaqApi() {
        return stockPriceService.getKosdaqIndex();
    }

    @GetMapping("/api/kosdaq/chart")
    @ResponseBody
    public StockChartDto getKosdaqChartApi() {
        return stockPriceService.getKosdaqChart();
    }

    @GetMapping("/api/kosdaq/time")
    @ResponseBody
    public StockChartDto getKosdaqTimeChartApi() {
        if (!isMarketOpen()) {
            return stockPriceService.getKosdaqChart();
        }
        return stockPriceService.getKosdaqTimeChart();
    }

    @GetMapping("/api/kosdaq/minute")
    @ResponseBody
    public StockChartDto getKosdaqMinuteChartApi() {
        if (!isMarketOpen()) {
            return stockPriceService.getKosdaqChart();
        }
        return stockPriceService.getKosdaqMinuteChart();
    }

    @GetMapping("/api/exchange")
    @ResponseBody
    public StockResponseDto getExchangeApi(@RequestParam(defaultValue = "USD") String currency) {
        return exchangeService.getExchangeRate(currency);
    }

    @GetMapping("/api/exchange/chart")
    @ResponseBody
    public StockChartDto getExchangeChartApi(@RequestParam(defaultValue = "USD") String currency) {
        return exchangeService.getExchangeChart(currency);
    }

    private boolean isMarketOpen() {
        LocalDateTime now = LocalDateTime.now();
        int day = now.getDayOfWeek().getValue();
        if (day >= 6) {
            return false;
        }

        int time = now.getHour() * 100 + now.getMinute();
        return time >= 900 && time <= 1530;
    }
}
