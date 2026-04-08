package com.Midterm.stock.service.stock;

import com.Midterm.stock.dto.StockResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 환율 서비스 (ExchangeRate-API 사용)
 * - 한국수출입은행 API SSL/리다이렉트 문제로 대체
 * - JPY는 100엔 기준 변환
 */
@Service
public class ExchangeService {

    private final WebClient erClient;

    public ExchangeService() {
        try {
            io.netty.handler.ssl.SslContext sslContext =
                    io.netty.handler.ssl.SslContextBuilder.forClient()
                            .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
                            .build();

            reactor.netty.http.client.HttpClient httpClient =
                    reactor.netty.http.client.HttpClient.create()
                            .secure(t -> t.sslContext(sslContext))
                            .responseTimeout(java.time.Duration.ofSeconds(10));

            this.erClient = WebClient.builder()
                    .baseUrl("https://open.er-api.com")
                    .clientConnector(new org.springframework.http.client.reactive
                            .ReactorClientHttpConnector(httpClient))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("환율 WebClient 초기화 실패", e);
        }
    }

    /**
     * 환율 조회
     * @param currency 통화코드 (USD, JPY(100), EUR, CNH, GBP)
     */
    public StockResponseDto getExchangeRate(String currency) {
        StockResponseDto dto = new StockResponseDto();
        dto.setCurrentPrice("0"); dto.setPriceChange("0"); dto.setChangeRate("0");
        try {
            String cleanCurrency = currency.contains("(")
                    ? currency.substring(0, currency.indexOf("(")) : currency;

            String raw = erClient.get()
                    .uri("/v6/latest/" + cleanCurrency)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (raw == null) return dto;

            JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
            if (!"success".equals(root.get("result").asText())) return dto;

            double krw = root.get("rates").get("KRW").asDouble();
            if (currency.contains("JPY")) krw = krw * 100;
            dto.setCurrentPrice(String.format("%.2f", krw));
        } catch (Exception e) {
            System.out.println("환율 조회 실패: " + e.getMessage());
        }
        return dto;
    }
}