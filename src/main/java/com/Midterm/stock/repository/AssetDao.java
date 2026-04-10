package com.Midterm.stock.repository;

import com.Midterm.stock.dto.AssetDto;
import com.Midterm.stock.dto.AssetPlannerAnalysisDto;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.time.YearMonth;
import java.util.*;

@Repository
public class AssetDao {


    // 1. 오라클 클라우드 접속 정보 (경로는 반드시 슬래시 / 사용)
    private String driver = "oracle.jdbc.OracleDriver";
    private String url = "jdbc:oracle:thin:@stoxle_high?TNS_ADMIN=C:/oraclepw";
    private String id = "ADMIN";
    private String pw = "Heeyoun1220!";

    private Connection conn = null;
    private PreparedStatement pstmt = null;
    private ResultSet rs = null;

    // 생성자: 드라이버 로딩 및 지갑 경로 설정
    public AssetDao() {
        System.out.println("AssetDao 생성자 호출 - 클라우드 설정 시작");
        try {
            Class.forName(driver);
            // 자바 시스템에 지갑(Wallet) 위치를 입력
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
    public List<AssetDto> getRecentTransactionsByMonth(int month, int year, int loginNum) {
        List<AssetDto> list = new ArrayList<>();
        conn = connect();

        try {

            String sql = "SELECT month, transaction_date, amount, vendor, category " +
                    "FROM spending_data " +
                    "where month = ? and user_id = ? and year = ? " +
                    "ORDER BY transaction_date DESC, spend_id DESC " +
                    "FETCH FIRST 10 ROWS ONLY";

            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, month);
            pstmt.setInt(2, loginNum);
            pstmt.setInt(3, year);
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
    public int getMonthSpending(int month, int year, int loginNum) {
        conn = connect();
        int total = -1;

        try {

            String sql = "SELECT SUM(amount) FROM spending_data " +
                    "WHERE month = ? and user_id = ? and year = ? ";

            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, month);  // 사용자가 선택한 month 바인딩
            pstmt.setInt(2, loginNum);  // 사용자
            pstmt.setInt(3, year);
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
    public Map<String, Integer> getNeedWantSpending(int month, int year, int loginNum) {
        conn = connect();
        Map<String, Integer> result = new HashMap<>();

        // 식비, 의료, 교육, 교통, 생활은 필수(Need)로 분류하는 SQL - 그외는 비분류
        String sql = "SELECT SUM(CASE WHEN category LIKE '%식비%' OR category LIKE '%의료%' OR category LIKE '%교육%' OR category LIKE '%교통%' OR category LIKE '%생활%'  THEN amount ELSE 0 END) as need, " +
                "SUM(CASE WHEN NOT (category LIKE '%식비%' OR category like '%의료%' OR category like '%교육%' OR category LIKE '%교통%' OR category LIKE '%생활%' ) THEN amount ELSE 0 END) as want " +
                "FROM spending_data " +
                "WHERE month = ? and user_id = ? and year = ? ";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, month);
            pstmt.setInt(2, loginNum);
            pstmt.setInt(3, year);
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
    public List<Map<String, Object>> getCategorySpending(int month, int year, int loginNum) {
        List<Map<String, Object>> list = new ArrayList<>();
        conn = connect();

        String sql = "SELECT category, SUM(amount) as total " +
                "FROM spending_data " +
                "WHERE month = ? and user_id = ? and year = ? " +
                "GROUP BY category " +
                "ORDER BY total DESC";
        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, month);
            pstmt.setInt(2, loginNum);
            pstmt.setInt(3, year);
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
    public Map<String, Integer> getCategoryMapByMonth(int month, int year, int loginNum) {
        Map<String, Integer> map = new HashMap<>();
        String sql = "SELECT category, SUM(amount) as total FROM spending_data WHERE month = ? and user_id = ? and year = ? GROUP BY category";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, month);
            pstmt.setInt(2, loginNum);
            pstmt.setInt(3, year);
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


    public int insertAnalysisHistory(AssetPlannerAnalysisDto dto, int loginNum) {
        connect();
        int count = -1;

        String sql = "insert into asset_analysis_history ("
                + "analysis_id, user_id, current_asset, monthly_income, monthly_expense, monthly_saving, "
                + "goal_amount, goal_months, expected_return, age, job_type, risk_preference, "
                + "prediction, prediction_label, tone_title, model_prediction, model_prediction_label, model_probability, "
                + "required_monthly_saving, estimated_final_asset, goal_gap, message, created_at"
                + ") values ("
                + "asset_analysis_history_seq.nextval, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, sysdate"
                + ")";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, loginNum);
            pstmt.setLong(2, dto.getCurrentAsset());
            pstmt.setLong(3, dto.getMonthlyIncome());
            pstmt.setLong(4, dto.getMonthlyExpense());
            pstmt.setDouble(5, dto.getMonthlySaving());
            pstmt.setLong(6, dto.getGoalAmount());
            pstmt.setInt(7, dto.getGoalMonths());
            pstmt.setDouble(8, dto.getExpectedReturn());
            pstmt.setInt(9, dto.getAge());
            pstmt.setString(10, dto.getJobType());
            pstmt.setString(11, dto.getRiskPreference());

            pstmt.setInt(12, dto.getPrediction());
            pstmt.setString(13, dto.getPredictionLabel());
            pstmt.setString(14, dto.getToneTitle());

            pstmt.setInt(15, dto.getModelPrediction());
            pstmt.setString(16, dto.getModelPredictionLabel());
            pstmt.setDouble(17, dto.getModelProbability());

            pstmt.setLong(18, dto.getRequiredMonthlySaving());
            pstmt.setLong(19, dto.getEstimatedFinalAsset());
            pstmt.setLong(20, dto.getGoalGap());
            pstmt.setString(21, dto.getMessage());

            count = pstmt.executeUpdate();
            System.out.println("insert count = " + count);
//            conn.commit(); // executeUpdate 후 추가

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return count;
    }


    public ArrayList<AssetPlannerAnalysisDto> getAnalysisHistory(int loginNum) {
        connect();
        ArrayList<AssetPlannerAnalysisDto> lists = new ArrayList<>();

        String sql = "select analysis_id, current_asset, monthly_income, monthly_expense, monthly_saving, "
                + "goal_amount, goal_months, expected_return, age, job_type, risk_preference, "
                + "prediction, prediction_label, tone_title, model_prediction, model_prediction_label, model_probability, "
                + "required_monthly_saving, estimated_final_asset, goal_gap, message, created_at "
                + "from asset_analysis_history "
                + "where user_id = ? "
                + "order by analysis_id desc "
                + "fetch first 10 rows only";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, loginNum);
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
                dto.setModelPredictionLabel(rs.getString("model_prediction_label"));
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



    // 최근 5개월 월별 지출 가져오기
    public List<Map<String, Object>> getLast5MonthsSpending(int year, int month, int loginNum)  {
        List<Map<String, Object>> trend = new ArrayList<>();

        // 파라미터로 받은 날짜를 기준으로 설정
        YearMonth targetDate = YearMonth.of(year, month);

        for (int i = 4; i >= 0; i--) { // 선택한 달 포함 과거 5개월
            YearMonth target = targetDate.minusMonths(i);
            int m = target.getMonthValue();
            int y = target.getYear();

            int total = getMonthSpending(m, y, loginNum);
            Map<String, Object> map = new HashMap<>();
            map.put("year", y);
            map.put("month", m);
            map.put("total", Math.max(total, 0));
            trend.add(map);
        }

        return trend;
    }

// --------------- 추가 -----------------------------------------------------------------
    //현재 선택된 날짜보다 이전 중 데이터가 있는 가장 최근 날짜 가져오기
    public Map<String, Integer> getNearestPrevDate(int month, int year, int loginNum) {
        // transaction_date < 기준일 (과거 데이터 찾기) -> 가장 최근 순(DESC)으로 1개
        String sql = "SELECT * FROM (" +
                "  SELECT TO_CHAR(transaction_date, 'YYYY') as yr, TO_CHAR(transaction_date, 'MM') as mon " +
                "  FROM SPENDING_DATA " +
                "  WHERE user_id = ? AND transaction_date < TO_DATE(?, 'YY/MM/DD') " +
                "  ORDER BY transaction_date DESC" +
                ") WHERE ROWNUM = 1";

        // 2026을 26으로 자르고 / 기호를 넣음
        String yearStr = String.valueOf(year).substring(2); // "2026" -> "26"
        String currentDate = yearStr + "/" + String.format("%02d", month) + "/01"; // "26/04/01"

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, loginNum);
            pstmt.setString(2, currentDate); // 이제 "26/04/01"이 전달됨
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Map.of("year", rs.getInt("yr"), "month", rs.getInt("mon"));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    // 현재 선택된 날짜보다 이후 중 데이터가 있는 가장 가까운 날짜 가져오기
    public Map<String, Integer> getNearestNextDate(int month, int year, int loginNum) {
        // transaction_date >= 기준월 마지막날+1 (미래 데이터 찾기) -> 가장 가까운 순(ASC)으로 1개
        String sql = "SELECT * FROM (" +
                "  SELECT TO_CHAR(transaction_date, 'YYYY') as yr, TO_CHAR(transaction_date, 'MM') as mon " +
                "  FROM SPENDING_DATA " +
                "  WHERE user_id = ? AND transaction_date >= LAST_DAY(TO_DATE(?, 'YY/MM/DD')) + 1 " +
                "  ORDER BY transaction_date ASC" +
                ") WHERE ROWNUM = 1";

        // 위와 동일하게 포맷 맞추기
        String yearStr = String.valueOf(year).substring(2);
        String currentDate = yearStr + "/" + String.format("%02d", month) + "/01";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, loginNum);
            pstmt.setString(2, currentDate);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Map.of("year", rs.getInt("yr"), "month", rs.getInt("mon"));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }



}