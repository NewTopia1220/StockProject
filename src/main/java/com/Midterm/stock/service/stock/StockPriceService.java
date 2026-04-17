package com.Midterm.stock.service.stock;

import com.Midterm.stock.dto.StockChartDto;
import com.Midterm.stock.dto.StockResponseDto;
import com.Midterm.stock.repository.StockRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StockPriceService {

    private final StockRepository stockRepository;
    private static final String DOMESTIC_MARKET_CODE = "J";
    private static final String INDEX_MARKET_CODE = "U";
    private static final String KOSPI_CODE = "0001";
    private static final String KOSDAQ_CODE = "1001";
    private static final int MARKET_RANK_LIMIT = 30;

    private static final String CURRENT_PRICE_TR_ID  = "FHKST01010100";
    private static final String DAILY_PRICE_TR_ID     = "FHKST01010400";
    private static final String STOCK_INTRADAY_TR_ID  = "FHKST03010200";
    private static final String INDEX_PRICE_TR_ID     = "FHPUP02100000";
    private static final String INDEX_DAILY_TR_ID     = "FHPUP02120000";
    private static final String INDEX_INTRADAY_TR_ID  = "FHKUP03500200";
    private static final String INDEX_HOURLY_INTERVAL = "3600";
    private static final String INDEX_MINUTE_INTERVAL = "60";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");

    private final KisApiService kisApi;

    // 현재가 케시 // 같은 종목 짧은 시간 안에 여러 번 조회할 때 매번 다시 부르지 않도록
    private final ConcurrentHashMap<String, CacheEntry<StockResponseDto>> quoteCache = new ConcurrentHashMap<>();

    // 차트 캐시
    private final ConcurrentHashMap<String, CacheEntry<StockChartDto>> chartCache = new ConcurrentHashMap<>();

    // 상위 종목 캐시
    private final ConcurrentHashMap<String, CacheEntry<List<StockResponseDto>>> rankingCache = new ConcurrentHashMap<>();

    // 종목명 캐시
    private final ConcurrentHashMap<String, String> stockNameCache = new ConcurrentHashMap<>();

    public StockResponseDto getCurrentPrice(String stockCode) {
        // 현재가는 장중엔 조금 더 자주 바뀌므로 TTL을 짧게,
        // 장외엔 값이 거의 안 바뀌므로 TTL을 길게 둔다.
        String cacheKey = "current:" + stockCode;
        int ttlSeconds = isMarketOpen() ? 15 : 300;

        return getCachedOrLoadResponse(quoteCache, cacheKey, ttlSeconds, () -> loadCurrentPrice(stockCode));
    }

    // 실제 외부 API 호출은 여기서만 수행한다.
    // getCurrentPrice()는 "캐시 우선" 진입점 역할만 담당하게 분리한다.
    // 이렇게 나누면 나중에 캐시 정책을 바꿔도 실제 조회 로직은 덜 흔들린다.
    private StockResponseDto loadCurrentPrice(String stockCode) {
        kisApi.issueToken();

        StockResponseDto dto = emptyResponseDto();
        dto.setStockCode(stockCode);

        // DB 종목명도 같은 종목이면 반복 조회할 필요가 없어서 캐시한다.
        String dbName = stockNameCache.computeIfAbsent(
                stockCode,
                key -> {
                    String name = stockRepository.findByStockCode(key);
                    return name != null ? name : key;
                }
        );
        dto.setStockName(dbName);

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
                stockNameCache.put(stockCode, stockName);
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
            // 실패하더라도 빈 DTO를 반환해서 화면이 아예 깨지지 않게 둔다.
        }

        return dto;
    }

    public StockChartDto getDailyPrice(String stockCode) {
        // 일봉은 분봉보다 덜 민감하므로 TTL을 더 길게 잡아도 괜찮다.
        String cacheKey = "daily:" + stockCode;
        int ttlSeconds = isMarketOpen() ? 180 : 1800;

        return getCachedOrLoadChart(chartCache, cacheKey, ttlSeconds, () -> loadDailyPrice(stockCode));
    }

    private StockChartDto loadDailyPrice(String stockCode) {
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
            return emptyChart();
        }
    }

    public StockChartDto getTimePrice(String stockCode) {
        // 시간봉은 실시간성이 조금 필요하지만,
        // 내부적으로 API를 여러 번 도는 구조라 캐시 효과가 특히 크다.
        String cacheKey = "time:" + stockCode;
        int ttlSeconds = isMarketOpen() ? 30 : 600;

        return getCachedOrLoadChart(chartCache, cacheKey, ttlSeconds, () -> loadTimePrice(stockCode));
    }

    private StockChartDto loadTimePrice(String stockCode) {
        kisApi.issueToken();
        List<JsonNode> allItems = new ArrayList<>();

        String realNowTime = java.time.LocalTime.now().format(TIME_FORMAT);
        String hourParam = realNowTime.compareTo("153000") > 0 ? "153000" : realNowTime;

        try {
            int callCount = 0;
            int retryCount = 0;

            // 이 부분은 기존 로직 유지.
            // 다만 이제는 바깥에서 캐시를 타기 때문에
            // 같은 요청이 연달아 들어와도 이 루프를 매번 다시 돌 가능성이 크게 줄어든다.
            while (callCount < 13 && retryCount < 5) {
                final String hp = hourParam;
                JsonNode response = kisApi.get(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice")
                        .queryParam("FID_ETC_CLS_CODE", "")
                        .queryParam("FID_COND_MRKT_DIV_CODE", DOMESTIC_MARKET_CODE)
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .queryParam("FID_INPUT_HOUR_1", hp)
                        .queryParam("FID_PW_DATA_INCU_YN", "Y")
                        .build(), STOCK_INTRADAY_TR_ID);

                if (response != null && "1".equals(response.path("rt_cd").asText())) {
                    Thread.sleep(100);
                    retryCount++;
                    continue;
                }

                if (response == null || response.get("output2") == null) {
                    break;
                }

                JsonNode output2 = response.get("output2");
                if (!output2.isArray() || output2.size() == 0) {
                    break;
                }

                List<JsonNode> batch = new ArrayList<>();
                for (JsonNode item : output2) {
                    String itemTime = item.path("stck_cntg_hour").asText();
                    if (itemTime.compareTo(realNowTime) <= 0) {
                        batch.add(item);
                    }
                }

                if (batch.isEmpty()) {
                    String oldest = output2.get(output2.size() - 1).path("stck_cntg_hour").asText("090000");
                    if (oldest.compareTo("090000") <= 0) {
                        break;
                    }
                    hourParam = oldest;
                    callCount++;
                    continue;
                }

                allItems.addAll(batch);

                String oldestTime = output2.get(output2.size() - 1).path("stck_cntg_hour").asText("090000");
                if (oldestTime.compareTo("090000") <= 0) {
                    break;
                }

                hourParam = oldestTime;
                callCount++;
                Thread.sleep(100);
            }
        } catch (Exception e) {
        }

        if (allItems.isEmpty()) {
            return emptyChart();
        }

        Collections.reverse(allItems);
        return parseHourlyChart(allItems);
    }

    public StockChartDto getMinutePrice(String stockCode) {
        // 분봉은 장중 민감도가 높으므로 TTL을 아주 짧게 잡는다.
        String cacheKey = "minute:" + stockCode;
        int ttlSeconds = isMarketOpen() ? 15 : 300;

        return getCachedOrLoadChart(chartCache, cacheKey, ttlSeconds, () -> loadMinutePrice(stockCode));
    }

    private StockChartDto loadMinutePrice(String stockCode) {
        kisApi.issueToken();
        List<JsonNode> allItems = new ArrayList<>();

        String realNowTime = java.time.LocalTime.now().format(TIME_FORMAT);
        String hourParam = realNowTime.compareTo("153000") > 0 ? "153000" : realNowTime;

        try {
            int callCount = 0;
            int retryCount = 0;

            while (callCount < 13 && retryCount < 3) {
                final String hp = hourParam;
                JsonNode response = kisApi.get(uriBuilder -> uriBuilder
                        .path("/uapi/domestic-stock/v1/quotations/inquire-time-itemchartprice")
                        .queryParam("FID_ETC_CLS_CODE", "")
                        .queryParam("FID_COND_MRKT_DIV_CODE", DOMESTIC_MARKET_CODE)
                        .queryParam("FID_INPUT_ISCD", stockCode)
                        .queryParam("FID_INPUT_HOUR_1", hp)
                        .queryParam("FID_PW_DATA_INCU_YN", "Y")
                        .build(), STOCK_INTRADAY_TR_ID);

                if (response != null && "1".equals(response.path("rt_cd").asText())) {
                    Thread.sleep(100);
                    retryCount++;
                    continue;
                }

                if (response == null || response.get("output2") == null) {
                    break;
                }

                JsonNode output2 = response.get("output2");
                if (!output2.isArray() || output2.size() == 0) {
                    break;
                }

                for (JsonNode item : output2) {
                    String itemTime = item.path("stck_cntg_hour").asText();
                    if (itemTime.compareTo(realNowTime) <= 0) {
                        allItems.add(item);
                    }
                }

                String oldestTime = output2.get(output2.size() - 1).path("stck_cntg_hour").asText("090000");
                if (oldestTime.compareTo("090000") <= 0) {
                    break;
                }

                hourParam = oldestTime;
                callCount++;
                Thread.sleep(100);
            }
        } catch (Exception e) {
        }

        if (allItems.isEmpty()) {
            return emptyChart();
        }

        Collections.reverse(allItems);
        return parseMinuteChart(allItems);
    }

    // ?????????????????????????????????????????????????????????????
    //  KOSPI / KOSDAQ
    // ?????????????????????????????????????????????????????????????
    public StockResponseDto getKospiIndex()        { return getIndexQuote(KOSPI_CODE, "KOSPI"); }
    public StockChartDto    getKospiChart()         { return getIndexDailyChart(KOSPI_CODE); }
    public StockChartDto    getKospiTimeChart()     { return getIndexIntradayChart(KOSPI_CODE,  INDEX_HOURLY_INTERVAL); }
    public StockChartDto    getKospiMinuteChart()   { return getIndexIntradayChart(KOSPI_CODE,  INDEX_MINUTE_INTERVAL); }

    public StockResponseDto getKosdaqIndex()       { return getIndexQuote(KOSDAQ_CODE, "KOSDAQ"); }
    public StockChartDto    getKosdaqChart()        { return getIndexDailyChart(KOSDAQ_CODE); }
    public StockChartDto    getKosdaqTimeChart()    { return getIndexIntradayChart(KOSDAQ_CODE, INDEX_HOURLY_INTERVAL); }
    public StockChartDto    getKosdaqMinuteChart()  { return getIndexIntradayChart(KOSDAQ_CODE, INDEX_MINUTE_INTERVAL); }

    public List<StockResponseDto> getTopFluctuation() {
        // 상위 종목 리스트는 모든 사용자가 거의 같은 데이터를 보기 때문에
        // 짧게 캐시해도 효과가 크고 기능상 문제도 적다.
        String cacheKey = "topFluctuation";
        int ttlSeconds = isMarketOpen() ? 20 : 300;

        return getCachedOrLoadRanking(rankingCache, cacheKey, ttlSeconds, this::loadTopFluctuation);
    }

    private List<StockResponseDto> loadTopFluctuation() {
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
                    .queryParam("fid_input_cnt_1", String.valueOf(MARKET_RANK_LIMIT))
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
                result.add(toStockDto(item));
                if (result.size() >= MARKET_RANK_LIMIT) {
                    break;
                }
            }
        } catch (Exception e) {
        }

        return result;
    }

    public List<StockResponseDto> getTopByTradeAmount() {
        String cacheKey = "topTrade";
        int ttlSeconds = isMarketOpen() ? 20 : 300;

        return getCachedOrLoadRanking(rankingCache, cacheKey, ttlSeconds, this::loadTopByTradeAmount);
    }

    private List<StockResponseDto> loadTopByTradeAmount() {
        kisApi.issueToken();
        List<StockResponseDto> result = new ArrayList<>();

        try {
            JsonNode response = kisApi.get(uriBuilder -> uriBuilder
                    .path("/uapi/domestic-stock/v1/quotations/volume-rank")
                    .queryParam("fid_cond_mrkt_div_code", DOMESTIC_MARKET_CODE)
                    .queryParam("fid_cond_scr_div_code", "20171")
                    .queryParam("fid_input_iscd", "0000")
                    .queryParam("fid_input_cnt_1", String.valueOf(MARKET_RANK_LIMIT))
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
                result.add(toStockDto(item));
                if (result.size() >= MARKET_RANK_LIMIT) {
                    break;
                }
            }
        } catch (Exception e) {
        }

        return result;
    }

    // ?????????????????????????????????????????????????????????????
    //  ?대? ?ы띁 - ?몃뜳??
    // ?????????????????????????????????????????????????????????????
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
            if (output == null || output.isNull()) return dto;

            dto.setCurrentPrice(defaultZero(text(output, "bstp_nmix_prpr")));
            dto.setPriceChange(defaultZero(text(output, "bstp_nmix_prdy_vrss")));
            dto.setChangeRate(defaultZero(text(output, "bstp_nmix_prdy_ctrt")));
            dto.setOpenPrice(defaultZero(text(output, "bstp_nmix_oprc")));
            dto.setHighPrice(defaultZero(text(output, "bstp_nmix_hgpr")));
            dto.setLowPrice(defaultZero(text(output, "bstp_nmix_lwpr")));
            dto.setVolume(defaultZero(text(output, "acml_vol")));
        } catch (Exception e) {
            // System.out.println("Index quote lookup failed [" + indexCode + "]: " + e.getMessage());
        }
        return dto;
    }

    private StockChartDto getIndexDailyChart(String indexCode) {
        kisApi.issueToken();
        try {
            String toDate   = LocalDate.now().format(DATE_FORMAT);
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
            if (output == null || output.isNull()) return emptyChart();
            return parseChartFromArray(output, "stck_bsop_date", "bstp_nmix_prpr", "acml_vol");
        } catch (Exception e) {
            // System.out.println("Index daily chart lookup failed [" + indexCode + "]: " + e.getMessage());
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
            if (output == null || output.isNull()) return emptyChart();
            return parseIndexTimeChart(output);
        } catch (Exception e) {
            // System.out.println("Index intraday chart lookup failed [" + indexCode + "]: " + e.getMessage());
            return emptyChart();
        }
    }

    // ?????????????????????????????????????????????????????????????
    //  ?뚯떛 - ?쒓컙蹂?(1?쒓컙 踰꾪궥 吏묎퀎)
    // ?????????????????????????????????????????????????????????????
    private StockChartDto parseHourlyChart(List<JsonNode> items) {
        Map<String, List<Integer>> priceGroups = new LinkedHashMap<>();
        Map<String, List<Integer>> volGroups   = new LinkedHashMap<>();

        for (JsonNode item : items) {
            String raw = item.path("stck_cntg_hour").asText();
            if (raw.length() < 2) continue;
            int hh    = parseIntSafe(raw.substring(0, 2));
            String key = String.format("%02d:00", hh);       // ?? "09:00", "10:00"
            int price  = parseIntSafe(item.path("stck_prpr").asText("0"));
            int vol    = parseIntSafe(item.path("cntg_vol").asText("0"));
            priceGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(price);
            volGroups.computeIfAbsent(key,   k -> new ArrayList<>()).add(vol);
        }
        return buildOhlcvDto(priceGroups, volGroups);
    }

    // ?????????????????????????????????????????????????????????????
    //  ?뚯떛 - 遺꾨퀎 (1遺?踰꾪궥 吏묎퀎, ?쇱씤 李⑦듃??
    //  遺꾨떦 醫낃?(留덉?留?泥닿껐媛) + 嫄곕옒???⑷퀎留?諛섑솚
    //  openPrices/highPrices/lowPrices 鍮꾩썙???꾨줎?몄뿉???쇱씤 ?뚮뜑留?
    // ?????????????????????????????????????????????????????????????
    private StockChartDto parseMinuteChart(List<JsonNode> items) {
        // LinkedHashMap?쇰줈 遺?HH:mm) ?⑥쐞 吏묎퀎 (?쒖꽌 ?좎?)
        Map<String, String> minuteClose  = new LinkedHashMap<>();
        Map<String, Integer> minuteVol   = new LinkedHashMap<>();

        for (JsonNode item : items) {
            String raw = item.path("stck_cntg_hour").asText();
            if (raw.length() < 4) continue;
            String minKey = raw.substring(0, 2) + ":" + raw.substring(2, 4);
            // ?대떦 遺꾩쓽 留덉?留?泥닿껐媛 = 醫낃?
            minuteClose.put(minKey, defaultZero(item.path("stck_prpr").asText("0")));
            // 嫄곕옒???꾩쟻
            int vol = parseIntSafe(item.path("cntg_vol").asText("0"));
            minuteVol.merge(minKey, vol, Integer::sum);
        }

        List<String> labels  = new ArrayList<>(minuteClose.keySet());
        List<String> closes  = new ArrayList<>(minuteClose.values());
        List<String> volumes = new ArrayList<>();
        minuteVol.values().forEach(v -> volumes.add(String.valueOf(v)));

        StockChartDto dto = emptyChart();   // open/high/low 鍮꾩썙???쇱씤 李⑦듃 媛뺤젣
        dto.setLabels(labels);
        dto.setClosePrices(closes);
        dto.setVolumes(volumes);
        return dto;
    }

    // ?????????????????????????????????????????????????????????????
    //  ?뚯떛 - ?몃뜳???μ쨷 (KOSPI/KOSDAQ ?쒓컙쨌遺꾨큺)
    //  ???몃뜳??API??怨좉?/?媛???뱀씪 ?꾩쟻媛믪씠??罹붾뱾 遺덇?
    //    ???ㅻ뒛 ?좎쭨 ?곗씠?곕쭔 + 醫낃?留?諛섑솚 ???쇱씤 李⑦듃濡??뚮뜑留?
    // ?????????????????????????????????????????????????????????????
    private StockChartDto parseIndexTimeChart(JsonNode array) {
        List<String> labels  = new ArrayList<>();
        List<String> prices  = new ArrayList<>();
        List<String> volumes = new ArrayList<>();

        String nowTime = java.time.LocalTime.now().format(TIME_FORMAT);
        String today   = java.time.LocalDate.now().format(DATE_FORMAT);  // "YYYYMMDD"

        for (JsonNode item : array) {
            String rawDate = text(item, "stck_bsop_date");
            String rawTime = text(item, "stck_cntg_hour");

            // ?ㅻ뒛 ?좎쭨 ?곗씠?곕쭔 ?ы븿 (?댁쟾 ?좎쭨 ?곗씠?곌? ?욎씠硫??숈씪 HH:mm 以묐났 諛쒖깮)
            if (!today.equals(rawDate)) continue;
            // ?꾩옱 ?쒓컙 ?댄썑 ?곗씠???쒖쇅
            if (rawTime != null && !rawTime.isBlank() && rawTime.compareTo(nowTime) > 0) continue;

            labels.add(formatTimeOnly(rawTime));
            prices.add(defaultZero(text(item, "bstp_nmix_prpr")));
            volumes.add(defaultZero(text(item, "cntg_vol")));
        }

        Collections.reverse(labels);
        Collections.reverse(prices);
        Collections.reverse(volumes);

        StockChartDto dto = emptyChart();   // openPrices/highPrices/lowPrices ??鍮?由ъ뒪?????쇱씤 李⑦듃
        dto.setLabels(labels);
        dto.setClosePrices(prices);
        dto.setVolumes(volumes);
        return dto;
    }

    // ?????????????????????????????????????????????????????????????
    //  ?뚯떛 - ?쇰큺 怨듯넻
    // ?????????????????????????????????????????????????????????????
    private StockChartDto parseChartFromArray(JsonNode array,
                                              String dateField,
                                              String priceField,
                                              String volumeField) {
        List<String> labels     = new ArrayList<>();
        List<String> openPrices = new ArrayList<>();
        List<String> highPrices = new ArrayList<>();
        List<String> lowPrices  = new ArrayList<>();
        List<String> prices     = new ArrayList<>();
        List<String> volumes    = new ArrayList<>();

        for (JsonNode item : array) {
            labels.add(defaultZero(text(item, dateField)));
            openPrices.add(defaultZero(text(item, "stck_oprc", "bstp_nmix_oprc", priceField)));
            highPrices.add(defaultZero(text(item, "stck_hgpr", "bstp_nmix_hgpr", priceField)));
            lowPrices.add(defaultZero(text(item, "stck_lwpr", "bstp_nmix_lwpr", priceField)));
            prices.add(defaultZero(text(item, priceField)));
            volumes.add(defaultZero(text(item, volumeField)));
        }

        Collections.reverse(labels);
        Collections.reverse(openPrices);
        Collections.reverse(highPrices);
        Collections.reverse(lowPrices);
        Collections.reverse(prices);
        Collections.reverse(volumes);

        StockChartDto dto = emptyChart();
        dto.setLabels(labels);
        dto.setOpenPrices(openPrices);
        dto.setHighPrices(highPrices);
        dto.setLowPrices(lowPrices);
        dto.setClosePrices(prices);
        dto.setVolumes(volumes);
        return dto;
    }

    // ?????????????????????????????????????????????????????????????
    //  OHLCV DTO 鍮뚮뱶 (踰꾪궥 ??DTO)
    // ?????????????????????????????????????????????????????????????
    private StockChartDto buildOhlcvDto(Map<String, List<Integer>> priceGroups,
                                        Map<String, List<Integer>> volGroups) {
        List<String> labels  = new ArrayList<>();
        List<String> opens   = new ArrayList<>();
        List<String> highs   = new ArrayList<>();
        List<String> lows    = new ArrayList<>();
        List<String> closes  = new ArrayList<>();
        List<String> volumes = new ArrayList<>();

        for (Map.Entry<String, List<Integer>> entry : priceGroups.entrySet()) {
            List<Integer> p = entry.getValue();
            List<Integer> v = volGroups.getOrDefault(entry.getKey(), List.of(0));
            labels.add(entry.getKey());
            opens.add(String.valueOf(p.get(0)));
            closes.add(String.valueOf(p.get(p.size() - 1)));
            highs.add(String.valueOf(p.stream().mapToInt(Integer::intValue).max().orElse(0)));
            lows.add(String.valueOf(p.stream().mapToInt(Integer::intValue).min().orElse(0)));
            volumes.add(String.valueOf(v.stream().mapToInt(Integer::intValue).sum()));
        }

        StockChartDto dto = new StockChartDto();
        dto.setLabels(labels);
        dto.setOpenPrices(opens);
        dto.setHighPrices(highs);
        dto.setLowPrices(lows);
        dto.setClosePrices(closes);
        dto.setVolumes(volumes);
        return dto;
    }

    // ?????????????????????????????????????????????????????????????
    //  ?쒖쐞 DTO 蹂??怨듯넻
    // ?????????????????????????????????????????????????????????????
    private StockResponseDto toStockDto(JsonNode item) {
        StockResponseDto dto = emptyResponseDto();
        dto.setStockCode(defaultZero(text(item, "mksc_shrn_iscd", "stck_shrn_iscd", "iscd")));
        dto.setStockName(text(item, "hts_kor_isnm"));
        dto.setCurrentPrice(defaultZero(text(item, "stck_prpr")));
        dto.setChangeRate(defaultZero(text(item, "prdy_ctrt")));
        dto.setPriceChange(defaultZero(text(item, "prdy_vrss")));
        dto.setVolume(defaultZero(text(item, "acml_vol")));
        dto.setTradeAmount(defaultZero(text(item, "acml_tr_pbmn")));
        return dto;
    }

    // ?????????????????????????????????????????????????????????????
    //  ?좏떥
    // ?????????????????????????????????????????????????????????????
    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    private StockResponseDto emptyResponseDto() {
        StockResponseDto dto = new StockResponseDto();
        dto.setCurrentPrice("0"); dto.setOpenPrice("0"); dto.setHighPrice("0");
        dto.setLowPrice("0");     dto.setVolume("0");    dto.setPriceChange("0");
        dto.setChangeRate("0");   dto.setTradeAmount("0");
        return dto;
    }

    private StockChartDto emptyChart() {
        StockChartDto dto = new StockChartDto();
        dto.setLabels(new ArrayList<>()); dto.setOpenPrices(new ArrayList<>());
        dto.setHighPrices(new ArrayList<>()); dto.setLowPrices(new ArrayList<>());
        dto.setClosePrices(new ArrayList<>()); dto.setVolumes(new ArrayList<>());
        return dto;
    }

    private String text(JsonNode node, String... fieldNames) {
        if (node == null || fieldNames == null) return "";
        for (String f : fieldNames) {
            if (f == null || f.isBlank()) continue;
            JsonNode v = node.get(f);
            if (v == null || v.isNull()) continue;
            String t = v.asText();
            if (t != null && !t.trim().isEmpty()) return t.trim();
        }
        return "";
    }

    private String text(JsonNode node, String fieldName) {
        return text(node, new String[]{fieldName});
    }

    private String defaultZero(String value) {
        return value == null || value.isBlank() ? "0" : value;
    }

    private String formatTimeOnly(String rawTime) {
        if (rawTime == null || rawTime.length() < 4) {
            return (rawTime == null || rawTime.isBlank()) ? "-" : rawTime;
        }
        return rawTime.substring(0, 2) + ":" + rawTime.substring(2, 4);
    }

    // 장중 여부를 서비스에서도 판단해서 TTL을 다르게 적용한다.
// 장중에는 더 자주 갱신하고, 장외에는 불필요한 외부 호출을 줄이기 위함이다.
    private boolean isMarketOpen() {
        LocalDateTime now = LocalDateTime.now();
        int day = now.getDayOfWeek().getValue();
        if (day >= 6) {
            return false;
        }

        int time = now.getHour() * 100 + now.getMinute();
        return time >= 900 && time <= 1530;
    }

    // 현재가 DTO 캐시 공통 처리
// 캐시에 살아 있는 값이 있으면 그걸 복사해서 반환하고,
// 없으면 loader를 실행한 뒤 결과를 캐시에 저장한다.
    private StockResponseDto getCachedOrLoadResponse(
            ConcurrentHashMap<String, CacheEntry<StockResponseDto>> cache,
            String key,
            int ttlSeconds,
            Supplier<StockResponseDto> loader
    ) {
        CacheEntry<StockResponseDto> cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return copyResponse(cached.value);
        }

        StockResponseDto loaded = loader.get();
        if (loaded != null) {
            cache.put(key, new CacheEntry<>(copyResponse(loaded), System.currentTimeMillis() + ttlSeconds * 1000L));
        }
        return loaded != null ? loaded : emptyResponseDto();
    }

    // 차트 DTO 캐시 공통 처리
    private StockChartDto getCachedOrLoadChart(
            ConcurrentHashMap<String, CacheEntry<StockChartDto>> cache,
            String key,
            int ttlSeconds,
            Supplier<StockChartDto> loader
    ) {
        CacheEntry<StockChartDto> cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return copyChart(cached.value);
        }

        StockChartDto loaded = loader.get();
        if (loaded != null) {
            cache.put(key, new CacheEntry<>(copyChart(loaded), System.currentTimeMillis() + ttlSeconds * 1000L));
        }
        return loaded != null ? loaded : emptyChart();
    }

    // 상위 종목 리스트 캐시 공통 처리
    private List<StockResponseDto> getCachedOrLoadRanking(
            ConcurrentHashMap<String, CacheEntry<List<StockResponseDto>>> cache,
            String key,
            int ttlSeconds,
            Supplier<List<StockResponseDto>> loader
    ) {
        CacheEntry<List<StockResponseDto>> cached = cache.get(key);
        if (cached != null && !cached.isExpired()) {
            return copyResponseList(cached.value);
        }

        List<StockResponseDto> loaded = loader.get();
        if (loaded != null) {
            cache.put(key, new CacheEntry<>(copyResponseList(loaded), System.currentTimeMillis() + ttlSeconds * 1000L));
        }
        return loaded != null ? loaded : new ArrayList<>();
    }

    // 캐시 안의 객체를 그대로 반환하면
// 나중에 다른 코드가 값을 건드렸을 때 캐시 원본까지 오염될 수 있어서 복사본을 반환한다.
    private StockResponseDto copyResponse(StockResponseDto source) {
        StockResponseDto dto = new StockResponseDto();
        dto.setStockCode(source.getStockCode());
        dto.setStockName(source.getStockName());
        dto.setCurrentPrice(source.getCurrentPrice());
        dto.setOpenPrice(source.getOpenPrice());
        dto.setHighPrice(source.getHighPrice());
        dto.setLowPrice(source.getLowPrice());
        dto.setVolume(source.getVolume());
        dto.setPriceChange(source.getPriceChange());
        dto.setChangeRate(source.getChangeRate());
        dto.setTradeAmount(source.getTradeAmount());
        return dto;
    }

    private List<StockResponseDto> copyResponseList(List<StockResponseDto> source) {
        List<StockResponseDto> copied = new ArrayList<>();
        for (StockResponseDto dto : source) {
            copied.add(copyResponse(dto));
        }
        return copied;
    }

    private StockChartDto copyChart(StockChartDto source) {
        StockChartDto dto = new StockChartDto();
        dto.setLabels(new ArrayList<>(source.getLabels()));
        dto.setOpenPrices(new ArrayList<>(source.getOpenPrices()));
        dto.setHighPrices(new ArrayList<>(source.getHighPrices()));
        dto.setLowPrices(new ArrayList<>(source.getLowPrices()));
        dto.setClosePrices(new ArrayList<>(source.getClosePrices()));
        dto.setVolumes(new ArrayList<>(source.getVolumes()));
        return dto;
    }

    // TTL 기반 메모리 캐시 엔트리
    private static class CacheEntry<T> {
        private final T value;
        private final long expiresAtMillis;

        private CacheEntry(T value, long expiresAtMillis) {
            this.value = value;
            this.expiresAtMillis = expiresAtMillis;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() >= expiresAtMillis;
        }
    }
}
