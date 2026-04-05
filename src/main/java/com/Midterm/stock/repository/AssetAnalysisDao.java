package com.Midterm.stock.repository;

import com.Midterm.stock.dto.AssetAnalysisDto;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;

@Repository
public class AssetAnalysisDao {

    private String driver = "oracle.jdbc.OracleDriver";
    private String url = "jdbc:oracle:thin:@stoxle_high?TNS_ADMIN=C:/oraclepw";
    private String id = "ADMIN";
    private String pw = "Heeyoun1220!";

    private Connection conn = null;
    private PreparedStatement pstmt = null;
    private ResultSet rs = null;

    public AssetAnalysisDao() {
        System.out.println("AssetAnalysisDao 생성자 호출 - 클라우드 설정 시작");
        try {
            Class.forName(driver);
            System.setProperty(
                    "oracle.net.wallet_location",
                    "(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=C:/oraclepw)))"
            );
            System.out.println("AssetAnalysisDao 드라이버 로드 및 클라우드 지갑 설정 성공");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public Connection connect() {
        try {
            conn = DriverManager.getConnection(url, id, pw);
            System.out.println("오라클 클라우드 DB 접속 성공!");
        } catch (SQLException e) {
            System.err.println("DB 접속 실패: " + e.getMessage());
            e.printStackTrace();
        }
        return conn;
    }

    public int insertAnalysisHistory(AssetAnalysisDto dto) {
        connect();
        int count = -1;

        String sql = "insert into asset_analysis_history ("
                + "analysis_id, current_asset, monthly_income, monthly_expense, monthly_saving, "
                + "goal_amount, goal_months, expected_return, age, job_type, risk_preference, "
                + "prediction, prediction_label, tone_title, model_prediction, model_probability, "
                + "required_monthly_saving, estimated_final_asset, goal_gap, message, created_at"
                + ") values ("
                + "asset_analysis_history_seq.nextval, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, sysdate"
                + ")";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setLong(1, dto.getCurrentAsset());
            pstmt.setLong(2, dto.getMonthlyIncome());
            pstmt.setLong(3, dto.getMonthlyExpense());
            pstmt.setLong(4, dto.getMonthlySaving());
            pstmt.setLong(5, dto.getGoalAmount());
            pstmt.setInt(6, dto.getGoalMonths());
            pstmt.setDouble(7, dto.getExpectedReturn());
            pstmt.setInt(8, dto.getAge());
            pstmt.setString(9, dto.getJobType());
            pstmt.setString(10, dto.getRiskPreference());

            pstmt.setInt(11, dto.getPrediction());
            pstmt.setString(12, dto.getPredictionLabel());
            pstmt.setString(13, dto.getToneTitle());

            pstmt.setInt(14, dto.getModelPrediction());
            pstmt.setDouble(15, dto.getModelProbability());

            pstmt.setLong(16, dto.getRequiredMonthlySaving());
            pstmt.setLong(17, dto.getEstimatedFinalAsset());
            pstmt.setLong(18, dto.getGoalGap());
            pstmt.setString(19, dto.getMessage());

            count = pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return count;
    }

    public ArrayList<AssetAnalysisDto> getAnalysisHistory() {
        connect();
        ArrayList<AssetAnalysisDto> lists = new ArrayList<>();

        String sql = "select analysis_id, current_asset, monthly_income, monthly_expense, monthly_saving, "
                + "goal_amount, goal_months, expected_return, age, job_type, risk_preference, "
                + "prediction, prediction_label, tone_title, model_prediction, model_probability, "
                + "required_monthly_saving, estimated_final_asset, goal_gap, message, created_at "
                + "from asset_analysis_history "
                + "order by analysis_id desc";

        try {
            pstmt = conn.prepareStatement(sql);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                AssetAnalysisDto dto = new AssetAnalysisDto();

                dto.setAnalysisId(rs.getInt("analysis_id"));
                dto.setCurrentAsset(rs.getLong("current_asset"));
                dto.setMonthlyIncome(rs.getLong("monthly_income"));
                dto.setMonthlyExpense(rs.getLong("monthly_expense"));
                dto.setMonthlySaving(rs.getLong("monthly_saving"));
                dto.setGoalAmount(rs.getLong("goal_amount"));
                dto.setGoalMonths(rs.getInt("goal_months"));
                dto.setExpectedReturn(rs.getDouble("expected_return"));
                dto.setAge(rs.getInt("age"));
                dto.setJobType(rs.getString("job_type"));
                dto.setRiskPreference(rs.getString("risk_preference"));

                dto.setPrediction(rs.getInt("prediction"));
                dto.setPredictionLabel(rs.getString("prediction_label"));
                dto.setToneTitle(rs.getString("tone_title"));

                dto.setModelPrediction(rs.getInt("model_prediction"));
                dto.setModelProbability(rs.getDouble("model_probability"));

                dto.setRequiredMonthlySaving(rs.getLong("required_monthly_saving"));
                dto.setEstimatedFinalAsset(rs.getLong("estimated_final_asset"));
                dto.setGoalGap(rs.getLong("goal_gap"));
                dto.setMessage(rs.getString("message"));
                dto.setCreatedAt(String.valueOf(rs.getTimestamp("created_at")));

                lists.add(dto);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return lists;
    }

    private void closeAll() {
        try {
            if (rs != null) rs.close();
            if (pstmt != null) pstmt.close();
            if (conn != null) conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}