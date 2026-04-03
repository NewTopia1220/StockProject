package com.Midterm.stock.service;

import com.Midterm.stock.dto.StockChartDto;
import com.Midterm.stock.dto.StockResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class StockService {

    @Value("${kis.api.app-key}")
    private String appKey;

    @Value("${kis.api.app-secret}")
    private String appSecret;

    @Value("${kis.api.base-url}")
    private String baseUrl;

    private String accessToken;

    // 1. 토큰 발급
    public void issueToken() {
        WebClient client = WebClient.create(baseUrl);

        String body = String.format(
                "{\"grant_type\":\"client_credentials\",\"appkey\":\"%s\",\"appsecret\":\"%s\"}",
                appKey, appSecret
        );

        JsonNode response = client.post()
                .uri("/oauth2/tokenP")
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        this.accessToken = response.get("access_token").asText();
    }

    // 2. 현재가 조회
    public StockResponseDto getCurrentPrice(String stockCode) {
        if (accessToken == null) issueToken();

        WebClient client = WebClient.create(baseUrl);

        JsonNode response = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .build())
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHKST01010100")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        JsonNode output = response.get("output");

//        System.out.println("=== API 응답 전체 ===");
//        System.out.println(response.toPrettyString());
//        System.out.println("=== output ===");
//        System.out.println(output.toPrettyString());

        StockResponseDto dto = new StockResponseDto();
        // 기존 - 이 필드가 응답에 없어서 null 터짐
        // dto.setStockName(output.get("hts_kor_isnm").asText());
        // 수정 - 종목코드로 대체 (종목명은 output에 없음)
        // dto.setStockName(output.get("stck_shrn_iscd").asText());
        dto.setStockName(stockCode); // 임시로 코드로 표시
        dto.setCurrentPrice(output.get("stck_prpr").asText());
        dto.setOpenPrice(output.get("stck_oprc").asText());
        dto.setHighPrice(output.get("stck_hgpr").asText());
        dto.setLowPrice(output.get("stck_lwpr").asText());
        dto.setVolume(output.get("acml_vol").asText());
        return dto;
    }

    // 3. 일별 시세 조회 (차트용)
    public StockChartDto getDailyPrice(String stockCode) {
        if (accessToken == null) issueToken();

        WebClient client = WebClient.create(baseUrl);

        JsonNode response = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-daily-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .queryParam("FID_PERIOD_DIV_CODE", "D")
                        .queryParam("FID_ORG_ADJ_PRC", "0")
                        .build())
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHKST01010400")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        List<String> labels = new ArrayList<>();
        List<String> closePrices = new ArrayList<>();
        List<String> volumes = new ArrayList<>();

        for (JsonNode item : response.get("output")) {
            labels.add(item.get("stck_bsop_date").asText());
            closePrices.add(item.get("stck_clpr").asText());
            volumes.add(item.get("acml_vol").asText());
        }

        // 날짜 오래된순으로 정렬 (차트가 왼→오른쪽 시간순)
        java.util.Collections.reverse(labels);
        java.util.Collections.reverse(closePrices);
        java.util.Collections.reverse(volumes);

        StockChartDto dto = new StockChartDto();
        dto.setLabels(labels);
        dto.setClosePrices(closePrices);
        dto.setVolumes(volumes);


//        System.out.println("=== 일별시세 응답 전체 ===");
//        System.out.println(response.toPrettyString());

        return dto;
    }

    // 코스피 조회 메서드
    public StockResponseDto getKospiIndex() {
        if (accessToken == null) issueToken();

        WebClient client = WebClient.create(baseUrl);

        JsonNode response = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-index-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "U")
                        .queryParam("FID_INPUT_ISCD", "0001") // 0001 = 코스피
                        .build())
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHPUP02100000")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

//        System.out.println("=== 코스피 응답 ===");
//        System.out.println(response.toPrettyString());

        JsonNode output = response.get("output");

        StockResponseDto dto = new StockResponseDto();
        dto.setCurrentPrice(output.get("bstp_nmix_prpr").asText());
        dto.setChangeRate(output.get("bstp_nmix_prdy_ctrt").asText());
        dto.setPriceChange(output.get("bstp_nmix_prdy_vrss").asText());
        dto.setVolume(output.get("acml_vol").asText());

//        System.out.println("=== 코스피 응답 전체 ===");
//        System.out.println(response.toPrettyString());

         return dto;
    }

    // 코스피 차트
    public StockChartDto getKospiChart() {
        if (accessToken == null) issueToken();

        WebClient client = WebClient.create(baseUrl);

        String toDate = java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fromDate = java.time.LocalDate.now().minusDays(30)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));

        JsonNode response = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-index-daily-price")
                        .queryParam("FID_COND_MRKT_DIV_CODE", "U")
                        .queryParam("FID_INPUT_ISCD", "0001")
                        .queryParam("FID_PERIOD_DIV_CODE", "D")
                        .queryParam("FID_INPUT_DATE_1", fromDate)
                        .queryParam("FID_INPUT_DATE_2", toDate)
                        .build())
                .header("authorization", "Bearer " + accessToken)
                .header("appkey", appKey)
                .header("appsecret", appSecret)
                .header("tr_id", "FHPUP02120000")
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        List<String> labels = new ArrayList<>();
        List<String> closePrices = new ArrayList<>();
        List<String> volumes = new ArrayList<>();

        for (JsonNode item : response.get("output2")) {
            labels.add(item.get("stck_bsop_date").asText());
            closePrices.add(item.get("bstp_nmix_prpr").asText());  // 코스피 지수
            volumes.add(item.get("acml_vol").asText());
        }

        Collections.reverse(labels);
        Collections.reverse(closePrices);
        Collections.reverse(volumes);

        StockChartDto dto = new StockChartDto();
        dto.setLabels(labels);
        dto.setClosePrices(closePrices);
        dto.setVolumes(volumes);
        return dto;
    }
}