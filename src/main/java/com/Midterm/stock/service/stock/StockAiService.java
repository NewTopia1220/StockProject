package com.Midterm.stock.service.stock;

import com.Midterm.stock.dto.AiPredictionDto;
import com.Midterm.stock.dto.NewsDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Python 모델 호출 서비스
 * - predict.py: 종목 단위 익일 상승 확률
 * - article_predict.py: 기사 단위 영향도(모델 폴백)
 */
@Slf4j
@Service
public class StockAiService {

    @Value("${python.path:python}")
    private String pythonPath;

    @Value("${ai.predict.script:predict.py}")
    private String predictScript;

    @Value("${ai.predict.model:lgbm_model.pkl}")
    private String modelPath;

    @Value("${ai.predict.sqlite:news_data.db}")
    private String sqlitePath;

    @Value("${ai.predict.oracle-company:oracle_company.csv}")
    private String oracleCompanyPath;

    @Value("${ai.predict.oracle-sector:oracle_sector.csv}")
    private String oracleSectorPath;

    @Value("${ai.article-impact.script:article_predict.py}")
    private String articleImpactScript;

    @Value("${ai.article-impact.model:article_lgbm_model.pkl}")
    private String articleImpactModelPath;

    @Value("${ai.predict.timeout:30}")
    private int timeoutSeconds;

    @Value("${ai.predict.cache-seconds:600}")
    private int stockPredictCacheSeconds;

    @Value("${ai.article-impact.cache-seconds:43200}")
    private int articleImpactCacheSeconds;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, CacheEntry<AiPredictionDto>> stockPredictCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheEntry<Double>> articleImpactCache = new ConcurrentHashMap<>();

    public AiPredictionDto predict(String stockCode) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return predict(stockCode, today);
    }

    public AiPredictionDto predict(String stockCode, String date) {
        String cacheKey = buildStockPredictCacheKey(stockCode, date);
        AiPredictionDto cached = getCached(stockPredictCache, cacheKey);
        if (cached != null) {
            return cached;
        }

        try {
            String stdout = runProcess(buildStockPredictProcess(stockCode, date), "predict.py");
            if (stdout == null) {
                return errorDto("AI prediction result is empty");
            }

            AiPredictionDto dto = objectMapper.readValue(stdout, AiPredictionDto.class);
            if (dto.getError() != null) {
                log.warn("[AI] predict.py error: {}", dto.getError());
            } else {
                putCached(stockPredictCache, cacheKey, dto, stockPredictCacheSeconds);
            }
            return dto;

        } catch (Exception e) {
            log.error("[AI] predict.py failed: {}", e.getMessage(), e);
            return errorDto("AI prediction error: " + e.getMessage());
        }
    }

    /**
     * DB 영향도 값이 없을 때 기사 단위 모델값으로 폴백.
     * @return 영향도 퍼센트(-100 ~ 100) 또는 null
     */
    public Double predictArticleImpact(NewsDto dto) {
        if (dto == null || isBlank(dto.getStockCode()) || isBlank(dto.getPubDate())) {
            return null;
        }

        String cacheKey = buildArticleCacheKey(dto);
        Double cached = getCached(articleImpactCache, cacheKey);
        if (cached != null) {
            return cached;
        }

        if (!existsPath(articleImpactScript) || !existsPath(articleImpactModelPath)) {
            return null;
        }

        try {
            String stdout = runProcess(buildArticleImpactProcess(dto), "article_predict.py");
            if (stdout == null) {
                return null;
            }

            JsonNode root = objectMapper.readTree(stdout);
            if (root.hasNonNull("error")) {
                log.warn("[AI] article_predict.py error: {}", root.path("error").asText());
                return null;
            }

            if (!root.path("impact_percent").isNumber()) {
                return null;
            }

            Double impact = root.path("impact_percent").asDouble();
            putCached(articleImpactCache, cacheKey, impact, articleImpactCacheSeconds);
            return impact;
        } catch (Exception e) {
            log.warn("[AI] article_predict.py failed: {}", e.getMessage());
            return null;
        }
    }

    // 종목 예측과 기사 영향도 예측이 같은 규칙으로 실행되도록 ProcessBuilder 생성을 분리했다.
    private ProcessBuilder buildStockPredictProcess(String stockCode, String date) {
        return new ProcessBuilder(
                resolvePythonExecutable(),
                predictScript,
                "--code", stockCode,
                "--date", date,
                "--model", modelPath,
                "--sqlite", sqlitePath,
                "--oracle-company", oracleCompanyPath,
                "--oracle-sector", oracleSectorPath
        );
    }

    private ProcessBuilder buildArticleImpactProcess(NewsDto dto) {
        return new ProcessBuilder(
                resolvePythonExecutable(),
                articleImpactScript,
                "--code", dto.getStockCode(),
                "--date", dto.getPubDate(),
                "--sentiment", safe(dto.getSentiment()),
                "--clickbait-prob", String.valueOf(dto.getClickbaitProb()),
                "--type-prob", String.valueOf(dto.getTypeProb()),
                "--article-type", safe(dto.getArticleType()),
                "--title", safe(dto.getTitle()),
                "--summary", safe(dto.getSummary()),
                "--model", articleImpactModelPath,
                "--sqlite", sqlitePath,
                "--oracle-company", oracleCompanyPath,
                "--oracle-sector", oracleSectorPath
        );
    }

    private String runProcess(ProcessBuilder pb, String scriptLabel) throws Exception {
        // Python 쪽 stdout/stderr를 UTF-8로 고정해서 한글 깨짐과 JSON 파싱 오류를 줄인다.
        pb.redirectErrorStream(false);
        pb.environment().put("PYTHONIOENCODING", "UTF-8");
        pb.environment().put("PYTHONUTF8", "1");
        long start = System.currentTimeMillis();

        Process process = pb.start();

        StringBuilder stdout = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stdout.append(line);
            }
        }

        new Thread(() -> {
            try (BufferedReader errReader = new BufferedReader(
                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = errReader.readLine()) != null) {
                    log.debug("[AI-py] {}", line);
                }
            } catch (Exception ignored) {
            }
        }).start();

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.warn("[AI] {} timeout ({}s)", scriptLabel, timeoutSeconds);
            return null;
        }

        long elapsed = System.currentTimeMillis() - start;
        String preview = stdout.toString();
        if (preview.length() > 140) {
            preview = preview.substring(0, 140);
        }
        log.info("[AI] {} done {}ms: {}", scriptLabel, elapsed, preview);

        if (stdout.length() == 0) {
            log.warn("[AI] {} stdout empty", scriptLabel);
            return null;
        }
        return stdout.toString();
    }

    private AiPredictionDto errorDto(String message) {
        AiPredictionDto dto = new AiPredictionDto();
        dto.setError(message);
        dto.setMessage(message);
        return dto;
    }

    private boolean existsPath(String pathText) {
        if (isBlank(pathText)) {
            return false;
        }
        try {
            return Files.exists(Path.of(pathText));
        } catch (Exception e) {
            return false;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // 팀원 환경마다 PYTHON_PATH가 비어 있어도, 프로젝트 루트의 가상환경이 있으면 그쪽을 우선 사용한다.
    private String resolvePythonExecutable() {
        if (!isBlank(pythonPath) && !isGenericPythonCommand(pythonPath)) {
            return pythonPath;
        }

        String projectRoot = System.getProperty("user.dir");
        String[] candidates = {
            Path.of(projectRoot, ".venv", "Scripts", "python.exe").toString(),
            Path.of(projectRoot, "python", ".venv", "Scripts", "python.exe").toString(),
            Path.of(projectRoot, ".venv", "bin", "python").toString(),
            Path.of(projectRoot, "python", ".venv", "bin", "python").toString()
        };

        for (String candidate : candidates) {
            if (existsPath(candidate)) {
                return candidate;
            }
        }

        return isBlank(pythonPath) ? "python" : pythonPath;
    }

    private boolean isGenericPythonCommand(String value) {
        if (isBlank(value)) {
            return true;
        }

        String normalized = value.trim().replace('\\', '/').toLowerCase();
        return normalized.equals("python")
            || normalized.equals("python.exe")
            || normalized.equals("py")
            || normalized.equals("py.exe");
    }

    private String buildArticleCacheKey(NewsDto dto) {
        if (!isBlank(dto.getLink())) {
            return "LINK|" + dto.getLink() + "|" + buildPathSignature(articleImpactModelPath, sqlitePath, oracleCompanyPath, oracleSectorPath);
        }
        return "ROW|" + safe(dto.getStockCode()) + "|" + safe(dto.getPubDate()) + "|" + safe(dto.getTitle())
            + "|" + buildPathSignature(articleImpactModelPath, sqlitePath, oracleCompanyPath, oracleSectorPath);
    }

    private String buildStockPredictCacheKey(String stockCode, String date) {
        return stockCode + "|" + date + "|" + buildPathSignature(modelPath, sqlitePath, oracleCompanyPath, oracleSectorPath);
    }

    // 모델이나 원본 데이터가 교체되면 캐시가 즉시 무효화되도록 파일 시그니처를 키에 포함한다.
    private String buildPathSignature(String... pathTexts) {
        StringBuilder signature = new StringBuilder();
        for (String pathText : pathTexts) {
            signature.append('|').append(resolvePathStamp(pathText));
        }
        return signature.toString();
    }

    private String resolvePathStamp(String pathText) {
        if (isBlank(pathText)) {
            return "missing";
        }
        try {
            Path path = Path.of(pathText);
            if (!Files.exists(path)) {
                return "missing";
            }
            return Files.getLastModifiedTime(path).toMillis() + ":" + Files.size(path);
        } catch (Exception e) {
            return "unknown";
        }
    }

    private <T> T getCached(ConcurrentHashMap<String, CacheEntry<T>> cache, String key) {
        CacheEntry<T> entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired()) {
            cache.remove(key, entry);
            return null;
        }
        return entry.value;
    }

    private <T> void putCached(
        ConcurrentHashMap<String, CacheEntry<T>> cache,
        String key,
        T value,
        int ttlSeconds
    ) {
        if (value == null) {
            return;
        }
        long ttl = Math.max(1, ttlSeconds) * 1000L;
        cache.put(key, new CacheEntry<>(value, System.currentTimeMillis() + ttl));
    }

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
