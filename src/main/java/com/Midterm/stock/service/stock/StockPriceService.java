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

@Service
@RequiredArgsConstructor
public class StockPriceService {

    private static final String DOMESTIC_MARKET_CODE = "J";
    private static final String INDEX_MARKET_CODE = "U";
    private static final String KOSPI_CODE = "0001";
    private static final String KOSDAQ_CODE = "1001";

    private static final String CURRENT_PRICE_TR_ID = "FHKST01010100";
    private static final String DAILY_PRICE_TR_ID = "FHKST01010400";
    private static final String STOCK_INTRADAY_TR_ID = "FHKST03010200";
    private static final String INDEX_PRICE_TR_ID = "FHPUP02100000";
    private static final String INDEX_DAILY_TR_ID = "FHPUP02120000";
    private static final String INDEX_INTRADAY_TR_ID = "FHKUP03500200";
    private static final String INDEX_HOURLY_INTERVAL = "3600";
    private static final String INDEX_MINUTE_INTERVAL = "60";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");

    private final KisApiService kisApi;

    public StockResponseDto getCurrentPrice(String stockCode) {
        kisApi.issueToken();

        StockResponseDto dto = emptyResponseDto();
        dto.setStockCode(stockCode);
        dto.setStockName(stockCode);

        try {
            JsonNode response = kisApi.get(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-price")
                    .queryParam("FID_COND_MRKT_DIV_CODE", DOMESTIC_MARKET_CODE)
                    .queryParam("FID_INPUT_ISCD", stockCode)
                    .build(), CURRENT_PRICE_TR_ID);

            JsonNode output = response == null ? null : response.get("output");
            if (output == null || output.isNull()) {
                return dto;
            }

            String stockName = text(output, "hts_kor_isnm");
            if (!stockName.isBlank()) {
                dto.setStockName(stockName);
            }

            dto.setCurrentPrice(defaultZero(text(output, "stck_prpr")));
            dto.setOpenPrice(defaultZero(text(output, "stck_oprc")));
            dto.setHighPrice(defaultZero(text(output, "stck_hgpr")));
            dto.setLowPrice(defaultZero(text(output, "stck_lwpr")));
            dto.setVolume(defaultZero(text(output, "acml_vol")));
            dto.setChangeRate(defaultZero(text(output, "prdy_ctrt")));

            String sign = text(output, "prdy_vrss_sign");
            String rawDiff = defaultZero(text(output, "prdy_vrss"));
            if ("4".equals(sign) || "5".equals(sign)) {
                dto.setPriceChange("-" + rawDiff.replace("-", ""));
            } else {
                dto.setPriceChange(rawDiff.replace("+", ""));
            }
        } catch (Exception e) {
            System.out.println("Current price lookup failed [" + stockCode + "]: " + e.getMessage());
        }

        return dto;
    }

    public StockChartDto getDailyPrice(String stockCode) {
        kisApi.issueToken();

        try {
            JsonNode response = kisApi.get(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-daily-price")
                    .queryParam("FID_COND_MRKT_DIV_CODE", DOMESTIC_MARKET_CODE)
                    .queryParam("FID_INPUT_ISCD", stockCode)
                    .queryParam("FID_PERIOD_DIV_CODE", "D")
                    .queryParam("FID_ORG_ADJ_PRC", "0")
                    .build(), DAILY_PRICE_TR_ID);

            JsonNode output = response == null ? null : response.get("output");
            if (output == null || output.isNull()) {
                return emptyChart();
            }

            return parseChartFromArray(output, "stck_bsop_date", "stck_clpr", "acml_vol");
        } catch (Exception e) {
            System.out.println("Daily price lookup failed [" + stockCode + "]: " + e.getMessage());
            return emptyChart();
        }
    }

    public StockChartDto getTimePrice(String stockCode) {
        return getStockIntradayPrice(stockCode, "Y");
    }

    public StockChartDto getMinutePrice(String stockCode) {
        return getStockIntradayPrice(stockCode, "N");
    }

    public StockResponseDto getKospiIndex() {
        return getIndexQuote(KOSPI_CODE, "KOSPI");
    }

    public StockChartDto getKospiChart() {
        return getIndexDailyChart(KOSPI_CODE);
    }

    public StockChartDto getKospiTimeChart() {
        return getIndexIntradayChart(KOSPI_CODE, INDEX_HOURLY_INTERVAL);
    }

    public StockChartDto getKospiMinuteChart() {
        return getIndexIntradayChart(KOSPI_CODE, INDEX_MINUTE_INTERVAL);
    }

    public StockResponseDto getKosdaqIndex() {
        return getIndexQuote(KOSDAQ_CODE, "KOSDAQ");
    }

    public StockChartDto getKosdaqChart() {
        return getIndexDailyChart(KOSDAQ_CODE);
    }

    public StockChartDto getKosdaqTimeChart() {
        return getIndexIntradayChart(KOSDAQ_CODE, INDEX_HOURLY_INTERVAL);
    }

    public StockChartDto getKosdaqMinuteChart() {
        return getIndexIntradayChart(KOSDAQ_CODE, INDEX_MINUTE_INTERVAL);
    }

    public List<StockResponseDto> getTopFluctuation() {
        kisApi.issueToken();
        List<StockResponseDto> result = new ArrayList<>();

        try {
            JsonNode response = kisApi.get(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/ranking/fluctuation")
                    .queryParam("fid_rsfl_rate2", "")
                    .queryParam("fid_cond_mrkt_div_code", DOMESTIC_MARKET_CODE)
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

            JsonNode output = response == null ? null : response.get("output");
            if (output == null || output.isNull()) {
                return result;
            }

            for (JsonNode item : output) {
                StockResponseDto dto = emptyResponseDto();
                dto.setStockCode(defaultZero(text(item, "mksc_shrn_iscd", "stck_shrn_iscd", "iscd")));
                dto.setStockName(text(item, "hts_kor_isnm"));
                dto.setCurrentPrice(defaultZero(text(item, "stck_prpr")));
                dto.setChangeRate(defaultZero(text(item, "prdy_ctrt")));
                dto.setPriceChange(defaultZero(text(item, "prdy_vrss")));
                dto.setVolume(defaultZero(text(item, "acml_vol")));
                dto.setTradeAmount(defaultZero(text(item, "acml_tr_pbmn")));
                result.add(dto);
            }
        } catch (Exception e) {
            System.out.println("Top fluctuation lookup failed: " + e.getMessage());
        }

        return result;
    }

    public List<StockResponseDto> getTopByTradeAmount() {
        kisApi.issueToken();
        List<StockResponseDto> result = new ArrayList<>();

        try {
            JsonNode response = kisApi.get(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/volume-rank")
                    .queryParam("fid_cond_mrkt_div_code", DOMESTIC_MARKET_CODE)
                    .queryParam("fid_cond_scr_div_code", "20171")
                    .queryParam("fid_input_iscd", "0000")
                    .queryParam("fid_div_cls_code", "2")
                    .queryParam("fid_blng_cls_code", "0")
                    .queryParam("fid_trgt_cls_code", "111111111")
                    .queryParam("fid_trgt_exls_cls_code", "0001000001")
                    .queryParam("fid_input_price_1", "")
                    .queryParam("fid_input_price_2", "")
                    .queryParam("fid_vol_cnt", "0")
                    .queryParam("fid_input_date_1", "")
                    .build(), "FHPST01710000");

            JsonNode output = response == null ? null : response.get("output");
            if (output == null || output.isNull()) {
                return result;
            }

            for (JsonNode item : output) {
                StockResponseDto dto = emptyResponseDto();
                dto.setStockCode(defaultZero(text(item, "mksc_shrn_iscd", "stck_shrn_iscd", "iscd")));
                dto.setStockName(text(item, "hts_kor_isnm"));
                dto.setCurrentPrice(defaultZero(text(item, "stck_prpr")));
                dto.setChangeRate(defaultZero(text(item, "prdy_ctrt")));
                dto.setPriceChange(defaultZero(text(item, "prdy_vrss")));
                dto.setVolume(defaultZero(text(item, "acml_vol")));
                dto.setTradeAmount(defaultZero(text(item, "acml_tr_pbmn")));
                result.add(dto);
            }
        } catch (Exception e) {
            System.out.println("Top trade amount lookup failed: " + e.getMessage());
        }

        return result;
    }

    private StockChartDto getStockIntradayPrice(String stockCode, String includePastData) {
        kisApi.issueToken();

        try {
            String now = java.time.LocalTime.now().format(TIME_FORMAT);
            JsonNode response = kisApi.get(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice")
                    .queryParam("FID_ETC_CLS_CODE", "")
                    .queryParam("FID_COND_MRKT_DIV_CODE", DOMESTIC_MARKET_CODE)
                    .queryParam("FID_INPUT_ISCD", stockCode)
                    .queryParam("FID_INPUT_HOUR_1", now)
                    .queryParam("FID_PW_DATA_INCU_YN", includePastData)
                    .build(), STOCK_INTRADAY_TR_ID);

            JsonNode output = response == null ? null : response.get("output2");
            if (output == null || output.isNull()) {
                return emptyChart();
            }

            return parseStockTimeChart(output);
        } catch (Exception e) {
            System.out.println("Stock intraday chart lookup failed [" + stockCode + "]: " + e.getMessage());
            return emptyChart();
        }
    }

    private StockResponseDto getIndexQuote(String indexCode, String indexName) {
        kisApi.issueToken();

        StockResponseDto dto = emptyResponseDto();
        dto.setStockName(indexName);

        try {
            JsonNode response = kisApi.get(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-index-price")
                    .queryParam("FID_COND_MRKT_DIV_CODE", INDEX_MARKET_CODE)
                    .queryParam("FID_INPUT_ISCD", indexCode)
                    .build(), INDEX_PRICE_TR_ID);

            JsonNode output = response == null ? null : response.get("output");
            if (output == null || output.isNull()) {
                return dto;
            }

            dto.setCurrentPrice(defaultZero(text(output, "bstp_nmix_prpr")));
            dto.setPriceChange(defaultZero(text(output, "bstp_nmix_prdy_vrss")));
            dto.setChangeRate(defaultZero(text(output, "bstp_nmix_prdy_ctrt")));
            dto.setOpenPrice(defaultZero(text(output, "bstp_nmix_oprc")));
            dto.setHighPrice(defaultZero(text(output, "bstp_nmix_hgpr")));
            dto.setLowPrice(defaultZero(text(output, "bstp_nmix_lwpr")));
            dto.setVolume(defaultZero(text(output, "acml_vol")));
        } catch (Exception e) {
            System.out.println("Index quote lookup failed [" + indexCode + "]: " + e.getMessage());
        }

        return dto;
    }

    private StockChartDto getIndexDailyChart(String indexCode) {
        kisApi.issueToken();

        try {
            String toDate = LocalDate.now().format(DATE_FORMAT);
            String fromDate = LocalDate.now().minusDays(30).format(DATE_FORMAT);

            JsonNode response = kisApi.get(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-index-daily-price")
                    .queryParam("FID_COND_MRKT_DIV_CODE", INDEX_MARKET_CODE)
                    .queryParam("FID_INPUT_ISCD", indexCode)
                    .queryParam("FID_PERIOD_DIV_CODE", "D")
                    .queryParam("FID_INPUT_DATE_1", fromDate)
                    .queryParam("FID_INPUT_DATE_2", toDate)
                    .build(), INDEX_DAILY_TR_ID);

            JsonNode output = response == null ? null : response.get("output2");
            if (output == null || output.isNull()) {
                return emptyChart();
            }

            return parseChartFromArray(output, "stck_bsop_date", "bstp_nmix_prpr", "acml_vol");
        } catch (Exception e) {
            System.out.println("Index daily chart lookup failed [" + indexCode + "]: " + e.getMessage());
            return emptyChart();
        }
    }

    private StockChartDto getIndexIntradayChart(String indexCode, String intervalCode) {
        kisApi.issueToken();

        try {
            JsonNode response = kisApi.get(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/inquire-time-indexchartprice")
                    .queryParam("FID_COND_MRKT_DIV_CODE", INDEX_MARKET_CODE)
                    .queryParam("FID_ETC_CLS_CODE", "0")
                    .queryParam("FID_INPUT_ISCD", indexCode)
                    .queryParam("FID_INPUT_HOUR_1", intervalCode)
                    .queryParam("FID_PW_DATA_INCU_YN", "Y")
                    .build(), INDEX_INTRADAY_TR_ID);

            JsonNode output = response == null ? null : response.get("output2");
            if (output == null || output.isNull()) {
                return emptyChart();
            }

            return parseIndexTimeChart(output);
        } catch (Exception e) {
            System.out.println("Index intraday chart lookup failed [" + indexCode + "]: " + e.getMessage());
            return emptyChart();
        }
    }

    private StockChartDto parseChartFromArray(JsonNode array, String dateField, String priceField, String volumeField) {
        List<String> labels = new ArrayList<>();
        List<String> prices = new ArrayList<>();
        List<String> volumes = new ArrayList<>();

        for (JsonNode item : array) {
            labels.add(defaultZero(text(item, dateField)));
            prices.add(defaultZero(text(item, priceField)));
            volumes.add(defaultZero(text(item, volumeField)));
        }

        Collections.reverse(labels);
        Collections.reverse(prices);
        Collections.reverse(volumes);

        StockChartDto dto = emptyChart();
        dto.setLabels(labels);
        dto.setClosePrices(prices);
        dto.setVolumes(volumes);
        return dto;
    }

    private StockChartDto parseStockTimeChart(JsonNode array) {
        List<String> labels = new ArrayList<>();
        List<String> prices = new ArrayList<>();
        List<String> volumes = new ArrayList<>();

        for (JsonNode item : array) {
            labels.add(formatTimeOnly(text(item, "stck_cntg_hour")));
            prices.add(defaultZero(text(item, "stck_prpr")));
            volumes.add(defaultZero(text(item, "cntg_vol")));
        }

        Collections.reverse(labels);
        Collections.reverse(prices);
        Collections.reverse(volumes);

        StockChartDto dto = emptyChart();
        dto.setLabels(labels);
        dto.setClosePrices(prices);
        dto.setVolumes(volumes);
        return dto;
    }

    private StockChartDto parseIndexTimeChart(JsonNode array) {
        List<String> labels = new ArrayList<>();
        List<String> prices = new ArrayList<>();
        List<String> volumes = new ArrayList<>();

        for (JsonNode item : array) {
            String rawDate = text(item, "stck_bsop_date");
            String rawTime = text(item, "stck_cntg_hour");
            labels.add(formatDateTimeLabel(rawDate, rawTime));
            prices.add(defaultZero(text(item, "bstp_nmix_prpr")));
            volumes.add(defaultZero(text(item, "cntg_vol")));
        }

        Collections.reverse(labels);
        Collections.reverse(prices);
        Collections.reverse(volumes);

        StockChartDto dto = emptyChart();
        dto.setLabels(labels);
        dto.setClosePrices(prices);
        dto.setVolumes(volumes);
        return dto;
    }

    private StockResponseDto emptyResponseDto() {
        StockResponseDto dto = new StockResponseDto();
        dto.setCurrentPrice("0");
        dto.setOpenPrice("0");
        dto.setHighPrice("0");
        dto.setLowPrice("0");
        dto.setVolume("0");
        dto.setPriceChange("0");
        dto.setChangeRate("0");
        dto.setTradeAmount("0");
        return dto;
    }

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

    private String text(JsonNode node, String fieldName) {
        return text(node, new String[]{fieldName});
    }

    private String text(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) {
            return "";
        }

        for (String fieldName : fieldNames) {
            if (fieldName == null || fieldName.isBlank()) {
                continue;
            }

            JsonNode value = node.get(fieldName);
            if (value == null || value.isNull()) {
                continue;
            }

            String text = value.asText();
            if (text != null && !text.trim().isEmpty()) {
                return text.trim();
            }
        }
        return "";
    }

    private String defaultZero(String value) {
        return value == null || value.isBlank() ? "0" : value;
    }

    private String formatTimeOnly(String rawTime) {
        if (rawTime == null || rawTime.length() < 4) {
            return rawTime == null || rawTime.isBlank() ? "-" : rawTime;
        }
        return rawTime.substring(0, 2) + ":" + rawTime.substring(2, 4);
    }

    private String formatDateTimeLabel(String rawDate, String rawTime) {
        String timeText = formatTimeOnly(rawTime);
        if (rawDate == null || rawDate.length() != 8) {
            return timeText;
        }
        String dateText = rawDate.substring(4, 6) + "-" + rawDate.substring(6, 8);
        return dateText + " " + timeText;
    }
}
