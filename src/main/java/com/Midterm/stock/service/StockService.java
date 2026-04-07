package com.Midterm.stock.service;

import com.Midterm.stock.dto.StockChartDto;
import com.Midterm.stock.dto.StockResponseDto;
import com.Midterm.stock.entity.Stock;
import com.Midterm.stock.repository.StockRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final StockRepository stockRepository;

    private final String appKey;
    private final String appSecret;
    private final String baseUrl;

    private final WebClient kisClient;
    private final WebClient krxClient;
    private final WebClient erClient; // ExchangeRate-API

    private String accessToken;
    private LocalDateTime tokenExpireTime;

    @Value("${python.path:python}")
    private String pythonPath;

    // 캐시
    private List<Map<String, String>> stockListCache = null;
    private LocalDate stockListCacheDate = null;

    // DB 실패 시 최소한의 fallback (삭제하지 말 것)
    private static final List<Map<String, String>> FALLBACK_STOCKS = List.of(
            Map.of("code","005930","name","삼성전자","fullName","삼성전자","market","KOSPI"),
            Map.of("code","000660","name","SK하이닉스","fullName","SK하이닉스","market","KOSPI"),
            Map.of("code","005380","name","현대차","fullName","현대자동차","market","KOSPI"),
            Map.of("code","000270","name","기아","fullName","기아","market","KOSPI"),
            Map.of("code","051910","name","LG화학","fullName","LG화학","market","KOSPI"),
            Map.of("code","006400","name","삼성SDI","fullName","삼성SDI","market","KOSPI"),
            Map.of("code","035420","name","NAVER","fullName","NAVER","market","KOSPI"),
            Map.of("code","035720","name","카카오","fullName","카카오","market","KOSPI")
//            Map.of("code","068270","name","셀트리온","fullName","셀트리온","market","KOSPI"),
//            Map.of("code","207940","name","삼성바이오로직스","fullName","삼성바이오로직스","market","KOSPI"),
//            Map.of("code","005490","name","POSCO홀딩스","fullName","POSCO홀딩스","market","KOSPI"),
//            Map.of("code","066570","name","LG전자","fullName","LG전자","market","KOSPI"),
//            Map.of("code","055550","name","신한지주","fullName","신한지주","market","KOSPI"),
//            Map.of("code","105560","name","KB금융","fullName","KB금융","market","KOSPI"),
//            Map.of("code","086790","name","하나금융지주","fullName","하나금융지주","market","KOSPI"),
//            Map.of("code","017670","name","SK텔레콤","fullName","SK텔레콤","market","KOSPI"),
//            Map.of("code","030200","name","KT","fullName","KT","market","KOSPI"),
//            Map.of("code","009540","name","HD한국조선해양","fullName","HD한국조선해양","market","KOSPI"),
//            Map.of("code","042660","name","한화오션","fullName","한화오션","market","KOSPI"),
//            Map.of("code","373220","name","LG에너지솔루션","fullName","LG에너지솔루션","market","KOSPI"),
//            Map.of("code","247540","name","에코프로비엠","fullName","에코프로비엠","market","KOSDAQ"),
//            Map.of("code","086520","name","에코프로","fullName","에코프로","market","KOSDAQ"),
//            Map.of("code","196170","name","알테오젠","fullName","알테오젠","market","KOSDAQ"),
//            Map.of("code","036570","name","엔씨소프트","fullName","엔씨소프트","market","KOSDAQ"),
//            Map.of("code","352820","name","하이브","fullName","하이브","market","KOSDAQ")
    );

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
            // ① KIS용 SSL 무시
            io.netty.handler.ssl.SslContext kisSslContext =
                    io.netty.handler.ssl.SslContextBuilder.forClient()
                            .trustManager(
                                    io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
                            .build();

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

            // ② ExchangeRate-API용 SSL 무시
            io.netty.handler.ssl.SslContext erSslContext =
                    io.netty.handler.ssl.SslContextBuilder.forClient()
                            .trustManager(
                                    io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
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

        // KRX - http라서 SSL 불필요
        this.krxClient = WebClient.builder()
                .baseUrl("http://data.krx.co.kr")
                .defaultHeader("Content-Type", "application/x-www-form-urlencoded")
                .defaultHeader("User-Agent", "Mozilla/5.0")
                .defaultHeader("Referer", "http://data.krx.co.kr")
                .build();
    }

    // 토큰 발급
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
            } else {
                System.out.println("토큰 응답: " + response);
            }
        } catch (Exception e) {
            System.out.println("토큰 발급 오류: " + e.getMessage());
        }
    }

    // KIS API 공통 GET
    private JsonNode kisGet(
            java.util.function.Function<org.springframework.web.util.UriBuilder,
                    java.net.URI> uriFunc, String trId) {
        if (accessToken == null) {
            System.out.println("토큰 없음 - KIS API 스킵 [" + trId + "]");
            return null;
        }
        // 최대 2번 시도
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
                System.out.println("KIS API 오류 [" + trId + "] 시도 " + attempt
                        + ": " + e.getMessage());
                if (attempt == 2) return null;
                // 재시도 전 잠깐 대기
                try { Thread.sleep(500); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            }
        }
        return null;
    }

    // 현재가 조회
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

    // 일별 시세
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
            return parseChartFromArray(response.get("output"),
                    "stck_bsop_date", "stck_clpr", "acml_vol");
        } catch (Exception e) {
            System.out.println("일별시세 실패 [" + stockCode + "]: " + e.getMessage());
            return emptyChart();
        }
    }

    // 코스피 지수
    public StockResponseDto getKospiIndex() {
        issueToken();
        StockResponseDto dto = new StockResponseDto();
        dto.setCurrentPrice("0"); dto.setChangeRate("0");
        dto.setPriceChange("0"); dto.setVolume("0");
        try {
            JsonNode response = kisGet(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-index-price")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "U")
                    .queryParam("FID_INPUT_ISCD", "0001")
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

    // 코스피 차트
    public StockChartDto getKospiChart() {
        issueToken();
        try {
            String toDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String fromDate = LocalDate.now().minusDays(30)
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            JsonNode response = kisGet(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-index-daily-price")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "U")
                    .queryParam("FID_INPUT_ISCD", "0001")
                    .queryParam("FID_PERIOD_DIV_CODE", "D")
                    .queryParam("FID_INPUT_DATE_1", fromDate)
                    .queryParam("FID_INPUT_DATE_2", toDate)
                    .build(), "FHPUP02120000");
            if (response == null || response.get("output2") == null) return emptyChart();
            return parseChartFromArray(response.get("output2"),
                    "stck_bsop_date", "bstp_nmix_prpr", "acml_vol");
        } catch (Exception e) {
            System.out.println("코스피 차트 실패: " + e.getMessage());
            return emptyChart();
        }
    }

    // 코스닥 지수
    public StockResponseDto getKosdaqIndex() {
        issueToken();
        StockResponseDto dto = new StockResponseDto();
        dto.setCurrentPrice("0"); dto.setPriceChange("0"); dto.setChangeRate("0");
        try {
            JsonNode response = kisGet(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-index-price")
                    .queryParam("FID_COND_MRKT_DIV_CODE", "U")
                    .queryParam("FID_INPUT_ISCD", "0002")
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

    // 환율 조회 - ExchangeRate-API 사용
    public StockResponseDto getExchangeRate(String currency) {
        StockResponseDto dto = new StockResponseDto();
        dto.setCurrentPrice("0");
        dto.setPriceChange("0");
        dto.setChangeRate("0");

        try {
            String cleanCurrency = currency.contains("(")
                    ? currency.substring(0, currency.indexOf("(")) : currency;

            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();

            // 오늘 환율
            String todayRaw = erClient.get()
                    .uri("/v6/latest/" + cleanCurrency)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (todayRaw == null) return dto;
            JsonNode todayRoot = mapper.readTree(todayRaw);
            if (!"success".equals(todayRoot.get("result").asText())) return dto;

            double todayKrw = todayRoot.get("rates").get("KRW").asDouble();

            if (currency.contains("JPY")) todayKrw = todayKrw * 100;

            dto.setCurrentPrice(String.format("%.2f", todayKrw));
            dto.setPriceChange("0");
            dto.setChangeRate("0");
        } catch (Exception e) {
            System.out.println("환율 조회 실패: " + e.getMessage());
        }
        return dto;
    }

    // 시간별 차트
    public StockChartDto getTimePrice(String stockCode) {
        issueToken();
        try {
            String now = java.time.LocalTime.now()
                    .format(DateTimeFormatter.ofPattern("HHmmss"));
            JsonNode response = kisGet(uriBuilder -> uriBuilder
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

    // 분별 차트
    public StockChartDto getMinutePrice(String stockCode) {
        issueToken();
        try {
            String now = java.time.LocalTime.now()
                    .format(DateTimeFormatter.ofPattern("HHmmss"));
            JsonNode response = kisGet(uriBuilder -> uriBuilder
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

    // KRX 전체 종목 목록
    public List<Map<String, String>> getKrxStockList() {
        List<Map<String, String>> result = new ArrayList<>();
        result.addAll(fetchKrxMarket("STK"));
        result.addAll(fetchKrxMarket("KSQ"));
        if (result.isEmpty()) {
            System.out.println("KRX 실패 → Fallback 사용");
            return new ArrayList<>(FALLBACK_STOCKS);
        }
        return result;
    }

    private String getLastTradingDay() {
        LocalDate date = LocalDate.now();
        while (date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            date = date.minusDays(1);
        }
        return date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    private List<Map<String, String>> fetchKrxMarket(String marketCode) {
        try {
            String trdDd = getLastTradingDay();
            String formData = "bld=dbms%2FMDC%2FSTAT%2Fstandard%2FMDCSTAT01901"
                    + "&mktId=" + marketCode
                    + "&trdDd=" + trdDd
                    + "&share=1&money=1&csvxls_isNo=false";

            // HttpURLConnection 사용 (Oracle SSL 충돌 없음)
            java.net.URL url = new java.net.URL(
                    "http://data.krx.co.kr/comm/bldAttendant/getJsonData.cmd");
            java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setRequestProperty("Accept",
                    "application/json, text/javascript, */*; q=0.01");
            conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");
            conn.setRequestProperty("Referer",
                    "http://data.krx.co.kr/contents/MDC/STAT/standard/MDCSTAT01901.cmd");
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");

            // body 전송
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(formData.getBytes("UTF-8"));
            }

            int statusCode = conn.getResponseCode();
            System.out.println("KRX [" + marketCode + "] 상태코드: " + statusCode);

            if (statusCode != 200) {
                System.out.println("KRX [" + marketCode + "] 실패");
                return new ArrayList<>();
            }

            // 응답 읽기
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            System.out.println("KRX [" + marketCode + "] 응답 앞100자: "
                    + sb.substring(0, Math.min(100, sb.length())));

            JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(sb.toString());
            JsonNode outBlock = root.get("OutBlock_1");

            List<Map<String, String>> list = new ArrayList<>();
            if (outBlock != null && outBlock.isArray()) {
                for (JsonNode item : outBlock) {
                    Map<String, String> stock = new java.util.HashMap<>();
                    stock.put("code", item.get("ISU_SRT_CD").asText());
                    stock.put("name", item.get("ISU_ABBRV").asText());
                    stock.put("fullName", item.get("ISU_NM").asText());
                    stock.put("market", item.get("MKT_TP_NM").asText());
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

    // 종목 검색
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
                        .filter(s -> s.get("name").contains(keyword)
                                || s.get("code").contains(keyword))
                        .limit(10)
                        .collect(java.util.stream.Collectors.toList());
            }

            return result.stream()
                    .map(Stock::toMap)
                    .collect(java.util.stream.Collectors.toList());

        } catch (Exception e) {
            System.out.println("종목 검색 오류: " + e.getMessage());
            return FALLBACK_STOCKS.stream()
                    .filter(s -> s.get("name").contains(keyword)
                            || s.get("code").contains(keyword))
                    .limit(10)
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    @org.springframework.transaction.annotation.Transactional
    public void refreshStockListToDB() {
        System.out.println("=== 종목 목록 갱신 시작 ===");
        try {
            String workingDir = System.getProperty("user.dir");
            String pythonPath = System.getProperty("python.path", "python");
            String scriptPath = workingDir + "/python/fetch_stocks.py";

            ProcessBuilder pb = new ProcessBuilder(pythonPath, scriptPath);
            pb.redirectErrorStream(false);
            pb.directory(new java.io.File(workingDir));
            Process process = pb.start();

            String output = new String(
                    process.getInputStream().readAllBytes(), "UTF-8").trim();
            String errors = new String(
                    process.getErrorStream().readAllBytes(), "UTF-8").trim();
            process.waitFor();

            if (!errors.isBlank()) System.out.println("Python 오류: " + errors);
            if (output.isBlank()) {
                System.out.println("Python 출력 없음");
                return;
            }

            int jsonStart = output.indexOf('[');
            if (jsonStart > 0) output = output.substring(jsonStart);

            List<Map<String, String>> stocks =
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .readValue(output,
                                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>() {});

            System.out.println("=== 파싱된 종목 수: " + stocks.size() + "개 ===");

            // 중복 종목코드 제거
            java.util.Map<String, Map<String, String>> dedupMap = new java.util.LinkedHashMap<>();
            for (Map<String, String> m : stocks) {
                String code = m.get("code");
                if (code != null && !code.isBlank()) {
                    dedupMap.put(code, m); // 같은 코드면 덮어씀
                }
            }
            List<Map<String, String>> deduped = new ArrayList<>(dedupMap.values());
            System.out.println("=== 중복 제거 후: " + deduped.size() + "개 ===");

            // DB 저장
            stockRepository.deleteAll();
            stockRepository.flush(); // 삭제 즉시 반영

            List<Stock> entities = deduped.stream().map(m -> {
                Stock s = new Stock();
                s.setStockCode(m.get("code"));
                s.setStockName(m.get("name"));
                s.setFullName(m.get("fullName"));
                s.setMarket(m.get("market"));
                return s;
            }).collect(java.util.stream.Collectors.toList());

            stockRepository.saveAll(entities);
            System.out.println("=== DB 저장 완료: " + entities.size() + "개 ===");

        } catch (Exception e) {
            System.out.println("종목 갱신 오류: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveFallbackToDB() {
        stockRepository.deleteAll();
        List<Stock> entities = FALLBACK_STOCKS.stream().map(m -> {
            Stock s = new Stock();
            s.setStockCode(m.get("code"));
            s.setStockName(m.get("name"));
            s.setFullName(m.get("fullName"));
            s.setMarket(m.get("market"));
            return s;
        }).collect(java.util.stream.Collectors.toList());
        stockRepository.saveAll(entities);
        System.out.println("Fallback " + entities.size() + "개 저장 완료");
    }

    // 빈 차트
    private StockChartDto emptyChart() {
        StockChartDto dto = new StockChartDto();
        dto.setLabels(new ArrayList<>());
        dto.setClosePrices(new ArrayList<>());
        dto.setVolumes(new ArrayList<>());
        return dto;
    }

    private StockChartDto parseChartFromArray(JsonNode array, String dateField,
                                              String priceField, String volField) {
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


    // 등락률 상위 종목 조회
    public List<StockResponseDto> getTopFluctuation() {
        issueToken();
        List<StockResponseDto> result = new ArrayList<>();
        try {
            JsonNode response = kisGet(
                    uriBuilder -> uriBuilder
                            .path("/uapi/domestic-stock/v1/ranking/fluctuation")
                            .queryParam("fid_rsfl_rate2", "")
                            .queryParam("fid_cond_mrkt_div_code", "J")
                            .queryParam("fid_cond_scr_div_code", "20170")
                            .queryParam("fid_input_iscd", "0000")
                            .queryParam("fid_rank_sort_cls_code", "0")    // ← 1자리
                            .queryParam("fid_input_cnt_1", "10")
                            .queryParam("fid_prc_cls_code", "0")
                            .queryParam("fid_input_price_1", "0")
                            .queryParam("fid_input_price_2", "1000000")
                            .queryParam("fid_vol_cnt", "100000")
                            .queryParam("fid_trgt_cls_code", "0")         // ← 9자리 0
                            .queryParam("fid_trgt_exls_cls_code", "0")    // ← 10자리 0
                            .queryParam("fid_div_cls_code", "0")
                            .queryParam("fid_rsfl_rate1", "0")
                            .build(),
                    "FHPST01700000"
            );

            System.out.println("=== 등락률 순위 응답 ===");
            System.out.println(response);

            if (response == null || response.get("output") == null) return result;

            for (JsonNode item : response.get("output")) {
                StockResponseDto dto = new StockResponseDto();
                dto.setStockName(item.get("hts_kor_isnm").asText());
                dto.setCurrentPrice(item.get("stck_prpr").asText());
                dto.setChangeRate(item.get("prdy_ctrt").asText());
                dto.setPriceChange(item.get("prdy_vrss").asText());
                result.add(dto);
            }
            System.out.println("등락률 순위 종목 수: " + result.size());
        } catch (Exception e) {
            System.out.println("등락률 순위 조회 실패: " + e.getMessage());
        }
        return result;
    }


}