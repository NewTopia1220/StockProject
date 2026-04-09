package com.Midterm.stock.service.stock;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;

/**
 * KIS API 공통 서비스
 * - OAuth 토큰 발급 및 캐싱
 * - 공통 GET 요청 헬퍼
 * - kisClient WebClient 관리
 */
@Service
public class KisApiService {

    private final String appKey;
    private final String appSecret;
    final WebClient kisClient; // package-private: 같은 패키지 서비스에서 접근

    private String accessToken;
    private LocalDateTime tokenExpireTime;

    public KisApiService(
            @Value("${kis.api.app-key}") String appKey,
            @Value("${kis.api.app-secret}") String appSecret,
            @Value("${kis.api.base-url}") String baseUrl
    ) {
        this.appKey = appKey;
        this.appSecret = appSecret;

        try {
            io.netty.handler.ssl.SslContext sslContext =
                    io.netty.handler.ssl.SslContextBuilder.forClient()
                            .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
                            .build();

            reactor.netty.resources.ConnectionProvider provider =
                    reactor.netty.resources.ConnectionProvider.builder("kis-pool")
                            .maxConnections(10)
                            .maxIdleTime(java.time.Duration.ofSeconds(15))
                            .maxLifeTime(java.time.Duration.ofSeconds(50))
                            .pendingAcquireTimeout(java.time.Duration.ofSeconds(10))
                            .evictInBackground(java.time.Duration.ofSeconds(10))
                            .build();

            reactor.netty.http.client.HttpClient httpClient =
                    reactor.netty.http.client.HttpClient.create(provider)
                            .secure(t -> t.sslContext(sslContext)
                                    .handshakeTimeout(java.time.Duration.ofSeconds(30)))
                            .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 15000)
                            .responseTimeout(java.time.Duration.ofSeconds(15))
                            .doOnConnected(conn -> conn
                                    .addHandlerLast(new io.netty.handler.timeout.ReadTimeoutHandler(
                                            15, java.util.concurrent.TimeUnit.SECONDS))
                                    .addHandlerLast(new io.netty.handler.timeout.WriteTimeoutHandler(
                                            15, java.util.concurrent.TimeUnit.SECONDS)));

            this.kisClient = WebClient.builder()
                    .baseUrl(baseUrl)
                    .clientConnector(new org.springframework.http.client.reactive
                            .ReactorClientHttpConnector(httpClient))
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("KIS WebClient 초기화 실패", e);
        }
    }

    /**
     * KIS OAuth 토큰 발급 (23시간 캐싱)
     * synchronized: 동시 다중 요청 시 중복 발급 방지
     */
    public synchronized void issueToken() {
        if (accessToken != null && tokenExpireTime != null
                && LocalDateTime.now().isBefore(tokenExpireTime)) {
            return;
        }
        try {
            String body = "{\"grant_type\":\"client_credentials\","
                    + "\"appkey\":\"" + appKey + "\","
                    + "\"appsecret\":\"" + appSecret + "\"}";

            JsonNode response = kisClient.post()
                    .uri("/oauth2/tokenP")
                    .header("content-type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            if (response != null && response.has("access_token")) {
                this.accessToken = response.get("access_token").asText();
                this.tokenExpireTime = LocalDateTime.now().plusHours(23);
                System.out.println("토큰 발급 완료!");
            }
        } catch (Exception e) {
            System.out.println("토큰 발급 오류: " + e.getMessage());
        }
    }

    // KIS API 요청 간 최소 간격 (100ms) - 초당 10회 제한 대응
    private long lastCallMs = 0;
    private static final long MIN_CALL_INTERVAL_MS = 100;

    /**
     * KIS API 공통 GET 요청 (최대 2회 재시도 + 호출 간격 제한)
     */
    public JsonNode get(
            java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uriFunc,
            String trId) {
        if (accessToken == null) {
            System.out.println("토큰 없음 - KIS API 스킵 [" + trId + "]");
            return null;
        }

        // 호출 간격 보장 (synchronized로 직렬화)
        synchronized (this) {
            long now = System.currentTimeMillis();
            long wait = MIN_CALL_INTERVAL_MS - (now - lastCallMs);
            if (wait > 0) {
                try { Thread.sleep(wait); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
            lastCallMs = System.currentTimeMillis();
        }

        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                return kisClient.get()
                        .uri(uriFunc)
                        .header("authorization", "Bearer " + accessToken)
                        .header("appkey", appKey)
                        .header("appsecret", appSecret)
                        .header("tr_id", trId)
                        .retrieve()
                        .bodyToMono(JsonNode.class)
                        .block();
            } catch (Exception e) {
                System.out.println("KIS API 오류 [" + trId + "] 시도 " + attempt + ": " + e.getMessage());
                if (attempt == 2) return null;
                try { Thread.sleep(600); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        return null;
    }
}