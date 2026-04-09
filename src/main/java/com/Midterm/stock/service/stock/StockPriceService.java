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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    // ── TTL 캐시 ─────────────────────────────────────────────
    // 현재가: 5초, 순위/지수: 30초 캐싱 → KIS API 호출 횟수 대폭 감소
    private static final long PRICE_TTL_MS  = 5_000L;   // 5초
    private static final long RANK_TTL_MS   = 30_000L;  // 30초
    private static final long INDEX_TTL_MS  = 30_000L;  // 30초

    private record CacheEntry<T>(T data, long expireMs) {
        boolean isExpired() { return System.currentTimeMillis() > expireMs; }
    }

    private final Map<String, CacheEntry<StockResponseDto>>       priceCache       = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<List<StockResponseDto>>> rankCache        = new ConcurrentHashMap<>();
    private volatile CacheEntry<StockResponseDto>                 kospiCache       = null;
    private volatile CacheEntry<StockResponseDto>                 kosdaqCache      = null;

    // ── 종목 시세 ────────────────────────────────────────────

    /** 현재가 조회 (5초 캐싱) */
    public StockResponseDto getCurrentPrice(String stockCode) {
        CacheEntry<StockResponseDto> cached = priceCache.get(stockCode);
        if (cached != null && !cached.isExpired()) return cached.data();

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
            dto.setChangeRate(o.path("prdy_ctrt").asText("0"));
            String vrssSign = o.path("prdy_vrss_sign").asText("3");
            String vrss     = o.path("prdy_vrss").asText("0");
            if (vrss.isBlank()) vrss = "0";
            boolean negative = vrssSign.equals("4") || vrssSign.equals("5");
            dto.setPriceChange(negative ? "-" + vrss : vrss);
        } catch (Exception e) {
            System.out.println("현재가 조회 실패 [" + stockCode + "]: " + e.getMessage());
        }
        priceCache.put(stockCode, new CacheEntry<>(dto, System.currentTimeMillis() + PRICE_TTL_MS));
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

    /** 코스피 현재 지수 (30초 캐싱) */
    public StockResponseDto getKospiIndex() {
        if (kospiCache != null && !kospiCache.isExpired()) return kospiCache.data();

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
        kospiCache = new CacheEntry<>(dto, System.currentTimeMillis() + INDEX_TTL_MS);
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

    /** 코스닥 현재 지수 (30초 캐싱) */
    public StockResponseDto getKosdaqIndex() {
        if (kosdaqCache != null && !kosdaqCache.isExpired()) return kosdaqCache.data();

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
        kosdaqCache = new CacheEntry<>(dto, System.currentTimeMillis() + INDEX_TTL_MS);
        return dto;
    }

    // ── 순위 ─────────────────────────────────────────────────

    /** 등락률 상위 20개 종목 (30초 캐싱) */
    public List<StockResponseDto> getTopFluctuation() {
        CacheEntry<List<StockResponseDto>> cached = rankCache.get("fluctuation");
        if (cached != null && !cached.isExpired()) return cached.data();

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
                    .queryParam("fid_input_cnt_1", "20")
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
                dto.setStockCode(item.get("stck_shrn_iscd").asText());
                dto.setStockName(item.get("hts_kor_isnm").asText());
                dto.setCurrentPrice(item.get("stck_prpr").asText());
                dto.setChangeRate(item.get("prdy_ctrt").asText());
                dto.setPriceChange(item.get("prdy_vrss").asText());
                dto.setVolume(item.get("acml_vol").asText());
                // 거래대금 (만원 단위)
                if (item.has("acml_tr_pbmn")) dto.setTradeAmount(item.get("acml_tr_pbmn").asText());
                result.add(dto);
            }
        } catch (Exception e) {
            System.out.println("등락률 순위 조회 실패: " + e.getMessage());
        }
        if (!result.isEmpty()) rankCache.put("fluctuation", new CacheEntry<>(result, System.currentTimeMillis() + RANK_TTL_MS));
        return result;
    }

    // ── 거래대금 순위 ─────────────────────────────────────────

    /**
     * 거래대금 상위 20개 종목 (30초 캐싱)
     * - fid_trgt_exls_cls_code: 우선주(index 3)·ETF(index 9) API 레벨 제외
     * - 서버 레벨: 이름/코드 패턴으로 우선주 추가 필터
     */
    public List<StockResponseDto> getTopByTradeAmount() {
        CacheEntry<List<StockResponseDto>> cached = rankCache.get("trade");
        if (cached != null && !cached.isExpired()) return cached.data();

        kisApi.issueToken();
        List<StockResponseDto> result = new ArrayList<>();
        try {
            JsonNode response = kisApi.get(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/volume-rank")
                    .queryParam("fid_cond_mrkt_div_code", "J")
                    .queryParam("fid_cond_scr_div_code", "20171")
                    .queryParam("fid_input_iscd", "0000")
                    .queryParam("fid_div_cls_code", "2")           // 거래대금 기준
                    .queryParam("fid_blng_cls_code", "0")
                    .queryParam("fid_trgt_cls_code", "111111111")
                    .queryParam("fid_trgt_exls_cls_code", "0001000001") // 우선주(3)·ETF(9) 제외
                    .queryParam("fid_input_price_1", "")
                    .queryParam("fid_input_price_2", "")
                    .queryParam("fid_vol_cnt", "0")
                    .queryParam("fid_input_date_1", "")
                    .build(), "FHPST01710000");

            if (response == null || response.get("output") == null) {
                System.out.println("거래대금 API 응답 없음");
                return result;
            }
            for (JsonNode item : response.get("output")) {
                StockResponseDto dto = new StockResponseDto();
                String code = item.has("mksc_shrn_iscd")
                        ? item.get("mksc_shrn_iscd").asText()
                        : item.get("stck_shrn_iscd").asText();
                dto.setStockCode(code);
                dto.setStockName(item.get("hts_kor_isnm").asText());
                dto.setCurrentPrice(item.get("stck_prpr").asText());
                dto.setChangeRate(item.get("prdy_ctrt").asText());
                dto.setPriceChange(item.get("prdy_vrss").asText());
                dto.setVolume(item.get("acml_vol").asText());
                if (item.has("acml_tr_pbmn")) dto.setTradeAmount(item.get("acml_tr_pbmn").asText());
                result.add(dto);
            }
            System.out.println("거래대금 순위 원본: " + result.size() + "개");
        } catch (Exception e) {
            System.out.println("거래대금 순위 조회 실패: " + e.getMessage());
        }

        // 서버 레벨 우선주 추가 필터
        // - 이름이 "우", "우B", "우C"로 끝나는 종목
        // - 코드 마지막 자리가 5 (한국 우선주 코드 패턴: 005935, 005385 등)
        // - 코드에 알파벳 포함 (ETF/특수 종목: 00680K 등)
        List<StockResponseDto> filtered = result.stream()
                .filter(dto -> {
                    String name = dto.getStockName() != null ? dto.getStockName() : "";
                    String code = dto.getStockCode() != null ? dto.getStockCode() : "";
                    boolean prefByName = name.endsWith("우") || name.endsWith("우B") || name.endsWith("우C");
                    boolean prefByCode = code.length() == 6 && code.charAt(5) == '5';
                    boolean isSpecial  = !code.matches("\\d{6}");
                    return !prefByName && !prefByCode && !isSpecial;
                })
                .collect(java.util.stream.Collectors.toList());

        System.out.println("거래대금 순위 (우선주 제외 후): " + filtered.size() + "개");

        // 필터 결과가 비어도 캐시에 저장 (반복 API 호출 방지)
        List<StockResponseDto> finalResult = filtered.isEmpty() ? result : filtered;
        if (!finalResult.isEmpty()) rankCache.put("trade", new CacheEntry<>(finalResult, System.currentTimeMillis() + RANK_TTL_MS));
        return finalResult;
    }

    // ── 차트 파싱 헬퍼 ───────────────────────────────────────

    /** 일별/지수 차트 파싱 (OHLCV, 최신→과거를 과거→최신으로 reverse) */
    private StockChartDto parseChartFromArray(JsonNode array, String dateField, String priceField, String volField) {
        List<String> labels = new ArrayList<>();
        List<String> opens  = new ArrayList<>();
        List<String> highs  = new ArrayList<>();
        List<String> lows   = new ArrayList<>();
        List<String> closes = new ArrayList<>();
        List<String> volumes = new ArrayList<>();
        for (JsonNode item : array) {
            labels.add(item.get(dateField).asText());
            closes.add(item.get(priceField).asText());
            volumes.add(item.get(volField).asText());
            // OHLCV (일별 주가 API에만 존재, 지수 등은 "0" 처리)
            opens.add(item.path("stck_oprc").asText("0"));
            highs.add(item.path("stck_hgpr").asText("0"));
            lows.add(item.path("stck_lwpr").asText("0"));
        }
        Collections.reverse(labels);
        Collections.reverse(opens);
        Collections.reverse(highs);
        Collections.reverse(lows);
        Collections.reverse(closes);
        Collections.reverse(volumes);
        StockChartDto dto = new StockChartDto();
        dto.setLabels(labels);
        dto.setOpenPrices(opens);
        dto.setHighPrices(highs);
        dto.setLowPrices(lows);
        dto.setClosePrices(closes);
        dto.setVolumes(volumes);
        return dto;
    }

    /** 시간별/분별 차트 파싱 (HHmmss → HH:mm 변환) */
    private StockChartDto parseTimeChart(JsonNode array) {
        List<String> labels = new ArrayList<>();
        List<String> closes = new ArrayList<>();
        List<String> volumes = new ArrayList<>();
        for (JsonNode item : array) {
            String raw = item.get("stck_cntg_hour").asText();
            labels.add(raw.substring(0, 2) + ":" + raw.substring(2, 4));
            closes.add(item.get("stck_prpr").asText());
            volumes.add(item.get("cntg_vol").asText());
        }
        Collections.reverse(labels);
        Collections.reverse(closes);
        Collections.reverse(volumes);
        StockChartDto dto = new StockChartDto();
        dto.setLabels(labels);
        dto.setClosePrices(closes);
        dto.setVolumes(volumes);
        return dto;
    }

    /** 빈 차트 DTO (API 실패 시 Fallback) */
    private StockChartDto emptyChart() {
        StockChartDto dto = new StockChartDto();
        dto.setLabels(new ArrayList<>());
        dto.setOpenPrices(new ArrayList<>());
        dto.setHighPrices(new ArrayList<>());
        dto.setLowPrices(new ArrayList<>());
        dto.setClosePrices(new ArrayList<>());
        dto.setVolumes(new ArrayList<>());
        return dto;
    }
}