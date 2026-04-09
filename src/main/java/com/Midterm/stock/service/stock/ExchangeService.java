package com.Midterm.stock.service.stock;

import com.Midterm.stock.dto.StockChartDto;
import com.Midterm.stock.dto.StockResponseDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Exchange quote service.
 * - Uses Exim daily FX data first so quote and chart stay aligned.
 * - Keeps KIS and ER API as fallbacks.
 */
@Service
public class ExchangeService {

    private static final String KIS_FX_TR_ID = "HHDFS00000300";
    private static final DateTimeFormatter EXIM_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int EXCHANGE_CHART_POINTS = 30;
    private static final int EXCHANGE_LOOKBACK_DAYS = 60;

    private static final Map<String, String> KIS_SYMBOLS = Map.of(
            "USD", "FX@KRWUSD",
            "JPY", "FX@KRWJPY",
            "EUR", "FX@KRWEUR",
            "CNH", "FX@KRWCNH",
            "GBP", "FX@KRWGBP"
    );

    private static final Map<String, String> EXIM_UNITS = Map.of(
            "USD", "USD",
            "JPY", "JPY(100)",
            "EUR", "EUR",
            "CNH", "CNH",
            "GBP", "GBP"
    );

    private final KisApiService kisApi;
    private final String eximApiKey;
    private final WebClient eximClient;
    private final WebClient erClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExchangeService(
            KisApiService kisApi,
            @Value("${exim.api.key:}") String eximApiKey
    ) {
        this.kisApi = kisApi;
        this.eximApiKey = eximApiKey;

        try {
            io.netty.handler.ssl.SslContext sslContext = io.netty.handler.ssl.SslContextBuilder.forClient()
                    .trustManager(io.netty.handler.ssl.util.InsecureTrustManagerFactory.INSTANCE)
                    .build();

            reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create()
                    .secure(spec -> spec.sslContext(sslContext))
                    .responseTimeout(Duration.ofSeconds(10));

            this.eximClient = WebClient.builder()
                    .baseUrl("https://www.koreaexim.go.kr")
                    .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                    .build();

            this.erClient = WebClient.builder()
                    .baseUrl("https://open.er-api.com")
                    .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Exchange WebClient initialization failed", e);
        }
    }

    public StockResponseDto getExchangeRate(String currency) {
        String normalizedCurrency = normalizeCurrency(currency);
        StockResponseDto dto = emptyResponseDto();

        if (loadFromExim(normalizedCurrency, dto)) {
            return dto;
        }

        if (loadFromKis(normalizedCurrency, dto)) {
            return dto;
        }

        loadFromErApi(normalizedCurrency, dto);
        return dto;
    }

    public StockChartDto getExchangeChart(String currency) {
        String normalizedCurrency = normalizeCurrency(currency);
        String eximUnit = EXIM_UNITS.get(normalizedCurrency);

        if (eximUnit == null || eximApiKey == null || eximApiKey.isBlank()) {
            return emptyChart();
        }

        List<String> labels = new ArrayList<>();
        List<String> prices = new ArrayList<>();
        List<String> volumes = new ArrayList<>();

        for (int i = 0; i < EXCHANGE_LOOKBACK_DAYS && labels.size() < EXCHANGE_CHART_POINTS; i++) {
            LocalDate date = LocalDate.now().minusDays(i);
            ExchangeSnapshot snapshot = fetchEximSnapshot(eximUnit, date);
            if (snapshot == null) {
                continue;
            }

            labels.add(snapshot.date.format(EXIM_DATE_FORMAT));
            prices.add(formatNumber(snapshot.price));
            volumes.add("0");
        }

        if (labels.isEmpty()) {
            return emptyChart();
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

    private boolean loadFromExim(String currency, StockResponseDto dto) {
        String eximUnit = EXIM_UNITS.get(currency);
        if (eximUnit == null || eximApiKey == null || eximApiKey.isBlank()) {
            return false;
        }

        try {
            ExchangeSnapshot latest = findEximSnapshot(eximUnit, LocalDate.now(), 10);
            if (latest == null) {
                return false;
            }

            ExchangeSnapshot previous = findEximSnapshot(eximUnit, latest.date.minusDays(1), 10);

            dto.setCurrentPrice(formatNumber(latest.price));
            if (previous == null) {
                dto.setPriceChange("0");
                dto.setChangeRate("0");
                return true;
            }

            double priceChange = latest.price - previous.price;
            double changeRate = previous.price == 0 ? 0 : (priceChange / previous.price) * 100;

            dto.setPriceChange(formatSignedNumber(priceChange));
            dto.setChangeRate(formatSignedNumber(changeRate));
            return true;
        } catch (Exception e) {
            System.out.println("Exim exchange quote failed [" + currency + "]: " + e.getMessage());
            return false;
        }
    }

    private boolean loadFromKis(String currency, StockResponseDto dto) {
        String symbol = KIS_SYMBOLS.get(currency);
        if (symbol == null) {
            return false;
        }

        try {
            kisApi.issueToken();
            JsonNode response = kisApi.get(uriBuilder -> uriBuilder
                    .path("/uapi/overseas-price/v1/quotations/price")
                    .queryParam("AUTH", "")
                    .queryParam("EXCD", "FX")
                    .queryParam("SYMB", symbol)
                    .build(), KIS_FX_TR_ID);

            JsonNode output = response == null ? null : response.get("output");
            if (output == null || output.isNull()) {
                return false;
            }

            String currentPrice = firstText(output, "stck_prpr", "last", "ovrs_nmix_prpr", "base_price");
            if (!hasPositiveNumber(currentPrice)) {
                return false;
            }

            dto.setCurrentPrice(currentPrice);
            dto.setPriceChange(defaultZero(firstText(output, "prdy_vrss", "tday_rltv", "change")));
            dto.setChangeRate(defaultZero(firstText(output, "prdy_ctrt", "tday_rltv_rt", "change_rate")));
            return true;
        } catch (Exception e) {
            System.out.println("KIS exchange quote failed [" + currency + "]: " + e.getMessage());
            return false;
        }
    }

    private void loadFromErApi(String currency, StockResponseDto dto) {
        try {
            String raw = erClient.get()
                    .uri("/v6/latest/" + currency)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (raw == null) {
                return;
            }

            JsonNode root = objectMapper.readTree(raw);
            if (!"success".equals(root.path("result").asText())) {
                return;
            }

            double krw = root.path("rates").path("KRW").asDouble(0);
            if (krw <= 0) {
                return;
            }

            if ("JPY".equals(currency)) {
                krw *= 100;
            }

            dto.setCurrentPrice(String.format(Locale.US, "%.2f", krw));
        } catch (Exception e) {
            System.out.println("Fallback exchange quote failed [" + currency + "]: " + e.getMessage());
        }
    }

    private ExchangeSnapshot findEximSnapshot(String unit, LocalDate startDate, int maxDaysBack) {
        for (int i = 0; i < maxDaysBack; i++) {
            LocalDate date = startDate.minusDays(i);
            ExchangeSnapshot snapshot = fetchEximSnapshot(unit, date);
            if (snapshot != null) {
                return snapshot;
            }
        }
        return null;
    }

    private ExchangeSnapshot fetchEximSnapshot(String unit, LocalDate date) {
        try {
            String raw = eximClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/site/program/financial/exchangeJSON")
                            .queryParam("authkey", eximApiKey)
                            .queryParam("searchdate", date.format(EXIM_DATE_FORMAT))
                            .queryParam("data", "AP01")
                            .build())
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (raw == null || raw.isBlank() || !raw.trim().startsWith("[")) {
                return null;
            }

            JsonNode items = objectMapper.readTree(raw);
            for (JsonNode item : items) {
                if (!unit.equals(item.path("cur_unit").asText())) {
                    continue;
                }

                double price = parseNumber(item.path("deal_bas_r").asText());
                if (price > 0) {
                    return new ExchangeSnapshot(date, price);
                }
            }
        } catch (Exception ignored) {
            return null;
        }

        return null;
    }

    private StockResponseDto emptyResponseDto() {
        StockResponseDto dto = new StockResponseDto();
        dto.setCurrentPrice("0");
        dto.setPriceChange("0");
        dto.setChangeRate("0");
        return dto;
    }

    private StockChartDto emptyChart() {
        StockChartDto dto = new StockChartDto();
        dto.setLabels(new ArrayList<>());
        dto.setClosePrices(new ArrayList<>());
        dto.setVolumes(new ArrayList<>());
        return dto;
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return "USD";
        }

        String normalized = currency.toUpperCase(Locale.ROOT).trim();
        int bracketIndex = normalized.indexOf('(');
        if (bracketIndex >= 0) {
            normalized = normalized.substring(0, bracketIndex);
        }
        return normalized;
    }

    private String firstText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode value = node.get(fieldName);
            if (value == null || value.isNull()) {
                continue;
            }

            String text = value.asText();
            if (text != null && !text.isBlank()) {
                return text.trim();
            }
        }
        return "";
    }

    private boolean hasPositiveNumber(String value) {
        return parseNumber(value) > 0;
    }

    private String defaultZero(String value) {
        return value == null || value.isBlank() ? "0" : value;
    }

    private double parseNumber(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        try {
            return Double.parseDouble(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String formatNumber(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private String formatSignedNumber(double value) {
        return String.format(Locale.US, "%.2f", value);
    }

    private record ExchangeSnapshot(LocalDate date, double price) {
    }
}
