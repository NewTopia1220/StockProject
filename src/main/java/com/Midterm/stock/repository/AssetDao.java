package com.Midterm.stock.repository;

import com.Midterm.stock.dto.AssetDto;
import com.Midterm.stock.dto.AssetPlannerAnalysisDto;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.*;

@Repository
public class AssetDao {


    // 1. 오라클 클라우드 접속 정보 (경로는 반드시 슬래시 / 사용)
    private String driver = "oracle.jdbc.OracleDriver";
    private String url = "jdbc:oracle:thin:@stoxle_high?TNS_ADMIN=C:/oraclepw";
    private String id = "ADMIN";
    private String pw = "Heeyoun1220!";

    // 스프링 빈은 싱글톤이므로, 필드에 변수를 두기보다 메서드 안에서 로컬로 사용하는 것이 안전하지만
    // 기존 코드 스타일을 유지하며 수정해 드립니다.
    private Connection conn = null;
    private PreparedStatement pstmt = null;
    private ResultSet rs = null;

    // 생성자: 드라이버 로딩 및 지갑 경로 설정
    public AssetDao() {
        System.out.println("AssetDao 생성자 호출 - 클라우드 설정 시작");
        try {
            Class.forName(driver);
            // ⭐️ 핵심: 자바 시스템에 지갑(Wallet) 위치를 강제로 입력합니다.
            System.setProperty("oracle.net.wallet_location", "(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=C:/oraclepw)))");
            System.out.println("드라이버 로드 및 클라우드 지갑 설정 성공");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    // 계정 접속
    public Connection connect() {
        try {
            // 이제 url, id, pw가 클라우드 정보를 바라봅니다.
            conn = DriverManager.getConnection(url, id, pw);
            System.out.println("오라클 클라우드 DB 접속 성공!");
        } catch (SQLException e) {
            System.err.println("DB 접속 실패: " + e.getMessage());
            e.printStackTrace();
        }
        return conn;
    }


    // dashboard - 최근 소비 조회 메서드
    public List<AssetDto> getRecentTransactionsByMonth(int month) {
        List<AssetDto> list = new ArrayList<>();
        conn = connect();

        try {

            String sql = "SELECT month, transaction_date, amount, vendor, category " +
                    "FROM asset_data " +
                    "where month = ?" +
                    "ORDER BY transaction_date DESC FETCH FIRST 10 ROWS ONLY";

            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, month);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                AssetDto dto = new AssetDto();
                dto.setMonth(rs.getInt("month"));
                dto.setDate(rs.getDate("transaction_date").toLocalDate());
                dto.setAmount(rs.getInt("amount"));
                dto.setVendor(rs.getString("vendor"));
                dto.setCategory(rs.getString("category"));

                list.add(dto);
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if(rs != null)
                    rs.close();
                if(pstmt != null)
                    pstmt.close();
                if(conn != null)
                    conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return list;
    }


    // dashboard - 이번 달/저번 달 총 지출
    public int getMonthSpending(int month) {
        conn = connect();
        int total = -1;

        try {

            String sql = "SELECT SUM(amount) FROM asset_data " +
                    "WHERE month = ?";

            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, month);  // 사용자가 선택한 month 바인딩
            rs = pstmt.executeQuery();

            if (rs.next()) {
                total = rs.getInt(1);
            }

        } catch (Exception e) {
            e.printStackTrace() ;
        } finally {
            try {
                if(rs != null) rs.close();
                if(pstmt != null) pstmt.close();
                if(conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return total;
    }



    // analytics - 필수(Need) vs 비필수(Want) 금액 조회
    public Map<String, Integer> getNeedWantSpending(int month) {
        conn = connect();
        Map<String, Integer> result = new HashMap<>();

        // 식비, 의료, 교육, 교통, 생활은 필수(Need)로 분류하는 SQL - 그외는 비분류
        String sql = "SELECT SUM(CASE WHEN category LIKE '%식비%' OR category LIKE '%의료%' OR category LIKE '%교육%' OR category LIKE '%교통%' OR category LIKE '%생활%'  THEN amount ELSE 0 END) as need, " +
                "SUM(CASE WHEN NOT (category LIKE '%식비%' OR category like '%의료%' OR category like '%교육%' OR category LIKE '%교통%' OR category LIKE '%생활%' ) THEN amount ELSE 0 END) as want " +
                "FROM asset_data " +
                "WHERE month = ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, month);
            rs = pstmt.executeQuery();
            if (rs.next()) {
                result.put("need", rs.getInt("need"));
                result.put("want", rs.getInt("want"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if(rs != null) rs.close();
                if(pstmt != null) pstmt.close();
                if(conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    // analytics - 카테고리별 합계 조회 (도넛 차트용)
    public List<Map<String, Object>> getCategorySpending(int month) {
        List<Map<String, Object>> list = new ArrayList<>();
        conn = connect();

        String sql = "SELECT category, SUM(amount) as total " +
                "FROM asset_data " +
                "WHERE month = ? " +
                "GROUP BY category " +
                "ORDER BY total DESC";
        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, month);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                map.put("name", rs.getString("category"));
                map.put("value", rs.getInt("total"));
                // 색상은 자바스크립트나 컨트롤러에서 매칭
                list.add(map);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            try {
                if(rs != null) rs.close();
                if(pstmt != null) pstmt.close();
                if(conn != null) conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return list;
    }




    // analytics - 변동사항이 큰 3개 카테고리 가져와서 카드 출력
    public Map<String, Integer> getCategoryMapByMonth(int month) {
        Map<String, Integer> map = new HashMap<>();
        String sql = "SELECT category, SUM(amount) as total FROM asset_data WHERE month = ? GROUP BY category";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, month);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("category"), rs.getInt("total"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return map;
    }


    public int insertAnalysisHistory(AssetPlannerAnalysisDto dto) {
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
            pstmt.setDouble(4, dto.getMonthlySaving());
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

    public ArrayList<AssetPlannerAnalysisDto> getAnalysisHistory() {
        connect();
        ArrayList<AssetPlannerAnalysisDto> lists = new ArrayList<>();

        String sql = "select analysis_id, current_asset, monthly_income, monthly_expense, monthly_saving, "
                + "goal_amount, goal_months, expected_return, age, job_type, risk_preference, "
                + "prediction, prediction_label, tone_title, model_prediction, model_probability, "
                + "required_monthly_saving, estimated_final_asset, goal_gap, message, created_at "
                + "from asset_analysis_history "
                + "order by analysis_id desc "
                + "fetch first 10 rows only";

        try {
            pstmt = conn.prepareStatement(sql);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                AssetPlannerAnalysisDto dto = new AssetPlannerAnalysisDto();

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

//    public Map<String, Long> getAssetTrendData(int selectedMonth) {
//        Map<String, Long> trendData = new LinkedHashMap<>();
//        conn = connect();
//
//        // 선택한 월(selectedMonth)을 기준으로 그 포함 이전 3개월치 데이터를 가져오는 쿼리
//        // 예: 4월 선택 시 -> 2, 3, 4월의 마지막 데이터 추출
//        String sql = "SELECT TO_CHAR(created_at, 'MM') || '월' as month_label, current_asset " +
//                "FROM ( " +
//                "    SELECT created_at, current_asset, " +
//                "           ROW_NUMBER() OVER (PARTITION BY TO_CHAR(created_at, 'MM') ORDER BY created_at DESC) as rn " +
//                "    FROM asset_analysis_history " +
//                "    WHERE created_at <= LAST_DAY(TO_DATE('2026-' || ? || '-01', 'YYYY-MM-DD')) " + // 선택월의 말일보다 이전인 데이터
//                "      AND created_at >= ADD_MONTHS(TO_DATE('2026-' || ? || '-01', 'YYYY-MM-DD'), -2) " + // 2개월 전부터
//                ") " +
//                "WHERE rn = 1 " +
//                "ORDER BY created_at ASC";
//
//        try {
//            pstmt = conn.prepareStatement(sql);
//            pstmt.setInt(1, selectedMonth);
//            pstmt.setInt(2, selectedMonth);
//            rs = pstmt.executeQuery();
//            while (rs.next()) {
//                trendData.put(rs.getString("month_label"), rs.getLong("current_asset"));
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        } finally {
//            closeAll();
//        }
//        return trendData;
//    }

}