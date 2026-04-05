package com.Midterm.stock.service;

import com.Midterm.stock.dto.AssetAnalysisDto;
import com.Midterm.stock.dto.AssetPredictionRequestDto;
import com.Midterm.stock.dto.AssetPredictionResponseDto;
import com.Midterm.stock.repository.AssetAnalysisDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;

@Service
public class AssetAnalysisService {

    @Autowired
    private AssetAnalysisDao assetAnalysisDao;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AssetAnalysisDto analyzeAndSave(AssetAnalysisDto formDto) throws Exception {

        AssetPredictionRequestDto requestDto = new AssetPredictionRequestDto();
        requestDto.setCurrent_asset(formDto.getCurrentAsset());
        requestDto.setMonthly_income(formDto.getMonthlyIncome());
        requestDto.setMonthly_expense(formDto.getMonthlyExpense());
        requestDto.setGoal_amount(formDto.getGoalAmount());
        requestDto.setGoal_months(formDto.getGoalMonths());

        // 화면에서 expectedReturn을 % 단위로 받는 구조였다면 /100 유지
        // 지금은 입력을 안 받으므로 0.0 -> 0.0 전송
        requestDto.setExpected_return(formDto.getExpectedReturn() / 100.0);

        requestDto.setAge(formDto.getAge());
        requestDto.setJob_type(formDto.getJobType());
        requestDto.setRisk_preference(formDto.getRiskPreference());

        String requestBody = objectMapper.writeValueAsString(requestDto);

        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:8000/api/asset/predict"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("FastAPI 호출 실패: " + response.body());
        }

        AssetPredictionResponseDto responseDto =
                objectMapper.readValue(response.body(), AssetPredictionResponseDto.class);

        AssetAnalysisDto resultDto = new AssetAnalysisDto();

        // 입력값 복사
        resultDto.setCurrentAsset(formDto.getCurrentAsset());
        resultDto.setMonthlyIncome(formDto.getMonthlyIncome());
        resultDto.setMonthlyExpense(formDto.getMonthlyExpense());
        resultDto.setGoalAmount(formDto.getGoalAmount());
        resultDto.setGoalMonths(formDto.getGoalMonths());
        resultDto.setExpectedReturn(formDto.getExpectedReturn());
        resultDto.setAge(formDto.getAge());
        resultDto.setJobType(formDto.getJobType());
        resultDto.setRiskPreference(formDto.getRiskPreference());

        // 기본 월 저축액 계산
        long monthlySaving = formDto.getMonthlyIncome() - formDto.getMonthlyExpense();
        resultDto.setMonthlySaving(monthlySaving);

        // FastAPI 응답에서 input_summary가 있으면 그 값으로 덮어씀
        if (responseDto.getInput_summary() != null) {
            resultDto.setMonthlySaving(responseDto.getInput_summary().getMonthly_saving());
        }

        // 예측 결과 복사
        resultDto.setPrediction(responseDto.getPrediction());
        resultDto.setPredictionLabel(responseDto.getPrediction_label());
        resultDto.setToneTitle(responseDto.getTone_title());

        resultDto.setModelPrediction(responseDto.getModel_prediction());
        resultDto.setModelProbability(responseDto.getModel_probability());

        // 분석 결과 복사
        if (responseDto.getAnalysis() != null) {
            resultDto.setRequiredMonthlySaving(responseDto.getAnalysis().getRequired_monthly_saving());
            resultDto.setEstimatedFinalAsset(responseDto.getAnalysis().getEstimated_final_asset());
            resultDto.setGoalGap(responseDto.getAnalysis().getGoal_gap());
            resultDto.setMessage(responseDto.getAnalysis().getMessage());
        } else {
            resultDto.setRequiredMonthlySaving(0);
            resultDto.setEstimatedFinalAsset(0);
            resultDto.setGoalGap(0);
            resultDto.setMessage("분석 결과를 받아오지 못했습니다.");
        }

        // DB 저장
        assetAnalysisDao.insertAnalysisHistory(resultDto);

        return resultDto;
    }

    public ArrayList<AssetAnalysisDto> getHistory() {
        return assetAnalysisDao.getAnalysisHistory();
    }
}