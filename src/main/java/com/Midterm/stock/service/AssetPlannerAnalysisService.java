//package com.Midterm.stock.service;
//
//import com.Midterm.stock.dto.AssetPlannerAnalysisDto;
//import com.Midterm.stock.dto.AssetPlannerPredictionRequestDto;
//import com.Midterm.stock.dto.AssetPlannerPredictionResponseDto;
//import com.Midterm.stock.repository.AssetDao;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.net.URI;
//import java.net.http.HttpClient;
//import java.net.http.HttpRequest;
//import java.net.http.HttpResponse;
//import java.util.ArrayList;
//
//@Service
//public class AssetPlannerAnalysisService {
//
//    @Autowired
//    private AssetDao assetDao;
//
//    private final ObjectMapper objectMapper = new ObjectMapper();
//
//    public AssetPlannerAnalysisDto analyzeAndSave(AssetPlannerAnalysisDto formDto) throws Exception {
//
//        AssetPlannerPredictionRequestDto requestDto = new AssetPlannerPredictionRequestDto();
//        requestDto.setCurrent_asset(formDto.getCurrentAsset());
//        requestDto.setMonthly_income(formDto.getMonthlyIncome());
//        requestDto.setMonthly_expense(formDto.getMonthlyExpense());
//        requestDto.setGoal_amount(formDto.getGoalAmount());
//        requestDto.setGoal_months(formDto.getGoalMonths());
//
//        // 화면에서 expectedReturn을 % 단위로 받는 구조였다면 /100 유지
//        // 지금은 입력을 안 받으므로 0.0 -> 0.0 전송
//        requestDto.setExpected_return(formDto.getExpectedReturn() / 100.0);
//
//        requestDto.setAge(formDto.getAge());
//        requestDto.setJob_type(formDto.getJobType());
//        requestDto.setRisk_preference(formDto.getRiskPreference());
//
//        String requestBody = objectMapper.writeValueAsString(requestDto);
//        // 이 로그를 찍어서 콘솔에 나오는 JSON 형태를 보세요.
//        // {"current_asset": 0, ...} 처럼 언더바(_)가 포함되어 나와야 성공입니다.
//        System.out.println("FastAPI 전송 데이터: " + requestBody);
//        HttpClient client = HttpClient.newHttpClient();
//
//        HttpRequest request = HttpRequest.newBuilder()
//                .uri(URI.create("http://127.0.0.1:8001/api/asset/predict"))
//                .header("Content-Type", "application/json")
//                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
//                .build();
//
//        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
//
//        if (response.statusCode() != 200) {
//            throw new RuntimeException("FastAPI 호출 실패: " + response.body());
//        }
//
//        AssetPlannerPredictionResponseDto responseDto =
//                objectMapper.readValue(response.body(), AssetPlannerPredictionResponseDto.class);
//
//        AssetPlannerAnalysisDto resultDto = new AssetPlannerAnalysisDto();
//
//        // 입력값 복사
//        resultDto.setCurrentAsset(formDto.getCurrentAsset());
//        resultDto.setMonthlyIncome(formDto.getMonthlyIncome());
//        resultDto.setMonthlyExpense(formDto.getMonthlyExpense());
//        resultDto.setGoalAmount(formDto.getGoalAmount());
//        resultDto.setGoalMonths(formDto.getGoalMonths());
//        resultDto.setExpectedReturn(formDto.getExpectedReturn());
//        resultDto.setAge(formDto.getAge());
//        resultDto.setJobType(formDto.getJobType());
//        resultDto.setRiskPreference(formDto.getRiskPreference());
//
//        // 기본 월 저축액 계산
//        long monthlySaving = formDto.getMonthlyIncome() - formDto.getMonthlyExpense();
//        resultDto.setMonthlySaving(monthlySaving);
//
//        // FastAPI 응답에서 input_summary가 있으면 그 값으로 덮어씀
//        if (responseDto.getInput_summary() != null) {
//            resultDto.setMonthlySaving(responseDto.getInput_summary().getMonthly_saving());
//        }
//
//        // 예측 결과 복사
//        resultDto.setPrediction(responseDto.getPrediction());
//        resultDto.setPredictionLabel(responseDto.getPrediction_label());
//        resultDto.setToneTitle(responseDto.getTone_title());
//
//        resultDto.setModelPrediction(responseDto.getModel_prediction());
//        resultDto.setModelProbability(responseDto.getModel_probability());
//
//        // 분석 결과 복사
//        if (responseDto.getAnalysis() != null) {
//            resultDto.setRequiredMonthlySaving(responseDto.getAnalysis().getRequired_monthly_saving());
//            resultDto.setEstimatedFinalAsset(responseDto.getAnalysis().getEstimated_final_asset());
//            resultDto.setGoalGap(responseDto.getAnalysis().getGoal_gap());
//            resultDto.setMessage(responseDto.getAnalysis().getMessage());
//        } else {
//            resultDto.setRequiredMonthlySaving(0);
//            resultDto.setEstimatedFinalAsset(0);
//            resultDto.setGoalGap(0);
//            resultDto.setMessage("분석 결과를 받아오지 못했습니다.");
//        }
//
//        // DB 저장
//        assetDao.insertAnalysisHistory(resultDto);
//
//        return resultDto;
//    }
//
//    public ArrayList<AssetPlannerAnalysisDto> getHistory() {
//        return assetDao.getAnalysisHistory();
//    }
//}



package com.Midterm.stock.service;

import com.Midterm.stock.dto.AssetPlannerAnalysisDto;
import com.Midterm.stock.dto.AssetPlannerPredictionRequestDto;
import com.Midterm.stock.dto.AssetPlannerPredictionResponseDto;
import com.Midterm.stock.repository.AssetDao;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;


@Service
public class AssetPlannerAnalysisService {

    @Autowired
    private AssetDao assetDao;

    private final ObjectMapper objectMapper;

    public AssetPlannerAnalysisService() {
        objectMapper = new ObjectMapper();
        // camelCase -> snake_case 자동 변환
        objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    public AssetPlannerAnalysisDto analyzeAndSave(AssetPlannerAnalysisDto formDto) throws Exception {

        // 1. AssetPlannerAnalysisDto -> AssetPlannerPredictionRequestDto 변환
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

        // 2. JSON 변환
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AssetPlannerPredictionRequestDto> entity =
                new HttpEntity<>(requestDto, headers);

        ResponseEntity<AssetPlannerPredictionResponseDto> response =
                restTemplate.postForEntity(
                        "http://127.0.0.1:8001/api/asset/predict",
                        entity,
                        AssetPlannerPredictionResponseDto.class
                );

        AssetPlannerPredictionResponseDto responseDto = response.getBody();
        System.out.println("MONTHLY SAVING: " + responseDto.getInput_summary().getMonthly_saving());

        if (responseDto == null) {
            throw new RuntimeException("FastAPI 응답이 비어있음");
        }
//        String requestBody = objectMapper.writeValueAsString(requestDto);
//        System.out.println("FastAPI 전송 데이터: " + requestBody);
//
//        // 3. FastAPI 호출
//        HttpClient client = HttpClient.newHttpClient();
//
//
//
//        HttpRequest request = HttpRequest.newBuilder()
//                .uri(URI.create("http://127.0.0.1:8001/api/asset/predict"))
//                .header("Content-Type", "application/json")
//                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
//                .build();
//
//        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
//
//        if (response.statusCode() != 200) {
//            throw new RuntimeException("FastAPI 호출 실패: " + response.body());
//        }
//
//        // 4. FastAPI 응답 파싱
//        AssetPlannerPredictionResponseDto responseDto =
//                objectMapper.readValue(response.body(), AssetPlannerPredictionResponseDto.class);

        // 5. 결과 DTO 생성 (AssetPlannerAnalysisDto)
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

        // 월 저축액 계산
        long monthlySaving = formDto.getMonthlyIncome() - formDto.getMonthlyExpense();
        resultDto.setMonthlySaving(monthlySaving);

        if (responseDto.getInput_summary() != null) {
            resultDto.setMonthlySaving(responseDto.getInput_summary().getMonthly_saving());
        }

        // FastAPI 예측 결과 복사
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
        assetDao.insertAnalysisHistory(resultDto);

        return resultDto;
    }

    public ArrayList<AssetPlannerAnalysisDto> getHistory() {
        return assetDao.getAnalysisHistory();
    }
}