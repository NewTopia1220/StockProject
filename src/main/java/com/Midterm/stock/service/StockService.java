package com.Midterm.stock.service;

import com.Midterm.stock.dto.StockChartDto;
import com.Midterm.stock.dto.StockResponseDto;
import com.Midterm.stock.entity.Stock;
import com.Midterm.stock.repository.StockRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 주식 관련 비즈니스 로직 서비스
 * - KIS API (한국투자증권): 주식 시세, 차트, 지수 조회
 * - ExchangeRate-API: 환율 조회
 * - KRX + Python(FinanceDataReader): 전체 종목 목록 관리
 */
@Service
public class StockService {

    // ── 저장소 ────────────────────────────────────────────────
    private final StockRepository stockRepository;

    // ── KIS API 인증 정보 ────────────────────────────────────
    private final String appKey;
    private final String appSecret;
    private final String baseUrl;

    // ── WebClient (API 호출 클라이언트) ─────────────────────
    private final WebClient kisClient;  // 한국투자증권 API
    private final WebClient krxClient;  // 한국거래소 API (현재 미사용, 추후 활용 가능)
    private final WebClient erClient;   // ExchangeRate-API (환율)

    // ── KIS 토큰 관리 ────────────────────────────────────────
    private String accessToken;         // KIS OAuth 액세스 토큰
    private LocalDateTime tokenExpireTime; // 토큰 만료 시각 (발급 후 23시간)

    // ── Python 스크립트 경로 (application.properties에서 주입) ─
    @Value("${python.path:python}")
    private String pythonPath;

    // ── Fallback 종목 목록 (DB 실패 시 최소 검색용, 삭제 금지) ─
    private static final List<Map<String, String>> FALLBACK_STOCKS = List.of(
            Map.of("code", "005930", "name", "삼성전자",   "fullName", "삼성전자",   "market", "KOSPI"),
            Map.of("code", "000660", "name", "SK하이닉스", "fullName", "SK하이닉스", "market", "KOSPI"),
            Map.of("code", "005380", "name", "현대차",     "fullName", "현대자동차", "market", "KOSPI"),
            Map.of("code", "000270", "name", "기아",       "fullName", "기아",       "market", "KOSPI"),
            Map.of("code", "051910", "name", "LG화학",     "fullName", "LG화학",     "market", "KOSPI"),
            Map.of("code", "006400", "name", "삼성SDI",    "fullName", "삼성SDI",    "market", "KOSPI"),
            Map.of("code", "035420", "name", "NAVER",      "fullName", "NAVER",      "market", "KOSPI"),
            Map.of("code", "035720", "name", "카카오",     "fullName", "카카오",     "market", "KOSPI")
    );

    // ── 생성자: WebClient 초기화 ─────────────────────────────
    public StockService(
            @Value("${kis.api.app-key}") String appKey,
            @Value("${kis.api.app-secret}") String appSecret,
            @Value("${kis.api.base-url}") String baseUrl,
            StockRepository stockRepository
    ) {
        this.appKey = appKey;
        this.appSecret = appSecret;
        this.baseUrl = baseUrl;
        this.stockRepository = stockRepository;

        try {
            // KIS API - HTTPS 9443 포트 사용, SSL 인증서 무시 필요
            io.netty.handler.ssl.SslContext kisSslContext =
                    io.netty.handler.ssl.SslContextBuilder.forClient()
                            .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
                            .build();

            // KIS 연결 풀: 유휴 연결 정리로 Connection prematurely closed 방지
            reactor.netty.resources.ConnectionProvider provider =
                    reactor.netty.resources.ConnectionProvider.builder("kis-pool")
                            .maxConnections(10)
                            .maxIdleTime(java.time.Duration.ofSeconds(15))
                            .maxLifeTime(java.time.Duration.ofSeconds(50))
                            .pendingAcquireTimeout(java.time.Duration.ofSeconds(10))
                            .evictInBackground(java.time.Duration.ofSeconds(10))
                            .build();

            reactor.netty.http.client.HttpClient kisHttpClient =
                    reactor.netty.http.client.HttpClient.create(provider)
                            .secure(t -> t.sslContext(kisSslContext)
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
                            .ReactorClientHttpConnector(kisHttpClient))
                    .build();

            // ExchangeRate-API - HTTPS, SSL 인증서 무시 필요
            io.netty.handler.ssl.SslContext erSslContext =
                    io.netty.handler.ssl.SslContextBuilder.forClient()
                            .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
                            .build();

            reactor.netty.http.client.HttpClient erHttpClient =
                    reactor.netty.http.client.HttpClient.create()
                            .secure(t -> t.sslContext(erSslContext))
                            .responseTimeout(java.time.Duration.ofSeconds(10));

            this.erClient = WebClient.builder()
                    .baseUrl("https://open.er-api.com")
                    .clientConnector(new org.springframework.http.client.reactive
                            .ReactorClientHttpConnector(erHttpClient))
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("WebClient 초기화 실패", e);
        }

        // KRX - HTTP 통신, SSL 불필요
        this.krxClient = WebClient.builder()
                .baseUrl("http://data.krx.co.kr")
                .defaultHeader("Content-Type", "application/x-www-form-urlencoded")
                .defaultHeader("User-Agent", "Mozilla/5.0")
                .defaultHeader("Referer", "http://data.krx.co.kr")
                .build();
    }

    // ════════════════════════════════════════════════════════
    //  KIS 토큰 관련
    // ════════════════════════════════════════════════════════

    /**
     * KIS API OAuth 토큰 발급
     * - 토큰은 23시간 유효, 만료 전이면 재발급 생략
     * - synchronized: 동시 다중 요청 시 중복 발급 방지
     */
    public synchronized void issueToken() {
        if (accessToken != null && tokenExpireTime != null
                && LocalDateTime.now().isBefore(tokenExpireTime)) {
            return;
        }
        try {
            // ObjectMapper 사용 시 appsecret의 '=' 문자가 이스케이프되는 문제로 직접 조합
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
            } else {
                System.out.println("토큰 응답 이상: " + response);
            }
        } catch (Exception e) {
            System.out.println("토큰 발급 오류: " + e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════
    //  KIS API 공통 헬퍼
    // ════════════════════════════════════════════════════════

    /**
     * KIS API 공통 GET 요청
     * - 토큰 없으면 즉시 null 반환
     * - 최대 2회 재시도 (Connection prematurely closed 대응)
     *
     * @param uriFunc URI 빌더 함수
     * @param trId    KIS 거래 ID (API별 고유값)
     * @return 응답 JsonNode, 실패 시 null
     */
    private JsonNode kisGet(
            java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI> uriFunc,
            String trId) {
        if (accessToken == null) {
            System.out.println("토큰 없음 - KIS API 스킵 [" + trId + "]");
            return null;
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
                try {
                    Thread.sleep(500); // 재시도 전 대기
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        return null;
    }

    // ════════════════════════════════════════════════════════
    //  주식 시세 조회
    // ════════════════════════════════════════════════════════

    /**
     * 특정 종목 현재가 조회
     * - 현재가, 시가, 고가, 저가, 거래량 포함
     *
     * @param stockCode 종목코드 (예: 005930)
     */
    public StockResponseDto getCurrentPrice(String stockCode) {
        issueToken();
        StockResponseDto dto = new StockResponseDto();
        dto.setStockName(stockCode);
        dto.setCurrentPrice("0");
        dto.setOpenPrice("0");
        dto.setHighPrice("0");
        dto.setLowPrice("0");
        dto.setVolume("0");
        try {
            JsonNode response = kisGet(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                    .queryParam("FID_INPUT_ISCD", stockCode)
                    .build(), "FHKST01010100");
            if (response == null || response.get("output") == null) return dto;
            JsonNode output = response.get("output");
            dto.setCurrentPrice(output.get("stck_prpr").asText());
            dto.setOpenPrice(output.get("stck_oprc").asText());
            dto.setHighPrice(output.get("stck_hgpr").asText());
            dto.setLowPrice(output.get("stck_lwpr").asText());
            dto.setVolume(output.get("acml_vol").asText());
        } catch (Exception e) {
            System.out.println("현재가 조회 실패 [" + stockCode + "]: " + e.getMessage());
        }
        return dto;
    }

    /**
     * 특정 종목 일별 시세 조회 (차트용)
     * - 최근 30일 종가, 거래량 데이터
     *
     * @param stockCode 종목코드
     */
    public StockChartDto getDailyPrice(String stockCode) {
        issueToken();
        try {
            JsonNode response = kisGet(uriBuilder -> uriBuilder
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

    /**
     * 특정 종목 시간별 체결 차트 조회 (장중 전용)
     *
     * @param stockCode 종목코드
     */
    public StockChartDto getTimePrice(String stockCode) {
        issueToken();
        try {
            String now = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
            JsonNode response = kisGet(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice")
                    .queryParam("FID_ETC_CLS_CODE", "")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                    .queryParam("FID_INPUT_ISCD", stockCode)
                    .queryParam("FID_INPUT_HOUR_1", now)
                    .queryParam("FID_PW_DATA_INCU_YN", "Y") // Y = 시간별
                    .build(), "FHKST03010200");
            if (response == null || response.get("output2") == null) return emptyChart();
            return parseTimeChart(response.get("output2"));
        } catch (Exception e) {
            System.out.println("시간별 차트 실패 [" + stockCode + "]: " + e.getMessage());
            return emptyChart();
        }
    }

    /**
     * 특정 종목 분별 체결 차트 조회 (장중 전용)
     *
     * @param stockCode 종목코드
     */
    public StockChartDto getMinutePrice(String stockCode) {
        issueToken();
        try {
            String now = java.time.LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
            JsonNode response = kisGet(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice")
                    .queryParam("FID_ETC_CLS_CODE", "")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                    .queryParam("FID_INPUT_ISCD", stockCode)
                    .queryParam("FID_INPUT_HOUR_1", now)
                    .queryParam("FID_PW_DATA_INCU_YN", "N") // N = 분별
                    .build(), "FHKST03010200");
            if (response == null || response.get("output2") == null) return emptyChart();
            return parseTimeChart(response.get("output2"));
        } catch (Exception e) {
            System.out.println("분별 차트 실패 [" + stockCode + "]: " + e.getMessage());
            return emptyChart();
        }
    }

    // ════════════════════════════════════════════════════════
    //  지수 조회
    // ════════════════════════════════════════════════════════

    /**
     * 코스피 현재 지수 조회
     * - 현재지수, 전일대비, 등락률, 거래량 포함
     */
    public StockResponseDto getKospiIndex() {
        issueToken();
        StockResponseDto dto = new StockResponseDto();
        dto.setCurrentPrice("0");
        dto.setChangeRate("0");
        dto.setPriceChange("0");
        dto.setVolume("0");
        try {
            JsonNode response = kisGet(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-index-price")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "U")
                    .queryParam("FID_INPUT_ISCD", "0001") // 0001 = 코스피
                    .build(), "FHPUP02100000");
            if (response == null || response.get("output") == null) return dto;
            JsonNode output = response.get("output");
            dto.setCurrentPrice(output.get("bstp_nmix_prpr").asText());
            dto.setChangeRate(output.get("bstp_nmix_prdy_ctrt").asText());
            dto.setPriceChange(output.get("bstp_nmix_prdy_vrss").asText());
            dto.setVolume(output.get("acml_vol").asText());
        } catch (Exception e) {
            System.out.println("코스피 조회 실패: " + e.getMessage());
        }
        return dto;
    }

    /**
     * 코스피 일별 차트 데이터 조회 (최근 30일)
     */
    public StockChartDto getKospiChart() {
        issueToken();
        try {
            String toDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String fromDate = LocalDate.now().minusDays(30).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            JsonNode response = kisGet(uriBuilder -> uriBuilder
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

    /**
     * 코스닥 현재 지수 조회
     */
    public StockResponseDto getKosdaqIndex() {
        issueToken();
        StockResponseDto dto = new StockResponseDto();
        dto.setCurrentPrice("0");
        dto.setPriceChange("0");
        dto.setChangeRate("0");
        try {
            JsonNode response = kisGet(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-index-price")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "U")
                    .queryParam("FID_INPUT_ISCD", "0002") // 0002 = 코스닥
                    .build(), "FHPUP02100000");
            if (response == null || response.get("output") == null) return dto;
            JsonNode output = response.get("output");
            dto.setCurrentPrice(output.get("bstp_nmix_prpr").asText());
            dto.setPriceChange(output.get("bstp_nmix_prdy_vrss").asText());
            dto.setChangeRate(output.get("bstp_nmix_prdy_ctrt").asText());
        } catch (Exception e) {
            System.out.println("코스닥 조회 실패: " + e.getMessage());
        }
        return dto;
    }

    // ════════════════════════════════════════════════════════
    //  환율 조회
    // ════════════════════════════════════════════════════════

    /**
     * 환율 조회 (ExchangeRate-API 사용)
     * - 한국수출입은행 API SSL/리다이렉트 문제로 대체
     * - JPY는 100엔 기준으로 변환하여 반환
     *
     * @param currency 통화코드 (USD, JPY(100), EUR, CNH, GBP)
     */
    public StockResponseDto getExchangeRate(String currency) {
        StockResponseDto dto = new StockResponseDto();
        dto.setCurrentPrice("0");
        dto.setPriceChange("0");
        dto.setChangeRate("0");
        try {
            // JPY(100) → JPY 로 정리
            String cleanCurrency = currency.contains("(")
                    ? currency.substring(0, currency.indexOf("(")) : currency;

            String todayRaw = erClient.get()
                    .uri("/v6/latest/" + cleanCurrency)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (todayRaw == null) return dto;

            JsonNode todayRoot = new com.fasterxml.jackson.databind.ObjectMapper().readTree(todayRaw);
            if (!"success".equals(todayRoot.get("result").asText())) return dto;

            double todayKrw = todayRoot.get("rates").get("KRW").asDouble();
            if (currency.contains("JPY")) todayKrw = todayKrw * 100; // 100엔 기준 변환

            dto.setCurrentPrice(String.format("%.2f", todayKrw));
        } catch (Exception e) {
            System.out.println("환율 조회 실패: " + e.getMessage());
        }
        return dto;
    }

    // ════════════════════════════════════════════════════════
    //  순위 조회
    // ════════════════════════════════════════════════════════

    /**
     * 등락률 상위 종목 조회 (상위 10개)
     * - 거래량 10만 이상 필터, 전체 종목 대상
     * - 티커 + 종목 리스트에 사용
     */
    public List<StockResponseDto> getTopFluctuation() {
        issueToken();
        List<StockResponseDto> result = new ArrayList<>();
        try {
            JsonNode response = kisGet(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/ranking/fluctuation")
                    .queryParam("fid_rsfl_rate2", "")
                    .queryParam("fid_cond_mrkt_div_code", "J")
                    .queryParam("fid_cond_scr_div_code", "20170")
                    .queryParam("fid_input_iscd", "0000")          // 전체 종목
                    .queryParam("fid_rank_sort_cls_code", "0")     // 등락률 순
                    .queryParam("fid_input_cnt_1", "10")           // 상위 10개
                    .queryParam("fid_prc_cls_code", "0")
                    .queryParam("fid_input_price_1", "0")
                    .queryParam("fid_input_price_2", "1000000")
                    .queryParam("fid_vol_cnt", "100000")           // 최소 거래량 10만
                    .queryParam("fid_trgt_cls_code", "0")
                    .queryParam("fid_trgt_exls_cls_code", "0")
                    .queryParam("fid_div_cls_code", "0")
                    .queryParam("fid_rsfl_rate1", "0")
                    .build(), "FHPST01700000");

            if (response == null || response.get("output") == null) return result;

            for (JsonNode item : response.get("output")) {
                StockResponseDto dto = new StockResponseDto();
                dto.setStockName(item.get("hts_kor_isnm").asText());   // 종목명
                dto.setCurrentPrice(item.get("stck_prpr").asText());   // 현재가
                dto.setChangeRate(item.get("prdy_ctrt").asText());     // 등락률
                dto.setPriceChange(item.get("prdy_vrss").asText());    // 전일대비
                result.add(dto);
            }
        } catch (Exception e) {
            System.out.println("등락률 순위 조회 실패: " + e.getMessage());
        }
        return result;
    }

    // ════════════════════════════════════════════════════════
    //  종목 목록 관리 (KRX + DB)
    // ════════════════════════════════════════════════════════

    /**
     * 종목명/코드로 종목 검색
     * - DB에 데이터 없으면 자동으로 KRX에서 로드
     * - DB 조회 실패 시 FALLBACK_STOCKS로 대체
     *
     * @param keyword 종목명 또는 종목코드
     */
    public List<Map<String, String>> searchStock(String keyword) {
        try {
            if (stockRepository.count() == 0) {
                System.out.println("DB 종목 없음 → KRX 로드 중...");
                refreshStockListToDB();
            }
            if (keyword == null || keyword.isBlank()) return List.of();

            List<Stock> result = stockRepository.findByKeyword(
                    keyword,
                    org.springframework.data.domain.PageRequest.of(0, 10)
            );

            if (result.isEmpty()) {
                return FALLBACK_STOCKS.stream()
                        .filter(s -> s.get("name").contains(keyword) || s.get("code").contains(keyword))
                        .limit(10)
                        .collect(Collectors.toList());
            }
            return result.stream().map(Stock::toMap).collect(Collectors.toList());

        } catch (Exception e) {
            System.out.println("종목 검색 오류: " + e.getMessage());
            return FALLBACK_STOCKS.stream()
                    .filter(s -> s.get("name").contains(keyword) || s.get("code").contains(keyword))
                    .limit(10)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Python(FinanceDataReader)으로 전체 종목 목록을 가져와 DB에 저장
     * - Python 실행 실패 시 FALLBACK_STOCKS로 저장
     * - 중복 종목코드 제거 후 저장 (ORA-00001 방지)
     * - @Transactional: deleteAll + saveAll 원자적 처리
     */
    @Transactional
    public void refreshStockListToDB() {
        System.out.println("=== 종목 목록 갱신 시작 ===");
        try {
            String workingDir = System.getProperty("user.dir");
            String scriptPath = workingDir + "/python/fetch_stocks.py";

            ProcessBuilder pb = new ProcessBuilder(pythonPath, scriptPath);
            pb.redirectErrorStream(false);
            pb.directory(new java.io.File(workingDir));
            Process process = pb.start();

            String output = new String(process.getInputStream().readAllBytes(), "UTF-8").trim();
            String errors = new String(process.getErrorStream().readAllBytes(), "UTF-8").trim();
            process.waitFor();

            if (!errors.isBlank()) System.out.println("Python 오류: " + errors);
            if (output.isBlank()) {
                System.out.println("Python 출력 없음 → Fallback 저장");
                saveFallbackToDB();
                return;
            }

            // JSON 배열 시작 위치 탐색 (Python print 로그가 앞에 붙는 경우 대비)
            int jsonStart = output.indexOf('[');
            if (jsonStart > 0) output = output.substring(jsonStart);

            List<Map<String, String>> stocks = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(output, new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>() {});

            System.out.println("=== 파싱된 종목 수: " + stocks.size() + "개 ===");

            // 중복 종목코드 제거 (같은 코드면 마지막 값으로 덮어씀)
            LinkedHashMap<String, Map<String, String>> dedupMap = new LinkedHashMap<>();
            for (Map<String, String> m : stocks) {
                String code = m.get("code");
                if (code != null && !code.isBlank()) {
                    dedupMap.put(code, m);
                }
            }
            List<Map<String, String>> deduped = new ArrayList<>(dedupMap.values());
            System.out.println("=== 중복 제거 후: " + deduped.size() + "개 ===");

            stockRepository.deleteAll();
            stockRepository.flush(); // 삭제 즉시 반영 후 insert

            List<Stock> entities = deduped.stream().map(m -> {
                Stock s = new Stock();
                s.setStockCode(m.get("code"));
                s.setStockName(m.get("name"));
                s.setFullName(m.get("fullName"));
                s.setMarket(m.get("market"));
                return s;
            }).collect(Collectors.toList());

            stockRepository.saveAll(entities);
            System.out.println("=== DB 저장 완료: " + entities.size() + "개 ===");

        } catch (Exception e) {
            System.out.println("종목 갱신 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * FALLBACK_STOCKS를 DB에 저장 (Python 실패 시 대체)
     */
    private void saveFallbackToDB() {
        stockRepository.deleteAll();
        List<Stock> entities = FALLBACK_STOCKS.stream().map(m -> {
            Stock s = new Stock();
            s.setStockCode(m.get("code"));
            s.setStockName(m.get("name"));
            s.setFullName(m.get("fullName"));
            s.setMarket(m.get("market"));
            return s;
        }).collect(Collectors.toList());
        stockRepository.saveAll(entities);
        System.out.println("Fallback " + entities.size() + "개 저장 완료");
    }

    // ════════════════════════════════════════════════════════
    //  차트 파싱 헬퍼
    // ════════════════════════════════════════════════════════

    /**
     * 일별/코스피 차트 배열 파싱
     * - API 응답은 최신→과거 순서이므로 reverse 처리
     *
     * @param array      JsonNode 배열
     * @param dateField  날짜 필드명
     * @param priceField 가격 필드명
     * @param volField   거래량 필드명
     */
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

    /**
     * 시간별/분별 차트 배열 파싱
     * - 시각 포맷: "103000" → "10:30"
     */
    private StockChartDto parseTimeChart(JsonNode array) {
        List<String> labels = new ArrayList<>();
        List<String> prices = new ArrayList<>();
        List<String> volumes = new ArrayList<>();
        for (JsonNode item : array) {
            String raw = item.get("stck_cntg_hour").asText();
            labels.add(raw.substring(0, 2) + ":" + raw.substring(2, 4)); // HHmmss → HH:mm
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

    /**
     * 빈 차트 DTO 반환 (API 실패 시 Fallback용)
     */
    private StockChartDto emptyChart() {
        StockChartDto dto = new StockChartDto();
        dto.setLabels(new ArrayList<>());
        dto.setClosePrices(new ArrayList<>());
        dto.setVolumes(new ArrayList<>());
        return dto;
    }
}
