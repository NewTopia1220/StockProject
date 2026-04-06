package com.Midterm.stock.service;

import com.Midterm.stock.dto.StockChartDto;
import com.Midterm.stock.dto.StockResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class StockService {

    private final String appKey;
    private final String appSecret;
    private final String baseUrl;
    private final String eximApiKey;

    private final WebClient kisClient;
    private final WebClient eximClient;
    private final WebClient krxClient;

    private String accessToken;
    private LocalDateTime tokenExpireTime;

    // 생성자 하나로 통일
    public StockService(
            @Value("${kis.api.app-key}") String appKey,
            @Value("${kis.api.app-secret}") String appSecret,
            @Value("${kis.api.base-url}") String baseUrl,
            @Value("${exim.api.key}") String eximApiKey
    ) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.baseUrl = baseUrl;
        this.eximApiKey = eximApiKey;

        this.kisClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();

        this.krxClient = WebClient.builder()
                .baseUrl("http://data.krx.co.kr")
                .defaultHeader("Content-Type", "application/x-www-form-urlencoded")
                .defaultHeader("User-Agent", "Mozilla/5.0")
                .defaultHeader("Referer", "http://data.krx.co.kr")
                .build();
        // 환율 API - SSL 무시
        try {
            io.netty.handler.ssl.SslContext sslContext = io.netty.handler.ssl.SslContextBuilder
                    .forClient()
                    .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
                    .build();

            reactor.netty.http.client.HttpClient httpClient =
                    reactor.netty.http.client.HttpClient.create()
                            .followRedirect(true)
                            .secure(t -> t.sslContext(sslContext));

            this.eximClient = WebClient.builder()
                    .baseUrl("https://www.koreaexim.go.kr")
                    .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("환율 SSL 설정 실패", e);
        }
    }

    // 토큰 발급 - synchronized로 동시 호출 방지
    public synchronized void issueToken() {
        if (accessToken != null && tokenExpireTime != null
                && LocalDateTime.now().isBefore(tokenExpireTime)) {
            return; // 유효한 토큰 있으면 스킵
        }

        System.out.println("appKey 길이: " + appKey.length());
        System.out.println("appKey 앞10자: [" + appKey.substring(0, Math.min(10, appKey.length())) + "]");
        System.out.println("baseUrl: [" + baseUrl + "]");

        System.out.println("appSecret 길이: " + appSecret.length());
        System.out.println("appSecret 앞5자: [" + appSecret.substring(0, Math.min(5, appSecret.length())) + "]");

        String body = String.format(
                "{\"grant_type\":\"client_credentials\",\"appkey\":\"%s\",\"appsecret\":\"%s\"}",
                appKey, appSecret
        );

        JsonNode response = kisClient.post()
                .uri("/oauth2/tokenP")
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response != null && response.has("access_token")) {
            this.accessToken = response.get("access_token").asText();
            this.tokenExpireTime = LocalDateTime.now().plusHours(23);
            System.out.println("토큰 발급 완료. 만료: " + tokenExpireTime);
        }
    }

    // KIS API 공통 GET 요청 헬퍼
    private JsonNode kisGet(String path, java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uriFunc, String trId) {
        return kisClient.get()
                .uri(uriFunc)
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", trId)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();
    }

    // 현재가 조회
    public StockResponseDto getCurrentPrice(String stockCode) {
        issueToken();
        JsonNode response = kisGet(
                "/uapi/domestic-stock/v1/quotations/inquire-price",
                uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .build(),
                "FHKST01010100"
        );
        JsonNode output = response.get("output");
        StockResponseDto dto = new StockResponseDto();
        dto.setStockName(stockCode);
        dto.setCurrentPrice(output.get("stck_prpr").asText());
        dto.setOpenPrice(output.get("stck_oprc").asText());
        dto.setHighPrice(output.get("stck_hgpr").asText());
        dto.setLowPrice(output.get("stck_lwpr").asText());
        dto.setVolume(output.get("acml_vol").asText());
        return dto;
    }

    // 일별 시세 조회
    public StockChartDto getDailyPrice(String stockCode) {
        issueToken();
        JsonNode response = kisGet(
                "/uapi/domestic-stock/v1/quotations/inquire-daily-price",
                uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-daily-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .queryParam("FID_PERIOD_DIV_CODE", "D")
                        .queryParam("FID_ORG_ADJ_PRC", "0")
                        .build(),
                "FHKST01010400"
        );
        return parseChartFromArray(response.get("output"), "stck_bsop_date", "stck_clpr", "acml_vol");
    }

    // 코스피 현재 지수
    public StockResponseDto getKospiIndex() {
        issueToken();
        JsonNode output = kisGet(
                "/uapi/domestic-stock/v1/quotations/inquire-index-price",
                uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-index-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "U")
                        .queryParam("FID_INPUT_ISCD", "0001")
                        .build(),
                "FHPUP02100000"
        ).get("output");
        StockResponseDto dto = new StockResponseDto();
        dto.setCurrentPrice(output.get("bstp_nmix_prpr").asText());
        dto.setChangeRate(output.get("bstp_nmix_prdy_ctrt").asText());
        dto.setPriceChange(output.get("bstp_nmix_prdy_vrss").asText());
        dto.setVolume(output.get("acml_vol").asText());
        return dto;
    }

    // 코스피 차트
    public StockChartDto getKospiChart() {
        issueToken();
        String toDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fromDate = LocalDate.now().minusDays(30).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        JsonNode response = kisGet(
                "/uapi/domestic-stock/v1/quotations/inquire-index-daily-price",
                uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-index-daily-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "U")
                        .queryParam("FID_INPUT_ISCD", "0001")
                        .queryParam("FID_PERIOD_DIV_CODE", "D")
                        .queryParam("FID_INPUT_DATE_1", fromDate)
                        .queryParam("FID_INPUT_DATE_2", toDate)
                        .build(),
                "FHPUP02120000"
        );
        return parseChartFromArray(response.get("output2"), "stck_bsop_date", "bstp_nmix_prpr", "acml_vol");
    }

    // 코스닥 현재 지수
    public StockResponseDto getKosdaqIndex() {
        issueToken();
        JsonNode output = kisGet(
                "/uapi/domestic-stock/v1/quotations/inquire-index-price",
                uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-index-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "U")
                        .queryParam("FID_INPUT_ISCD", "0002")
                        .build(),
                "FHPUP02100000"
        ).get("output");
        StockResponseDto dto = new StockResponseDto();
        dto.setCurrentPrice(output.get("bstp_nmix_prpr").asText());
        dto.setPriceChange(output.get("bstp_nmix_prdy_vrss").asText());
        dto.setChangeRate(output.get("bstp_nmix_prdy_ctrt").asText());
        return dto;
    }

    // 환율 조회
    public StockResponseDto getExchangeRate(String currency) {
        LocalDate date = LocalDate.now().minusDays(1);
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY) date = date.minusDays(1);
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) date = date.minusDays(2);
        String searchDate = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        StockResponseDto dto = new StockResponseDto();
        dto.setCurrentPrice("0");
        dto.setPriceChange("0");
        dto.setChangeRate("0");

        try {
            String rawResponse = eximClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/site/program/financial/exchangeJSON")
                            .queryParam("authkey", eximApiKey)
                            .queryParam("searchdate", searchDate)
                            .queryParam("data", "AP01")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (rawResponse == null || rawResponse.isBlank()) return dto;

            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode response = mapper.readTree(rawResponse);

            for (JsonNode item : response) {
                if (currency.equals(item.get("cur_unit").asText())) {
                    dto.setCurrentPrice(item.get("deal_bas_r").asText());
                    dto.setPriceChange(item.get("yy_efee_r").asText());
                    dto.setChangeRate(item.get("yy_efee_r").asText());
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("환율 API 오류: " + e.getMessage());
        }
        return dto;
    }

    // 시간별 차트
    public StockChartDto getTimePrice(String stockCode) {
        issueToken();
        String now = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        JsonNode response = kisGet(
                "/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice",
                uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice")
                        .queryParam("FID_ETC_CLS_CODE", "")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .queryParam("FID_INPUT_HOUR_1", now)
                        .queryParam("FID_PW_DATA_INCU_YN", "Y")
                        .build(),
                "FHKST03010200"
        );
        return parseTimeChart(response.get("output2"));
    }

    // 분별 차트
    public StockChartDto getMinutePrice(String stockCode) {
        issueToken();
        String now = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
        JsonNode response = kisGet(
                "/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice",
                uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice")
                        .queryParam("FID_ETC_CLS_CODE", "")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .queryParam("FID_INPUT_HOUR_1", now)
                        .queryParam("FID_PW_DATA_INCU_YN", "N")
                        .build(),
                "FHKST03010200"
        );
        return parseTimeChart(response.get("output2"));
    }

    // 공통 차트 파싱 (일별/코스피 등)
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
        dto.setLabels(labels);
        dto.setClosePrices(prices);
        dto.setVolumes(volumes);
        return dto;
    }

    // 시간별/분별 차트 파싱
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
        dto.setLabels(labels);
        dto.setClosePrices(prices);
        dto.setVolumes(volumes);
        return dto;
    }

    // 종목명으로 종목코드 검색

    // 캐시
    private List<Map<String, String>> stockListCache = null;
    private LocalDate stockListCacheDate = null;

    // KRX 전체 종목 목록
    public List<Map<String, String>> getKrxStockList() {
        List<Map<String, String>> result = new ArrayList<>();
        result.addAll(fetchKrxMarket("STK")); // 유가증권(코스피)
        result.addAll(fetchKrxMarket("KSQ")); // 코스닥
        return result;
    }

    private List<Map<String, String>> fetchKrxMarket(String marketCode) {
        try {
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            // form 데이터 방식으로 변경
            String formData = "bld=dbms%2FMDC%2FSTAT%2Fstandard%2FMDCSTAT01901"
                    + "&mktId=" + marketCode
                    + "&trdDd=" + today
                    + "&share=1&money=1&csvxls_isNo=false";

            System.out.println("KRX 요청 formData: " + formData);

            String rawResponse = krxClient.post()
                    .uri("/comm/bldAttendant/getJsonData.cmd")
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("Accept", "application/json, text/javascript, */*; q=0.01")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .bodyValue(formData)
                    .retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), res ->
                            res.bodyToMono(String.class).doOnNext(body ->
                                    System.out.println("KRX 오류 응답 body: " + body)
                            ).then(reactor.core.publisher.Mono.error(
                                    new RuntimeException("KRX 오류: " + res.statusCode())))
                    )
                    .bodyToMono(String.class)
                    .block();

            if (rawResponse == null) return new ArrayList<>();

            System.out.println("KRX 응답 앞100자: " + rawResponse.substring(0, Math.min(100, rawResponse.length())));

            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            JsonNode root = mapper.readTree(rawResponse);
            JsonNode outBlock = root.get("OutBlock_1");

            List<Map<String, String>> list = new ArrayList<>();
            if (outBlock != null && outBlock.isArray()) {
                for (JsonNode item : outBlock) {
                    Map<String, String> stock = new java.util.HashMap<>();
                    stock.put("code", item.get("ISU_SRT_CD").asText());
                    stock.put("name", item.get("ISU_ABBRV").asText());
                    stock.put("fullName", item.get("ISU_NM").asText());
                    stock.put("market", item.get("MKT_TP_NM").asText());
                    stock.put("type", item.get("SECUGRP_NM").asText());
                    list.add(stock);
                }
            }
            System.out.println("KRX [" + marketCode + "] 종목 수: " + list.size());
            return list;

        } catch (Exception e) {
            System.out.println("KRX [" + marketCode + "] 오류: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // 검색 - 하루 1회 캐시
    public List<Map<String, String>> searchStock(String keyword) {
        if (stockListCache == null || !LocalDate.now().equals(stockListCacheDate)) {
            System.out.println("KRX 종목 목록 갱신 중...");
            stockListCache = getKrxStockList();
            stockListCacheDate = LocalDate.now();
            System.out.println("KRX 종목 갱신 완료: " + stockListCache.size() + "개");
        }

        if (keyword == null || keyword.isBlank()) return List.of();

        return stockListCache.stream()
                .filter(s -> s.get("name").contains(keyword)
                        || s.get("fullName").contains(keyword)
                        || s.get("code").contains(keyword))
                .limit(10)
                .collect(java.util.stream.Collectors.toList());
    }
}

//KRX 종목 목록 갱신 중...
//KRX 요청 formData: bld=dbms%2FMDC%2FSTAT%2Fstandard%2FMDCSTAT01901&mktId=STK&trdDd=20260406&share=1&money=1&csvxls_isNo=false
//KRX 오류 응답 body: LOGOUT
//KRX [STK] 오류: KRX 오류: 400 BAD_REQUEST
//KRX 요청 formData: bld=dbms%2FMDC%2FSTAT%2Fstandard%2FMDCSTAT01901&mktId=KSQ&trdDd=20260406&share=1&money=1&csvxls_isNo=false
//KRX 오류 응답 body: LOGOUT
//KRX [KSQ] 오류: KRX 오류: 400 BAD_REQUEST
//KRX 종목 갱신 완료: 0개