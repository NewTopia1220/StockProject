package com.Midterm.stock.controller;

import com.Midterm.stock.dto.StockChartDto;
import com.Midterm.stock.dto.StockResponseDto;
import com.Midterm.stock.service.StockService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 주식 REST API 컨트롤러
 * - 프론트(stock.js)에서 AJAX로 호출하는 API 엔드포인트 모음
 * - 모든 응답은 @ResponseBody (JSON)
 */
@Controller
@RequiredArgsConstructor
public class StockController {

    private final StockService stockService;

    // ── 종목 시세 API ────────────────────────────────────────

    /**
     * 특정 종목 현재가 조회
     * GET /api/stock/{code}
     * ex) /api/stock/005930 → 삼성전자 현재가
     */
    @GetMapping("/api/stock/{code}")
    @ResponseBody
    public StockResponseDto getStockApi(@PathVariable String code) {
        return stockService.getCurrentPrice(code);
    }

    /**
     * 특정 종목 일별 차트 데이터 조회
     * GET /api/stock/{code}/chart
     */
    @GetMapping("/api/stock/{code}/chart")
    @ResponseBody
    public StockChartDto getChartApi(@PathVariable String code) {
        return stockService.getDailyPrice(code);
    }

    /**
     * 특정 종목 시간별 차트 조회 (장 중에만 유효)
     * GET /api/stock/{code}/time
     * - 장 외 시간이면 일별 차트로 대체
     */
    @GetMapping("/api/stock/{code}/time")
    @ResponseBody
    public StockChartDto getTimeChart(@PathVariable String code) {
        if (!isMarketOpen()) {
            return stockService.getDailyPrice(code);
        }
        return stockService.getTimePrice(code);
    }

    /**
     * 특정 종목 분별 차트 조회 (장 중에만 유효)
     * GET /api/stock/{code}/minute
     * - 장 외 시간이면 일별 차트로 대체
     */
    @GetMapping("/api/stock/{code}/minute")
    @ResponseBody
    public StockChartDto getMinuteChart(@PathVariable String code) {
        if (!isMarketOpen()) {
            return stockService.getDailyPrice(code);
        }
        return stockService.getMinutePrice(code);
    }

    /**
     * 종목명 또는 코드로 종목 검색 (자동완성용)
     * GET /api/stock/search?keyword=삼성
     */
    @GetMapping("/api/stock/search")
    @ResponseBody
    public List<Map<String, String>> searchStock(@RequestParam String keyword) {
        try {
            return stockService.searchStock(keyword);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 등락률 상위 10개 종목 조회
     * GET /api/stock/top-fluctuation
     * - 티커 스크롤, 종목 리스트에 사용
     */
    @GetMapping("/api/stock/top-fluctuation")
    @ResponseBody
    public List<StockResponseDto> getTopFluctuation() {
        return stockService.getTopFluctuation();
    }

    /**
     * 종목 DB 수동 갱신 (관리용)
     * GET /api/stock/refresh
     * - Python 스크립트 실행 → DB 저장
     */
    @GetMapping("/api/stock/refresh")
    @ResponseBody
    public String refreshStocks() {
        try {
            stockService.refreshStockListToDB();
            return "DB 갱신 완료";
        } catch (Exception e) {
            return "오류: " + e.getMessage();
        }
    }

    // ── 지수 API ─────────────────────────────────────────────

    /**
     * 코스피 현재 지수 조회
     * GET /api/kospi
     */
    @GetMapping("/api/kospi")
    @ResponseBody
    public StockResponseDto getKospiApi() {
        return stockService.getKospiIndex();
    }

    /**
     * 코스피 일별 차트 데이터 조회
     * GET /api/kospi/chart
     */
    @GetMapping("/api/kospi/chart")
    @ResponseBody
    public StockChartDto getKospiChartApi() {
        return stockService.getKospiChart();
    }

    /**
     * 코스닥 현재 지수 조회
     * GET /api/kosdaq
     */
    @GetMapping("/api/kosdaq")
    @ResponseBody
    public StockResponseDto getKosdaqApi() {
        return stockService.getKosdaqIndex();
    }

    // ── 환율 API ─────────────────────────────────────────────

    /**
     * 환율 조회
     * GET /api/exchange?currency=USD
     *
     * @param currency 통화코드 (기본값: USD), 옵션: JPY(100), EUR, CNH, GBP
     */
    @GetMapping("/api/exchange")
    @ResponseBody
    public StockResponseDto getExchangeApi(
            @RequestParam(defaultValue = "USD") String currency) {
        return stockService.getExchangeRate(currency);
    }

    // ── 헬퍼 메서드 ──────────────────────────────────────────

    /**
     * 현재 장 운영 시간 여부 확인
     * - 평일 09:00 ~ 15:30 만 true
     */
    private boolean isMarketOpen() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        int day = now.getDayOfWeek().getValue(); // 1=월 ~ 7=일
        if (day >= 6) return false;              // 토(6), 일(7) 제외
        int time = now.getHour() * 100 + now.getMinute();
        return time >= 900 && time <= 1530;
    }
}
