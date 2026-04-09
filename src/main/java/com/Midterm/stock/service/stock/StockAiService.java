package com.Midterm.stock.service.stock;

import com.Midterm.stock.dto.AiPredictionDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * Calls python/ml/predict.py and maps JSON stdout to AiPredictionDto.
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

    @Value("${ai.predict.timeout:30}")
    private int timeoutSeconds;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiPredictionDto predict(String stockCode) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        return predict(stockCode, today);
    }

    public AiPredictionDto predict(String stockCode, String date) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                pythonPath,
                predictScript,
                "--code", stockCode,
                "--date", date,
                "--model", modelPath,
                "--sqlite", sqlitePath,
                "--oracle-company", oracleCompanyPath,
                "--oracle-sector", oracleSectorPath
            );
            pb.redirectErrorStream(false);

            log.info("[AI] predict.py call: code={} date={}", stockCode, date);
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
                log.warn("[AI] predict.py timeout ({}s)", timeoutSeconds);
                return errorDto("AI prediction timeout");
            }

            long elapsed = System.currentTimeMillis() - start;
            String preview = stdout.toString();
            if (preview.length() > 120) {
                preview = preview.substring(0, 120);
            }
            log.info("[AI] done {}ms: {}", elapsed, preview);

            if (stdout.length() == 0) {
                log.warn("[AI] predict.py stdout empty");
                return errorDto("AI prediction result is empty");
            }

            AiPredictionDto dto = objectMapper.readValue(stdout.toString(), AiPredictionDto.class);
            if (dto.getError() != null) {
                log.warn("[AI] predict.py error: {}", dto.getError());
            }
            return dto;

        } catch (Exception e) {
            log.error("[AI] predict.py failed: {}", e.getMessage(), e);
            return errorDto("AI prediction error: " + e.getMessage());
        }
    }

    private AiPredictionDto errorDto(String message) {
        AiPredictionDto dto = new AiPredictionDto();
        dto.setError(message);
        dto.setMessage(message);
        return dto;
    }
}
