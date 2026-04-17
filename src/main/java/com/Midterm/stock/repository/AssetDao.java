package com.Midterm.stock.repository;

import com.Midterm.stock.dto.AssetDto;
import com.Midterm.stock.dto.AssetPlannerAnalysisDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class AssetDao {

    @Autowired
    private DataSource dataSource;

    public AssetDao() {
        System.out.println("AssetDao 생성자 호출 - 클라우드 설정 시작");
        System.setProperty("oracle.net.wallet_location",
                "(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=C:/oraclepw)))");
        System.out.println("클라우드 지갑 설정 성공");
    }

    // dashboard - 최근 소비 조회 메서드
    public List<AssetDto> getRecentTransactionsByMonth(int month, int year, int loginNum) {
        List<AssetDto> list = new ArrayList<>();

        String sql = "SELECT month, transaction_date, amount, vendor, category "
                + "FROM spending_data "
                + "WHERE month = ? AND user_id = ? AND year = ? "
                + "ORDER BY transaction_date DESC, spend_id DESC "
                + "FETCH FIRST 10 ROWS ONLY";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, month);
            pstmt.setInt(2, loginNum);
            pstmt.setInt(3, year);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    AssetDto dto = new AssetDto();
                    dto.setMonth(rs.getInt("month"));
                    dto.setDate(rs.getDate("transaction_date").toLocalDate());
                    dto.setAmount(rs.getInt("amount"));
                    dto.setVendor(rs.getString("vendor"));
                    dto.setCategory(rs.getString("category"));
                    list.add(dto);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }

    // dashboard - 이번 달/저번 달 총 지출
    public int getMonthSpending(int month, int year, int loginNum) {
        int total = 0;

        String sql = "SELECT NVL(SUM(amount), 0) "
                + "FROM spending_data "
                + "WHERE month = ? AND user_id = ? AND year = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, month);
            pstmt.setInt(2, loginNum);
            pstmt.setInt(3, year);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    total = rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return total;
    }

    // analytics - 필수(Need) vs 비필수(Want) 금액 조회
    public Map<String, Integer> getNeedWantSpending(int month, int year, int loginNum) {
        Map<String, Integer> result = new HashMap<>();

        String sql = "SELECT "
                + "SUM(CASE "
                + "        WHEN category LIKE '%식비%' "
                + "          OR category LIKE '%의료%' "
                + "          OR category LIKE '%교육%' "
                + "          OR category LIKE '%교통%' "
                + "          OR category LIKE '%생활%' "
                + "        THEN amount ELSE 0 END) AS need, "
                + "SUM(CASE "
                + "        WHEN NOT (category LIKE '%식비%' "
                + "               OR category LIKE '%의료%' "
                + "               OR category LIKE '%교육%' "
                + "               OR category LIKE '%교통%' "
                + "               OR category LIKE '%생활%') "
                + "        THEN amount ELSE 0 END) AS want "
                + "FROM spending_data "
                + "WHERE month = ? AND user_id = ? AND year = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, month);
            pstmt.setInt(2, loginNum);
            pstmt.setInt(3, year);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    result.put("need", rs.getInt("need"));
                    result.put("want", rs.getInt("want"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return result;
    }

    // analytics - 카테고리별 합계 조회 (도넛 차트용)
    public List<Map<String, Object>> getCategorySpending(int month, int year, int loginNum) {
        List<Map<String, Object>> list = new ArrayList<>();

        String sql = "SELECT category, SUM(amount) AS total "
                + "FROM spending_data "
                + "WHERE month = ? AND user_id = ? AND year = ? "
                + "GROUP BY category "
                + "ORDER BY total DESC";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, month);
            pstmt.setInt(2, loginNum);
            pstmt.setInt(3, year);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", rs.getString("category"));
                    map.put("value", rs.getInt("total"));
                    list.add(map);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return list;
    }

    // analytics - 월별 카테고리 맵
    public Map<String, Integer> getCategoryMapByMonth(int month, int year, int loginNum) {
        Map<String, Integer> map = new HashMap<>();

        String sql = "SELECT category, SUM(amount) AS total "
                + "FROM spending_data "
                + "WHERE month = ? AND user_id = ? AND year = ? "
                + "GROUP BY category";

        try (Connection conn = dataSource.getConnection();
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
        int count = -1;

        String sql = "INSERT INTO asset_analysis_history ("
                + "analysis_id, user_id, current_asset, monthly_income, monthly_expense, monthly_saving, "
                + "goal_amount, goal_months, expected_return, age, job_type, risk_preference, "
                + "prediction, prediction_label, tone_title, model_prediction, model_prediction_label, model_probability, "
                + "required_monthly_saving, estimated_final_asset, goal_gap, message, created_at"
                + ") VALUES ("
                + "asset_analysis_history_seq.nextval, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, sysdate"
                + ")";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

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
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return count;
    }

    public ArrayList<AssetPlannerAnalysisDto> getAnalysisHistory(int loginNum) {
        ArrayList<AssetPlannerAnalysisDto> lists = new ArrayList<>();

        String sql = "SELECT analysis_id, current_asset, monthly_income, monthly_expense, monthly_saving, "
                + "goal_amount, goal_months, expected_return, age, job_type, risk_preference, "
                + "prediction, prediction_label, tone_title, model_prediction, model_prediction_label, model_probability, "
                + "required_monthly_saving, estimated_final_asset, goal_gap, message, created_at "
                + "FROM asset_analysis_history "
                + "WHERE user_id = ? "
                + "ORDER BY analysis_id DESC "
                + "FETCH FIRST 10 ROWS ONLY";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, loginNum);

            try (ResultSet rs = pstmt.executeQuery()) {
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
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return lists;
    }

    // 최근 5개월 월별 지출 가져오기
    public List<Map<String, Object>> getLast5MonthsSpending(int year, int month, int loginNum) {
        List<Map<String, Object>> trend = new ArrayList<>();

        YearMonth endMonth = YearMonth.of(year, month);
        YearMonth startMonth = endMonth.minusMonths(4);

        String sql = "SELECT EXTRACT(YEAR FROM transaction_date) AS yr, "
                + "       EXTRACT(MONTH FROM transaction_date) AS mon, "
                + "       SUM(amount) AS total "
                + "FROM spending_data "
                + "WHERE user_id = ? "
                + "  AND transaction_date >= ? "
                + "  AND transaction_date < ? "
                + "GROUP BY EXTRACT(YEAR FROM transaction_date), EXTRACT(MONTH FROM transaction_date) "
                + "ORDER BY yr, mon";

        Map<String, Integer> grouped = new HashMap<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, loginNum);
            pstmt.setDate(2, Date.valueOf(startMonth.atDay(1)));
            pstmt.setDate(3, Date.valueOf(endMonth.plusMonths(1).atDay(1)));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int y = rs.getInt("yr");
                    int m = rs.getInt("mon");
                    int total = rs.getInt("total");
                    grouped.put(y + "-" + m, total);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        for (int i = 0; i < 5; i++) {
            YearMonth current = startMonth.plusMonths(i);
            Map<String, Object> map = new HashMap<>();
            map.put("year", current.getYear());
            map.put("month", current.getMonthValue());
            map.put("total", grouped.getOrDefault(current.getYear() + "-" + current.getMonthValue(), 0));
            trend.add(map);
        }

        return trend;
    }

    // 현재 선택된 날짜보다 이전 중 데이터가 있는 가장 최근 날짜 가져오기
    public Map<String, Integer> getNearestPrevDate(int month, int year, int loginNum) {
        String sql = "SELECT * FROM ("
                + "  SELECT TO_CHAR(transaction_date, 'YYYY') AS yr, TO_CHAR(transaction_date, 'MM') AS mon "
                + "  FROM spending_data "
                + "  WHERE user_id = ? AND transaction_date < TO_DATE(?, 'YY/MM/DD') "
                + "  ORDER BY transaction_date DESC"
                + ") WHERE ROWNUM = 1";

        String yearStr = String.valueOf(year).substring(2);
        String currentDate = yearStr + "/" + String.format("%02d", month) + "/01";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, loginNum);
            pstmt.setString(2, currentDate);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Map.of(
                            "year", rs.getInt("yr"),
                            "month", rs.getInt("mon")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    // 현재 선택된 날짜보다 이후 중 데이터가 있는 가장 가까운 날짜 가져오기
    public Map<String, Integer> getNearestNextDate(int month, int year, int loginNum) {
        String sql = "SELECT * FROM ("
                + "  SELECT TO_CHAR(transaction_date, 'YYYY') AS yr, TO_CHAR(transaction_date, 'MM') AS mon "
                + "  FROM spending_data "
                + "  WHERE user_id = ? AND transaction_date >= LAST_DAY(TO_DATE(?, 'YY/MM/DD')) + 1 "
                + "  ORDER BY transaction_date ASC"
                + ") WHERE ROWNUM = 1";

        String yearStr = String.valueOf(year).substring(2);
        String currentDate = yearStr + "/" + String.format("%02d", month) + "/01";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, loginNum);
            pstmt.setString(2, currentDate);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Map.of(
                            "year", rs.getInt("yr"),
                            "month", rs.getInt("mon")
                    );
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }
}
