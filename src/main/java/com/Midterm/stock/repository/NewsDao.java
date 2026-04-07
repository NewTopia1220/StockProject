package com.Midterm.stock.repository;

import com.Midterm.stock.dto.NewsDto;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

@Repository
public class NewsDao {

    private String driver = "oracle.jdbc.OracleDriver";
    private String url = "jdbc:oracle:thin:@stoxle_high?TNS_ADMIN=C:/oraclepw";
    private String id = "ADMIN";
    private String pw = "Heeyoun1220!";

    // pub_date가 VARCHAR2('YYYY-MM-DD HH:MM') 형태로 저장되어 있다고 가정
    // TRUNC(pub_date) 대신 SUBSTR(pub_date, 1, 10) 사용
    private static final String TODAY_FILTER =
        "SUBSTR(pub_date, 1, 10) = TO_CHAR(SYSDATE, 'YYYY-MM-DD')";

    public NewsDao() {
        try {
            Class.forName(driver);
            System.setProperty("oracle.net.wallet_location",
                "(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=C:/oraclepw)))");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private Connection connect() {
        try {
            return DriverManager.getConnection(url, id, pw);
        } catch (SQLException e) {
            System.err.println("NewsDao 접속 실패: " + e.getMessage());
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 당일 AI 종합 분석 (신뢰도, 노이즈, 감성)
    // ─────────────────────────────────────────────────────────────
    public Map<String, Object> getTodayAnalysis() {
        Map<String, Object> result = new HashMap<>();
        result.put("totalCount", 0);
        result.put("avgTypeProb", 0.0);
        result.put("avgClickbaitProb", 0.0);
        result.put("positiveCount", 0);
        result.put("negativeCount", 0);
        result.put("neutralCount", 0);

        String sql =
            "SELECT COUNT(*) as total_count, " +
            "       ROUND(AVG(type_prob), 1) as avg_type_prob, " +
            "       ROUND(AVG(clickbait_prob), 1) as avg_clickbait_prob, " +
            "       SUM(CASE WHEN sentiment = '호재' THEN 1 ELSE 0 END) as pos_cnt, " +
            "       SUM(CASE WHEN sentiment = '악재' THEN 1 ELSE 0 END) as neg_cnt, " +
            "       SUM(CASE WHEN sentiment = '중립' THEN 1 ELSE 0 END) as neu_cnt " +
            "FROM ( " +
            "    SELECT type_prob, clickbait_prob, sentiment " +
            "    FROM NEWS_DATA WHERE " + TODAY_FILTER +
            "    UNION ALL " +
            "    SELECT type_prob, clickbait_prob, sentiment " +
            "    FROM NEWS_DATA_SEC WHERE " + TODAY_FILTER +
            ")";

        Connection conn = connect();
        if (conn == null) return result;

        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            if (rs.next()) {
                result.put("totalCount",      rs.getInt("total_count"));
                result.put("avgTypeProb",     rs.getDouble("avg_type_prob"));
                result.put("avgClickbaitProb",rs.getDouble("avg_clickbait_prob"));
                result.put("positiveCount",   rs.getInt("pos_cnt"));
                result.put("negativeCount",   rs.getInt("neg_cnt"));
                result.put("neutralCount",    rs.getInt("neu_cnt"));
            }
        } catch (SQLException e) {
            System.err.println("getTodayAnalysis 오류: " + e.getMessage());
        } finally {
            try { conn.close(); } catch (Exception ignore) {}
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // 전광판용 당일 종목 목록
    // ─────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getTodayTickerCompanies() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql =
            "SELECT category, " +
            "       COUNT(*) as cnt, " +
            "       SUM(CASE WHEN sentiment='호재' THEN 1 ELSE 0 END) as pos, " +
            "       SUM(CASE WHEN sentiment='악재' THEN 1 ELSE 0 END) as neg " +
            "FROM ( " +
            "    SELECT category, sentiment FROM NEWS_DATA WHERE " + TODAY_FILTER +
            "    UNION ALL " +
            "    SELECT category, sentiment FROM NEWS_DATA_SEC WHERE " + TODAY_FILTER +
            ") " +
            "GROUP BY category ORDER BY cnt DESC";

        Connection conn = connect();
        if (conn == null) return list;

        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                String rawCategory = rs.getString("category");
                int pos = rs.getInt("pos");
                int neg = rs.getInt("neg");

                String companyName = rawCategory;
                int openIdx = rawCategory.lastIndexOf('(');
                int closeIdx = rawCategory.lastIndexOf(')');
                if (openIdx > 0 && closeIdx > openIdx) {
                    companyName = rawCategory.substring(0, openIdx).trim();
                }

                String sentiment = (pos >= neg) ? "호재" : "악재";
                int sentCount    = (pos >= neg) ? pos : neg;

                Map<String, Object> item = new LinkedHashMap<>();
                item.put("category",    rawCategory);
                item.put("companyName", companyName);
                item.put("cnt",         rs.getInt("cnt"));
                item.put("positive",    pos);
                item.put("negative",    neg);
                item.put("sentiment",   sentiment);
                item.put("sentCount",   sentCount);
                list.add(item);
            }
        } catch (SQLException e) {
            System.err.println("getTodayTickerCompanies 오류: " + e.getMessage());
        } finally {
            try { conn.close(); } catch (Exception ignore) {}
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────────
    // 섹터→종목 맵 (stock 페이지 하단 카드용, 당일 기준)
    // ─────────────────────────────────────────────────────────────
    public LinkedHashMap<String, List<Map<String, Object>>> getSectorCompanyMap() {
        LinkedHashMap<String, List<Map<String, Object>>> sectorMap = new LinkedHashMap<>();

        String sql =
            "SELECT category, " +
            "       COUNT(*) as article_count, " +
            "       SUM(CASE WHEN sentiment='호재' THEN 1 ELSE 0 END) as pos, " +
            "       SUM(CASE WHEN sentiment='악재' THEN 1 ELSE 0 END) as neg, " +
            "       SUM(CASE WHEN sentiment='중립' THEN 1 ELSE 0 END) as neu " +
            "FROM ( " +
            "    SELECT category, sentiment FROM NEWS_DATA WHERE " + TODAY_FILTER +
            "    UNION ALL " +
            "    SELECT category, sentiment FROM NEWS_DATA_SEC WHERE " + TODAY_FILTER +
            ") " +
            "GROUP BY category ORDER BY category";

        Connection conn = connect();
        if (conn == null) return sectorMap;

        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                String rawCategory = rs.getString("category");
                int pos = rs.getInt("pos"), neg = rs.getInt("neg"), neu = rs.getInt("neu");

                String companyName = rawCategory, sectorName = "기타";
                int openIdx = rawCategory.lastIndexOf('(');
                int closeIdx = rawCategory.lastIndexOf(')');
                if (openIdx > 0 && closeIdx > openIdx) {
                    companyName = rawCategory.substring(0, openIdx).trim();
                    sectorName  = rawCategory.substring(openIdx + 1, closeIdx).trim();
                }

                String dom;
                if (pos >= neg && pos >= neu)      dom = "호재";
                else if (neg >= pos && neg >= neu)  dom = "악재";
                else                                dom = "중립";

                Map<String, Object> info = new LinkedHashMap<>();
                info.put("companyName",       companyName);
                info.put("sectorName",        sectorName);
                info.put("rawCategory",       rawCategory);
                info.put("articleCount",      rs.getInt("article_count"));
                info.put("positiveCount",     pos);
                info.put("negativeCount",     neg);
                info.put("neutralCount",      neu);
                info.put("dominantSentiment", dom);

                sectorMap.computeIfAbsent(sectorName, k -> new ArrayList<>()).add(info);
            }
        } catch (SQLException e) {
            System.err.println("getSectorCompanyMap 오류: " + e.getMessage());
        } finally {
            try { conn.close(); } catch (Exception ignore) {}
        }
        return sectorMap;
    }

    // ─────────────────────────────────────────────────────────────
    // 뉴스 목록 (페이징, sector/company 필터) — pub_date VARCHAR2 대응
    // ─────────────────────────────────────────────────────────────
    public List<NewsDto> getNewsList(String sector, String company, int start, int end) {
        List<NewsDto> list = new ArrayList<>();
        String filterCondition = buildFilterCondition(sector, company);

        // pub_date를 그대로 VARCHAR2 문자열로 읽고, Java에서 포맷
        String sql =
            "SELECT * FROM ( " +
            "    SELECT ROWNUM as rnum, t.* FROM ( " +
            "        SELECT link, category, title, summary, sentiment, pub_date, " +
            "               clickbait_prob, article_type, type_prob " +
            "        FROM ( " +
            "            SELECT link, category, title, summary, sentiment, pub_date, " +
            "                   clickbait_prob, article_type, type_prob " +
            "            FROM NEWS_DATA " + filterCondition +
            "            UNION ALL " +
            "            SELECT link, category, title, summary, sentiment, pub_date, " +
            "                   clickbait_prob, article_type, type_prob " +
            "            FROM NEWS_DATA_SEC " + filterCondition +
            "        ) " +
            "        ORDER BY pub_date DESC " +
            "    ) t WHERE ROWNUM <= ? " +
            ") WHERE rnum >= ?";

        Connection conn = connect();
        if (conn == null) return list;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int paramIdx = 1;
            for (int i = 0; i < 2; i++) {
                if (company != null && !company.isEmpty()) {
                    pstmt.setString(paramIdx++, company + " (%");
                } else if (sector != null && !sector.isEmpty()) {
                    pstmt.setString(paramIdx++, "%(" + sector + ")%");
                }
            }
            pstmt.setInt(paramIdx++, end);
            pstmt.setInt(paramIdx,   start);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    NewsDto dto = new NewsDto();
                    dto.setLink(rs.getString("link"));
                    dto.setCategory(rs.getString("category"));
                    dto.setTitle(rs.getString("title"));
                    dto.setSummary(rs.getString("summary"));
                    dto.setSentiment(rs.getString("sentiment"));
                    // pub_date: VARCHAR2 또는 DATE 모두 처리
                    dto.setPubDate(formatPubDate(rs, "pub_date"));
                    dto.setClickbaitProb(rs.getDouble("clickbait_prob"));
                    dto.setArticleType(rs.getString("article_type"));
                    dto.setTypeProb(rs.getDouble("type_prob"));
                    list.add(dto);
                }
            }
        } catch (SQLException e) {
            System.err.println("getNewsList 오류: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try { conn.close(); } catch (Exception ignore) {}
        }
        return list;
    }

    // ─────────────────────────────────────────────────────────────
    // 뉴스 총 건수 (페이징용)
    // ─────────────────────────────────────────────────────────────
    public int getNewsCount(String sector, String company) {
        String filterCondition = buildFilterCondition(sector, company);
        String sql =
            "SELECT COUNT(*) FROM ( " +
            "    SELECT 1 FROM NEWS_DATA " + filterCondition +
            "    UNION ALL " +
            "    SELECT 1 FROM NEWS_DATA_SEC " + filterCondition +
            ")";

        Connection conn = connect();
        if (conn == null) return 0;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            int paramIdx = 1;
            for (int i = 0; i < 2; i++) {
                if (company != null && !company.isEmpty()) {
                    pstmt.setString(paramIdx++, company + " (%");
                } else if (sector != null && !sector.isEmpty()) {
                    pstmt.setString(paramIdx++, "%(" + sector + ")%");
                }
            }
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("getNewsCount 오류: " + e.getMessage());
        } finally {
            try { conn.close(); } catch (Exception ignore) {}
        }
        return 0;
    }

    // ─────────────────────────────────────────────────────────────
    // 뉴스 사이드바용 섹터→종목 맵 (전체 기간)
    // ─────────────────────────────────────────────────────────────
    public LinkedHashMap<String, List<String>> getSidebarSectorMap() {
        LinkedHashMap<String, List<String>> sectorMap = new LinkedHashMap<>();
        String sql =
            "SELECT DISTINCT category FROM ( " +
            "    SELECT category FROM NEWS_DATA " +
            "    UNION ALL " +
            "    SELECT category FROM NEWS_DATA_SEC " +
            ") ORDER BY category";

        Connection conn = connect();
        if (conn == null) return sectorMap;

        try (PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                String raw = rs.getString("category");
                String company = raw, sector = "기타";
                int openIdx = raw.lastIndexOf('(');
                int closeIdx = raw.lastIndexOf(')');
                if (openIdx > 0 && closeIdx > openIdx) {
                    company = raw.substring(0, openIdx).trim();
                    sector  = raw.substring(openIdx + 1, closeIdx).trim();
                }
                sectorMap.computeIfAbsent(sector, k -> new ArrayList<>()).add(company);
            }
        } catch (SQLException e) {
            System.err.println("getSidebarSectorMap 오류: " + e.getMessage());
        } finally {
            try { conn.close(); } catch (Exception ignore) {}
        }
        return sectorMap;
    }

    // ─────────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────────────────────────────
    private String buildFilterCondition(String sector, String company) {
        if ((company != null && !company.isEmpty()) || (sector != null && !sector.isEmpty())) {
            return "WHERE category LIKE ?";
        }
        return "";
    }

    /** pub_date가 VARCHAR2이면 getString, DATE/TIMESTAMP이면 Timestamp 변환 */
    private String formatPubDate(ResultSet rs, String columnName) {
        try {
            // VARCHAR2로 저장된 경우 그대로 반환 (앞 16자리: YYYY-MM-DD HH:MM)
            String raw = rs.getString(columnName);
            if (raw != null && raw.length() >= 16) {
                return raw.substring(0, 16);
            }
            return raw != null ? raw : "";
        } catch (SQLException e) {
            try {
                Timestamp ts = rs.getTimestamp(columnName);
                if (ts != null) {
                    return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(ts);
                }
            } catch (SQLException ignore) {}
            return "";
        }
    }
}
