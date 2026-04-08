package com.Midterm.stock.service;

import com.Midterm.stock.dto.AssetPlannerAnalysisDto;
import com.Midterm.stock.dto.AssetPlannerPredictionRequestDto;
import com.Midterm.stock.dto.AssetPlannerPredictionResponseDto;
import com.Midterm.stock.repository.AssetDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;

@Service
public class AssetPlannerAnalysisService {

    @Autowired
    private AssetDao assetDao;

    private final ObjectMapper objectMapper;
    private final String predictionApiUrl;

    public AssetPlannerAnalysisService(@Value("${asset.prediction.api-url:http://127.0.0.1:9000/api/asset/predict}") String predictionApiUrl) {
        objectMapper = new ObjectMapper();
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        this.predictionApiUrl = predictionApiUrl;
    }

    public AssetPlannerAnalysisDto analyzeAndSave(AssetPlannerAnalysisDto formDto, int loginNum) throws Exception {

        AssetPlannerPredictionRequestDto requestDto = new AssetPlannerPredictionRequestDto();
        requestDto.setCurrent_asset(formDto.getCurrentAsset());
        requestDto.setMonthly_income(formDto.getMonthlyIncome());
        requestDto.setMonthly_expense(formDto.getMonthlyExpense());
        requestDto.setGoal_amount(formDto.getGoalAmount());
        requestDto.setGoal_months(formDto.getGoalMonths());
        requestDto.setExpected_return(formDto.getExpectedReturn());
        requestDto.setAge(formDto.getAge());
        requestDto.setJob_type(formDto.getJobType());
        requestDto.setRisk_preference(formDto.getRiskPreference());

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AssetPlannerPredictionRequestDto> entity =
                new HttpEntity<>(requestDto, headers);


        ResponseEntity<AssetPlannerPredictionResponseDto> response;
        try {
            response = restTemplate.postForEntity(
                    predictionApiUrl,
                    entity,
                    AssetPlannerPredictionResponseDto.class
            );
        } catch (RestClientException e) {
            throw new RuntimeException("자산 예측 API 호출에 실패했습니다. FastAPI 서버 상태와 주소를 확인해주세요: " + predictionApiUrl, e);
        }


        AssetPlannerPredictionResponseDto responseDto = response.getBody();

        if (responseDto == null) {
            throw new RuntimeException("FastAPI 응답이 비어있음");
        }

        if (responseDto.getInput_summary() != null) {
            System.out.println("MONTHLY CASHFLOW: " + responseDto.getInput_summary().getMonthly_cashflow());
        }

        System.out.println("model_prediction = " + responseDto.getModel_prediction());
        System.out.println("model_prediction_label = " + responseDto.getModel_prediction_label());
        System.out.println("model_probability = " + responseDto.getModel_probability());

        AssetPlannerAnalysisDto resultDto = new AssetPlannerAnalysisDto();
        resultDto.setCurrentAsset(formDto.getCurrentAsset());
        resultDto.setMonthlyIncome(formDto.getMonthlyIncome());
        resultDto.setMonthlyExpense(formDto.getMonthlyExpense());
        resultDto.setGoalAmount(formDto.getGoalAmount());
        resultDto.setGoalMonths(formDto.getGoalMonths());
        resultDto.setExpectedReturn(formDto.getExpectedReturn());
        resultDto.setAge(formDto.getAge());
        resultDto.setJobType(formDto.getJobType());
        resultDto.setRiskPreference(formDto.getRiskPreference());

        // FastAPI의 monthly_cashflow 값을, 자바/DB에서는 기존 monthlySaving 필드에 저장
        long monthlyCashflow = formDto.getMonthlyIncome() - formDto.getMonthlyExpense();
        resultDto.setMonthlySaving(monthlyCashflow);

        if (responseDto.getInput_summary() != null) {
            resultDto.setMonthlySaving(responseDto.getInput_summary().getMonthly_cashflow());
        }

        resultDto.setPrediction(responseDto.getPrediction());
        // getPrediction_label 추가
        resultDto.setPredictionLabel(responseDto.getPrediction_label());
        resultDto.setToneTitle(responseDto.getTone_title());

        resultDto.setModelPrediction(responseDto.getModel_prediction());
        resultDto.setModelPredictionLabel(responseDto.getModel_prediction_label());
        resultDto.setModelProbability(responseDto.getModel_probability());

        System.out.println("resultDto modelPredictionLabel = " + resultDto.getModelPredictionLabel());

        if (responseDto.getAnalysis() != null) {
            // FastAPI의 required_monthly_cashflow 값을, 자바/DB에서는 기존 requiredMonthlySaving 필드에 저장
            resultDto.setRequiredMonthlySaving(responseDto.getAnalysis().getRequired_monthly_cashflow());
            resultDto.setEstimatedFinalAsset(responseDto.getAnalysis().getEstimated_final_asset());
            resultDto.setGoalGap(responseDto.getAnalysis().getGoal_gap());
            resultDto.setMessage(responseDto.getAnalysis().getMessage());
        } else {
            resultDto.setRequiredMonthlySaving(0);
            resultDto.setEstimatedFinalAsset(0);
            resultDto.setGoalGap(0);
            resultDto.setMessage("분석 결과를 받아오지 못했습니다.");
        }

        assetDao.insertAnalysisHistory(resultDto, loginNum);

        return resultDto;
    }

    public ArrayList<AssetPlannerAnalysisDto> getHistory(int loginNum) {
        return assetDao.getAnalysisHistory(loginNum);
    }
}
