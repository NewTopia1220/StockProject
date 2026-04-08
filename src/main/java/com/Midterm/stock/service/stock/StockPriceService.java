package com.Midterm.stock.service.stock;

import com.Midterm.stock.dto.StockChartDto;
import com.Midterm.stock.dto.StockResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 주식 시세/지수/순위 서비스
 * - 종목 현재가, 일별/시간별/분별 차트
 * - 코스피/코스닥 지수
 * - 등락률 상위 종목
 */
@Service
@RequiredArgsConstructor
public class StockPriceService {

    private final KisApiService kisApi;

    // ── 종목 시세 ────────────────────────────────────────────

    /** 현재가 조회 (현재가, 시가, 고가, 저가, 거래량) */
    public StockResponseDto getCurrentPrice(String stockCode) {
        kisApi.issueToken();
        StockResponseDto dto = new StockResponseDto();
        dto.setStockName(stockCode);
        dto.setCurrentPrice("0"); dto.setOpenPrice("0");
        dto.setHighPrice("0"); dto.setLowPrice("0"); dto.setVolume("0");
        try {
            JsonNode response = kisApi.get(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                    .queryParam("FID_INPUT_ISCD", stockCode)
                    .build(), "FHKST01010100");
            if (response == null || response.get("output") == null) return dto;
            JsonNode o = response.get("output");
            dto.setCurrentPrice(o.get("stck_prpr").asText());
            dto.setOpenPrice(o.get("stck_oprc").asText());
            dto.setHighPrice(o.get("stck_hgpr").asText());
            dto.setLowPrice(o.get("stck_lwpr").asText());
            dto.setVolume(o.get("acml_vol").asText());
        } catch (Exception e) {
            System.out.println("현재가 조회 실패 [" + stockCode + "]: " + e.getMessage());
        }
        return dto;
    }

    /** 일별 시세 차트 (최근 30일) */
    public StockChartDto getDailyPrice(String stockCode) {
        kisApi.issueToken();
        try {
            JsonNode response = kisApi.get(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-daily-price")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                    .queryParam("FID_INPUT_ISCD", stockCode)
                    .queryParam("FID_PERIOD_DIV_CODE", "D")
                    .queryParam("FID_ORG_ADJ_PRC", "0")
                    .build(), "FHKST01010400");
            if (response == null || response.get("output") == null) return emptyChart();
            return parseChartFromArray(response.get("output"), "stck_bsop_date", "stck_clpr", "acml_vol");
        } catch (Exception e) {
            System.out.println("일별시세 실패 [" + stockCode + "]: " + e.getMessage());
            return emptyChart();
        }
    }

    /** 시간별 차트 (장중 전용) */
    public StockChartDto getTimePrice(String stockCode) {
        kisApi.issueToken();
        try {
            String now = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
            JsonNode response = kisApi.get(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice")
                    .queryParam("FID_ETC_CLS_CODE", "")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                    .queryParam("FID_INPUT_ISCD", stockCode)
                    .queryParam("FID_INPUT_HOUR_1", now)
                    .queryParam("FID_PW_DATA_INCU_YN", "Y")
                    .build(), "FHKST03010200");
            if (response == null || response.get("output2") == null) return emptyChart();
            return parseTimeChart(response.get("output2"));
        } catch (Exception e) {
            System.out.println("시간별 차트 실패 [" + stockCode + "]: " + e.getMessage());
            return emptyChart();
        }
    }

    /** 분별 차트 (장중 전용) */
    public StockChartDto getMinutePrice(String stockCode) {
        kisApi.issueToken();
        try {
            String now = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
            JsonNode response = kisApi.get(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice")
                    .queryParam("FID_ETC_CLS_CODE", "")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                    .queryParam("FID_INPUT_ISCD", stockCode)
                    .queryParam("FID_INPUT_HOUR_1", now)
                    .queryParam("FID_PW_DATA_INCU_YN", "N")
                    .build(), "FHKST03010200");
            if (response == null || response.get("output2") == null) return emptyChart();
            return parseTimeChart(response.get("output2"));
        } catch (Exception e) {
            System.out.println("분별 차트 실패 [" + stockCode + "]: " + e.getMessage());
            return emptyChart();
        }
    }

    // ── 지수 ─────────────────────────────────────────────────

    /** 코스피 현재 지수 */
    public StockResponseDto getKospiIndex() {
        kisApi.issueToken();
        StockResponseDto dto = new StockResponseDto();
        dto.setCurrentPrice("0"); dto.setChangeRate("0");
        dto.setPriceChange("0"); dto.setVolume("0");
        try {
            JsonNode response = kisApi.get(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-index-price")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "U")
                    .queryParam("FID_INPUT_ISCD", "0001")
                    .build(), "FHPUP02100000");
            if (response == null || response.get("output") == null) return dto;
            JsonNode o = response.get("output");
            dto.setCurrentPrice(o.get("bstp_nmix_prpr").asText());
            dto.setChangeRate(o.get("bstp_nmix_prdy_ctrt").asText());
            dto.setPriceChange(o.get("bstp_nmix_prdy_vrss").asText());
            dto.setVolume(o.get("acml_vol").asText());
        } catch (Exception e) {
            System.out.println("코스피 조회 실패: " + e.getMessage());
        }
        return dto;
    }

    /** 코스피 일별 차트 (최근 30일) */
    public StockChartDto getKospiChart() {
        kisApi.issueToken();
        try {
            String toDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String fromDate = LocalDate.now().minusDays(30).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            JsonNode response = kisApi.get(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-index-daily-price")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "U")
                    .queryParam("FID_INPUT_ISCD", "0001")
                    .queryParam("FID_PERIOD_DIV_CODE", "D")
                    .queryParam("FID_INPUT_DATE_1", fromDate)
                    .queryParam("FID_INPUT_DATE_2", toDate)
                    .build(), "FHPUP02120000");
            if (response == null || response.get("output2") == null) return emptyChart();
            return parseChartFromArray(response.get("output2"), "stck_bsop_date", "bstp_nmix_prpr", "acml_vol");
        } catch (Exception e) {
            System.out.println("코스피 차트 실패: " + e.getMessage());
            return emptyChart();
        }
    }

    /** 코스닥 현재 지수 */
    public StockResponseDto getKosdaqIndex() {
        kisApi.issueToken();
        StockResponseDto dto = new StockResponseDto();
        dto.setCurrentPrice("0"); dto.setPriceChange("0"); dto.setChangeRate("0");
        try {
            JsonNode response = kisApi.get(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-index-price")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "U")
                    .queryParam("FID_INPUT_ISCD", "0002")
                    .build(), "FHPUP02100000");
            if (response == null || response.get("output") == null) return dto;
            JsonNode o = response.get("output");
            dto.setCurrentPrice(o.get("bstp_nmix_prpr").asText());
            dto.setPriceChange(o.get("bstp_nmix_prdy_vrss").asText());
            dto.setChangeRate(o.get("bstp_nmix_prdy_ctrt").asText());
        } catch (Exception e) {
            System.out.println("코스닥 조회 실패: " + e.getMessage());
        }
        return dto;
    }

    // ── 순위 ─────────────────────────────────────────────────

    /** 등락률 상위 10개 종목 (거래량 10만 이상) */
    public List<StockResponseDto> getTopFluctuation() {
        kisApi.issueToken();
        List<StockResponseDto> result = new ArrayList<>();
        try {
            JsonNode response = kisApi.get(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/ranking/fluctuation")
                    .queryParam("fid_rsfl_rate2", "")
                    .queryParam("fid_cond_mrkt_div_code", "J")
                    .queryParam("fid_cond_scr_div_code", "20170")
                    .queryParam("fid_input_iscd", "0000")
                    .queryParam("fid_rank_sort_cls_code", "0")
                    .queryParam("fid_input_cnt_1", "10")
                    .queryParam("fid_prc_cls_code", "0")
                    .queryParam("fid_input_price_1", "0")
                    .queryParam("fid_input_price_2", "1000000")
                    .queryParam("fid_vol_cnt", "100000")
                    .queryParam("fid_trgt_cls_code", "0")
                    .queryParam("fid_trgt_exls_cls_code", "0")
                    .queryParam("fid_div_cls_code", "0")
                    .queryParam("fid_rsfl_rate1", "0")
                    .build(), "FHPST01700000");
            if (response == null || response.get("output") == null) return result;
            for (JsonNode item : response.get("output")) {
                StockResponseDto dto = new StockResponseDto();
                dto.setStockName(item.get("hts_kor_isnm").asText());
                dto.setCurrentPrice(item.get("stck_prpr").asText());
                dto.setChangeRate(item.get("prdy_ctrt").asText());
                dto.setPriceChange(item.get("prdy_vrss").asText());
                result.add(dto);
            }
        } catch (Exception e) {
            System.out.println("등락률 순위 조회 실패: " + e.getMessage());
        }
        return result;
    }

    // ── 차트 파싱 헬퍼 ───────────────────────────────────────

    /** 일별/지수 차트 파싱 (최신→과거 순서를 과거→최신으로 reverse) */
    private StockChartDto parseChartFromArray(JsonNode array, String dateField, String priceField, String volField) {
        List<String> labels = new ArrayList<>();
        List<String> prices = new ArrayList<>();
        List<String> volumes = new ArrayList<>();
        for (JsonNode item : array) {
            labels.add(item.get(dateField).asText());
            prices.add(item.get(priceField).asText());
            volumes.add(item.get(volField).asText());
        }
        Collections.reverse(labels);
        Collections.reverse(prices);
        Collections.reverse(volumes);
        StockChartDto dto = new StockChartDto();
        dto.setLabels(labels); dto.setClosePrices(prices); dto.setVolumes(volumes);
        return dto;
    }

    /** 시간별/분별 차트 파싱 (HHmmss → HH:mm 변환) */
    private StockChartDto parseTimeChart(JsonNode array) {
        List<String> labels = new ArrayList<>();
        List<String> prices = new ArrayList<>();
        List<String> volumes = new ArrayList<>();
        for (JsonNode item : array) {
            String raw = item.get("stck_cntg_hour").asText();
            labels.add(raw.substring(0, 2) + ":" + raw.substring(2, 4));
            prices.add(item.get("stck_prpr").asText());
            volumes.add(item.get("cntg_vol").asText());
        }
        Collections.reverse(labels);
        Collections.reverse(prices);
        Collections.reverse(volumes);
        StockChartDto dto = new StockChartDto();
        dto.setLabels(labels); dto.setClosePrices(prices); dto.setVolumes(volumes);
        return dto;
    }

    /** 빈 차트 DTO (API 실패 시 Fallback) */
    private StockChartDto emptyChart() {
        StockChartDto dto = new StockChartDto();
        dto.setLabels(new ArrayList<>());
        dto.setClosePrices(new ArrayList<>());
        dto.setVolumes(new ArrayList<>());
        return dto;
    }
}