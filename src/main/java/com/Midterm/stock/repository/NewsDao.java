package com.Midterm.stock.repository;

import com.Midterm.stock.dto.NewsDto;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.*;

@Repository
public class NewsDao {

    private String driver = "oracle.jdbc.OracleDriver";
    private String url    = "jdbc:oracle:thin:@stoxle_high?TNS_ADMIN=C:/oraclepw";
    private String id     = "ADMIN";
    private String pw     = "Heeyoun1220!";

    private static final String TODAY_FILTER =
        "SUBSTR(pub_date, 1, 10) = TO_CHAR(SYSDATE, 'YYYY-MM-DD')";

    // Oracle 드라이버와 지갑 경로를 초기화한다.
    public NewsDao() {
        try {
            Class.forName(driver);
            System.setProperty("oracle.net.wallet_location",
                "(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=C:/oraclepw)))");
        } catch (ClassNotFoundException e) { e.printStackTrace(); }
    }

    // 뉴스 전용 Oracle DB 연결을 생성
    private Connection connect() {
        try { return DriverManager.getConnection(url, id, pw); }
        catch (SQLException e) { System.err.println("NewsDao 접속 실패: " + e.getMessage()); return null; }
    }

    // ─────────────────────────────────────────────────────────────
    // 당일 AI 종합 분석
    // ─────────────────────────────────────────────────────────────
    // 오늘 수집된 뉴스들의 감성/신뢰도 집계 결과를 조회한다.
    public Map<String, Object> getTodayAnalysis() {
        Map<String, Object> r = new HashMap<>();
        r.put("totalCount", 0); r.put("avgTypeProb", 0.0);
        r.put("avgClickbaitProb", 0.0); r.put("positiveCount", 0);
        r.put("negativeCount", 0); r.put("neutralCount", 0);

        String sql =
            "SELECT COUNT(*) as tc, ROUND(AVG(type_prob),1) as atp, " +
            "       ROUND(AVG(clickbait_prob),1) as acp, " +
            "       SUM(CASE WHEN sentiment='호재' THEN 1 ELSE 0 END) as pos, " +
            "       SUM(CASE WHEN sentiment='악재' THEN 1 ELSE 0 END) as neg, " +
            "       SUM(CASE WHEN sentiment='중립' THEN 1 ELSE 0 END) as neu " +
            "FROM (SELECT type_prob,clickbait_prob,sentiment FROM NEWS_DATA WHERE " + TODAY_FILTER +
            "      UNION ALL " +
            "      SELECT type_prob,clickbait_prob,sentiment FROM NEWS_DATA_SEC WHERE " + TODAY_FILTER + ")";

        Connection conn = connect(); if (conn == null) return r;
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                r.put("totalCount",      rs.getInt("tc"));
                r.put("avgTypeProb",     rs.getDouble("atp"));
                r.put("avgClickbaitProb",rs.getDouble("acp"));
                r.put("positiveCount",   rs.getInt("pos"));
                r.put("negativeCount",   rs.getInt("neg"));
                r.put("neutralCount",    rs.getInt("neu"));
            }
        } catch (SQLException e) { System.err.println("getTodayAnalysis: " + e.getMessage()); }
        finally { try { conn.close(); } catch (Exception ignore) {} }
        return r;
    }

    // 오늘 뉴스에 많이 등장한 종목과 감성 요약을 전광판용으로 조회
    public List<Map<String, Object>> getTodayTickerCompanies() {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql =
            "SELECT category, COUNT(*) as cnt, " +
            "       SUM(CASE WHEN sentiment='호재' THEN 1 ELSE 0 END) as pos, " +
            "       SUM(CASE WHEN sentiment='악재' THEN 1 ELSE 0 END) as neg " +
            "FROM (SELECT category,sentiment FROM NEWS_DATA WHERE " + TODAY_FILTER +
            "      UNION ALL SELECT category,sentiment FROM NEWS_DATA_SEC WHERE " + TODAY_FILTER + ") " +
            "GROUP BY category ORDER BY cnt DESC";

        Connection conn = connect(); if (conn == null) return list;
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String raw = rs.getString("category");
                int pos = rs.getInt("pos"), neg = rs.getInt("neg");
                String cname = raw;
                int oi = raw.lastIndexOf('('), ci = raw.lastIndexOf(')');
                if (oi > 0 && ci > oi) cname = raw.substring(0, oi).trim();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("category",    raw);
                item.put("companyName", cname);
                item.put("cnt",         rs.getInt("cnt"));
                item.put("positive",    pos);
                item.put("negative",    neg);
                item.put("sentiment",   pos >= neg ? "호재" : "악재");
                item.put("sentCount",   pos >= neg ? pos : neg);
                list.add(item);
            }
        } catch (SQLException e) { System.err.println("getTodayTickerCompanies: " + e.getMessage()); }
        finally { try { conn.close(); } catch (Exception ignore) {} }
        return list;
    }

    // ─────────────────────────────────────────────────────────────
    // 섹터레벨 집계 (stock 페이지 카드용) — 기업명 제거, 섹터만
    // ─────────────────────────────────────────────────────────────
    public LinkedHashMap<String, Map<String, Object>> getSectorLevelMap() {
        LinkedHashMap<String, Map<String, Object>> result = new LinkedHashMap<>();
        String sql =
            "SELECT sector_name, COUNT(*) as article_count, " +
            "       ROUND(AVG(type_prob),1) as avg_type_prob, " +
            "       ROUND(AVG(clickbait_prob),1) as avg_clickbait_prob, " +
            "       SUM(CASE WHEN sentiment='호재' THEN 1 ELSE 0 END) as pos, " +
            "       SUM(CASE WHEN sentiment='악재' THEN 1 ELSE 0 END) as neg, " +
            "       SUM(CASE WHEN sentiment='중립' THEN 1 ELSE 0 END) as neu " +
            "FROM ( " +
            "    SELECT CASE WHEN INSTR(category,'(')>0 " +
            "                THEN TRIM(SUBSTR(category, INSTR(category,'(')+1, " +
            "                         INSTR(category,')')-INSTR(category,'(')-1)) " +
            "                ELSE TRIM(category) END as sector_name, " +
            "           sentiment, type_prob, clickbait_prob " +
            "    FROM NEWS_DATA WHERE " + TODAY_FILTER +
            "    UNION ALL " +
            "    SELECT CASE WHEN INSTR(category,'(')>0 " +
            "                THEN TRIM(SUBSTR(category, INSTR(category,'(')+1, " +
            "                         INSTR(category,')')-INSTR(category,'(')-1)) " +
            "                ELSE TRIM(category) END as sector_name, " +
            "           sentiment, type_prob, clickbait_prob " +
            "    FROM NEWS_DATA_SEC WHERE " + TODAY_FILTER +
            ") GROUP BY sector_name ORDER BY sector_name";
        Connection conn = connect(); if (conn == null) return result;
        try (PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String sector = rs.getString("sector_name");
                int pos = rs.getInt("pos"), neg = rs.getInt("neg"), neu = rs.getInt("neu");
                int total = rs.getInt("article_count");
                String dom = (pos>=neg && pos>=neu) ? "호재" : (neg>=pos && neg>=neu) ? "악재" : "중립";
                int posRatio = total > 0 ? (int)Math.round((double)pos/total*100) : 0;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("articleCount",    total);
                m.put("avgTypeProb",     rs.getDouble("avg_type_prob"));
                m.put("avgClickbaitProb",rs.getDouble("avg_clickbait_prob"));
                m.put("positiveCount",   pos);
                m.put("negativeCount",   neg);
                m.put("neutralCount",    neu);
                m.put("dominant",        dom);
                m.put("posRatio",        posRatio);
                result.put(sector, m);
            }
        } catch (SQLException e) { System.err.println("getSectorLevelMap: " + e.getMessage()); }
        finally { try { conn.close(); } catch (Exception ignore) {} }
        return result;
    }

    // ─────────────────────────────────────────────────────────────
    // 뉴스 목록 (sector/keyword 필터 + 좋아요/댓글 초기값)
    // ─────────────────────────────────────────────────────────────
    public List<NewsDto> getNewsList(String sector, String keyword, int start, int end, int userNum) {
        List<NewsDto> list = new ArrayList<>();
        String where1 = buildWhere(sector, keyword, "1");
        String where2 = buildWhere(sector, keyword, "2");

        String sql =
            "SELECT * FROM ( " +
            "    SELECT ROWNUM as rnum, t.* FROM ( " +
            "        SELECT link, category, title, summary, sentiment, pub_date, " +
            "               clickbait_prob, article_type, type_prob " +
            "        FROM ( " +
            "            SELECT link,category,title,summary,sentiment,pub_date,clickbait_prob,article_type,type_prob " +
            "            FROM NEWS_DATA " + where1 +
            "            UNION ALL " +
            "            SELECT link,category,title,summary,sentiment,pub_date,clickbait_prob,article_type,type_prob " +
            "            FROM NEWS_DATA_SEC " + where2 +
            "        ) ORDER BY pub_date DESC " +
            "    ) t WHERE ROWNUM <= ? " +
            ") WHERE rnum >= ?";

        Connection conn = connect(); if (conn == null) return list;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            idx = setWhereParams(ps, idx, sector, keyword);
            idx = setWhereParams(ps, idx, sector, keyword);
            ps.setInt(idx++, end);
            ps.setInt(idx,   start);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    NewsDto dto = new NewsDto();
                    dto.setLink(rs.getString("link"));
                    dto.setCategory(rs.getString("category"));
                    dto.setTitle(rs.getString("title"));
                    dto.setSummary(rs.getString("summary"));
                    dto.setSentiment(rs.getString("sentiment"));
                    dto.setPubDate(formatPubDate(rs, "pub_date"));
                    dto.setClickbaitProb(rs.getDouble("clickbait_prob"));
                    dto.setArticleType(rs.getString("article_type"));
                    dto.setTypeProb(rs.getDouble("type_prob"));
                    list.add(dto);
                }
            }
        } catch (SQLException e) { System.err.println("getNewsList: " + e.getMessage()); e.printStackTrace(); }
        finally { try { conn.close(); } catch (Exception ignore) {} }

        // 좋아요/댓글 수 배치 로드
        if (!list.isEmpty()) loadLikeCommentCounts(list, userNum);
        return list;
    }

    // 뉴스 링크 목록에 대응하는 종목 매핑과 저장된 영향도를 한 번에 조회
    // 뉴스 링크 목록에 대응하는 종목 매핑과 저장된 영향도를 한 번에 조회
    public Map<String, Map<String, Object>> getNewsSignalMap(List<String> links) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        if (links == null || links.isEmpty()) {
            return result;
        }

        List<String> uniqueLinks = new ArrayList<>(new LinkedHashSet<>(links));
        String placeholders = String.join(",", Collections.nCopies(uniqueLinks.size(), "?"));

        Connection conn = connect();
        if (conn == null) {
            return result;
        }

        String sql =
                "SELECT link, stock_code, impact_30m FROM ( " +
                        "    SELECT link, stock_code, impact_30m, " +
                        "           ROW_NUMBER() OVER (PARTITION BY link ORDER BY created_at DESC NULLS LAST, id DESC) AS rn " +
                        "    FROM NEWS_IMPACT " +
                        "    WHERE link IN (" + placeholders + ") " +
                        ") WHERE rn = 1";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindLinkParams(ps, uniqueLinks);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String link = rs.getString("link");
                    Map<String, Object> row = result.computeIfAbsent(link, key -> new HashMap<>());

                    String stockCode = rs.getString("stock_code");
                    if (stockCode != null && !stockCode.isBlank()) {
                        row.put("stockCode", stockCode);
                    }

                    double impact = rs.getDouble("impact_30m");
                    if (!rs.wasNull()) {
                        row.put("impact30m", impact);
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("getNewsSignalMap: " + e.getMessage());
        } finally {
            try {
                conn.close();
            } catch (Exception ignore) {
            }
        }

        return result;
    }

    // 기사 영향도 예측 결과를 NEWS_IMPACT 테이블에 업데이트하거나 신규 저장
    public void saveNewsImpact(String link, String stockCode, Double impact30m) {
        if (isBlank(link) || isBlank(stockCode) || impact30m == null) {
            return;
        }

        Connection conn = connect();
        if (conn == null) {
            return;
        }
        try {
            String updateSql =
                "UPDATE NEWS_IMPACT " +
                "SET stock_code=?, impact_30m=?, created_at=SYSTIMESTAMP " +
                "WHERE link=?";

            try (PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                updatePs.setString(1, stockCode);
                updatePs.setDouble(2, impact30m);
                updatePs.setString(3, link);
                int updated = updatePs.executeUpdate();
                if (updated > 0) {
                    return;
                }
            } catch (SQLException e) {
                System.err.println("saveNewsImpact(update): " + e.getMessage());
            }

            String insertNoIdSql =
                "INSERT INTO NEWS_IMPACT (link, stock_code, impact_30m, created_at) " +
                "VALUES (?, ?, ?, SYSTIMESTAMP)";

            try (PreparedStatement insertPs = conn.prepareStatement(insertNoIdSql)) {
                insertPs.setString(1, link);
                insertPs.setString(2, stockCode);
                insertPs.setDouble(3, impact30m);
                insertPs.executeUpdate();
                return;
            } catch (SQLException ignored) {
                // fall back to sequence insert for schemas without identity id
            }

            String insertWithSeqSql =
                "INSERT INTO NEWS_IMPACT (id, link, stock_code, impact_30m, created_at) " +
                "VALUES (NEWS_IMPACT_SEQ.NEXTVAL, ?, ?, ?, SYSTIMESTAMP)";

            try (PreparedStatement insertPs = conn.prepareStatement(insertWithSeqSql)) {
                insertPs.setString(1, link);
                insertPs.setString(2, stockCode);
                insertPs.setDouble(3, impact30m);
                insertPs.executeUpdate();
            } catch (SQLException e) {
                System.err.println("saveNewsImpact(insert): " + e.getMessage());
            }
        } finally {
            try { conn.close(); } catch (Exception ignore) {}
        }
    }

    /** 좋아요·댓글 수를 한 번에 로드해서 dto에 세팅 */
    // 좋아요·댓글 수를 현재 페이지 뉴스 링크 기준으로 한 번에 로드해서 dto에 세팅
    // 좋아요·댓글 수를 현재 페이지 뉴스 링크 기준으로 한 번에 로드해서 dto에 세팅
    private void loadLikeCommentCounts(List<NewsDto> list, int userNum) {
        if (list == null || list.isEmpty()) {
            return;
        }

        List<String> links = new ArrayList<>();
        for (NewsDto dto : list) {
            if (dto.getLink() != null && !dto.getLink().isBlank()) {
                links.add(dto.getLink());
            }
        }

        if (links.isEmpty()) {
            return;
        }

        String placeholders = String.join(",", Collections.nCopies(links.size(), "?"));
        Map<String, Integer> likeCountMap = new HashMap<>();
        Map<String, Boolean> likedMap = new HashMap<>();
        Map<String, Integer> commentCountMap = new HashMap<>();

        Connection conn = connect();
        if (conn == null) {
            return;
        }

        try {
            // 수정 이유:
            // 기사마다 좋아요 쿼리를 따로 날리면 뉴스 개수만큼 반복되므로
            // 현재 페이지 링크 전체를 한 번에 집계해서 가져온다.
            String likeSql =
                    "SELECT news_link, COUNT(*) AS total, " +
                            "       SUM(CASE WHEN user_num = ? THEN 1 ELSE 0 END) AS mine " +
                            "FROM NEWS_LIKES " +
                            "WHERE news_link IN (" + placeholders + ") " +
                            "GROUP BY news_link";

            try (PreparedStatement ps = conn.prepareStatement(likeSql)) {
                int idx = 1;
                ps.setInt(idx++, userNum);
                for (String link : links) {
                    ps.setString(idx++, link);
                }

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String link = rs.getString("news_link");
                        likeCountMap.put(link, rs.getInt("total"));
                        likedMap.put(link, rs.getInt("mine") > 0);
                    }
                }
            }

            // 수정 이유:
            // 댓글 수도 기사별로 개별 조회하지 않고
            // 현재 페이지 링크 전체를 한 번에 집계해서 가져온다.
            String commentSql =
                    "SELECT news_link, COUNT(*) AS total " +
                            "FROM NEWS_COMMENTS " +
                            "WHERE news_link IN (" + placeholders + ") " +
                            "GROUP BY news_link";

            try (PreparedStatement ps = conn.prepareStatement(commentSql)) {
                int idx = 1;
                for (String link : links) {
                    ps.setString(idx++, link);
                }

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        commentCountMap.put(rs.getString("news_link"), rs.getInt("total"));
                    }
                }
            }

            for (NewsDto dto : list) {
                String link = dto.getLink();
                dto.setLikeCount(likeCountMap.getOrDefault(link, 0));
                dto.setLiked(likedMap.getOrDefault(link, false));
                dto.setCommentCount(commentCountMap.getOrDefault(link, 0));
            }

        } catch (SQLException e) {
            System.err.println("loadLikeCommentCounts: " + e.getMessage());
        } finally {
            try {
                conn.close();
            } catch (Exception ignore) {
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 뉴스 총 건수
    // ─────────────────────────────────────────────────────────────
    public int getNewsCount(String sector, String keyword) {
        String where1 = buildWhere(sector, keyword, "1");
        String where2 = buildWhere(sector, keyword, "2");
        String sql = "SELECT COUNT(*) FROM (SELECT 1 FROM NEWS_DATA " + where1 +
                     " UNION ALL SELECT 1 FROM NEWS_DATA_SEC " + where2 + ")";
        Connection conn = connect(); if (conn == null) return 0;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            idx = setWhereParams(ps, idx, sector, keyword);
            setWhereParams(ps, idx, sector, keyword);
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt(1); }
        } catch (SQLException e) { System.err.println("getNewsCount: " + e.getMessage()); }
        finally { try { conn.close(); } catch (Exception ignore) {} }
        return 0;
    }

    // ─────────────────────────────────────────────────────────────
    // 사이드바용 섹터→종목 맵 (기타 제외)
    // ─────────────────────────────────────────────────────────────
    public LinkedHashMap<String, List<String>> getSidebarSectorMap() {
        LinkedHashMap<String, List<String>> sectorMap = new LinkedHashMap<>();
        String sql = "SELECT DISTINCT category FROM " +
            "(SELECT category FROM NEWS_DATA UNION ALL SELECT category FROM NEWS_DATA_SEC) ORDER BY category";
        Connection conn = connect();
        if (conn == null) return sectorMap;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String raw = rs.getString("category");
                String company = raw;
                String sector = null;
                int oi = raw.lastIndexOf('('), ci = raw.lastIndexOf(')');
                if (oi > 0 && ci > oi) {
                    company = raw.substring(0, oi).trim(); //news_data --> 회사  --> news_data_sec --> null
                    sector  = raw.substring(oi + 1, ci).trim();//news_date--> 진짜 색터 -- news_data_Sec -->진짜 있음
                }
                if (sector == null || sector.equals("기타")) continue; // 기타 제외
                sectorMap.computeIfAbsent(sector, k -> new ArrayList<>()).add(company);
            }
        } catch (SQLException e) { System.err.println("getSidebarSectorMap: " + e.getMessage()); }
        finally { try { conn.close(); } catch (Exception ignore) {} }
        return sectorMap;
    }

    // ─────────────────────────────────────────────────────────────
    // 사이드바 AI 분석 (현재 필터 기준)
    // ─────────────────────────────────────────────────────────────
    public Map<String, Object> getSidebarAnalysis(String sector, String keyword) {
        Map<String, Object> r = new HashMap<>();
        r.put("totalCount", 0); r.put("avgTypeProb", 0.0);
        r.put("avgClickbaitProb", 0.0); r.put("positiveCount", 0);

        String where1 = buildWhere(sector, keyword, "1");
        String where2 = buildWhere(sector, keyword, "2");
        String sql =
            "SELECT COUNT(*) as tc, ROUND(AVG(type_prob),1) as atp, " +
            "       ROUND(AVG(clickbait_prob),1) as acp, " +
            "       SUM(CASE WHEN sentiment='호재' THEN 1 ELSE 0 END) as pos " +
            "FROM (SELECT type_prob,clickbait_prob,sentiment FROM NEWS_DATA " + where1 +
            "      UNION ALL SELECT type_prob,clickbait_prob,sentiment FROM NEWS_DATA_SEC " + where2 + ")";

        Connection conn = connect(); if (conn == null) return r;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int idx = 1;
            idx = setWhereParams(ps, idx, sector, keyword);
            setWhereParams(ps, idx, sector, keyword);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    r.put("totalCount",      rs.getInt("tc"));
                    r.put("avgTypeProb",     rs.getDouble("atp"));
                    r.put("avgClickbaitProb",rs.getDouble("acp"));
                    r.put("positiveCount",   rs.getInt("pos"));
                }
            }
        } catch (SQLException e) { System.err.println("getSidebarAnalysis: " + e.getMessage()); }
        finally { try { conn.close(); } catch (Exception ignore) {} }
        return r;
    }

    // ─────────────────────────────────────────────────────────────
    // 좋아요 토글
    // ─────────────────────────────────────────────────────────────
    public int toggleLike(String newsLink, int userNum) {
        Connection conn = connect(); if (conn == null) return 0;
        try {
            String chk = "SELECT COUNT(*) FROM NEWS_LIKES WHERE news_link=? AND user_num=?";
            try (PreparedStatement ps = conn.prepareStatement(chk)) {
                ps.setString(1, newsLink); ps.setInt(2, userNum);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        try (PreparedStatement d = conn.prepareStatement(
                                "DELETE FROM NEWS_LIKES WHERE news_link=? AND user_num=?")) {
                            d.setString(1, newsLink); d.setInt(2, userNum); d.executeUpdate();
                        }
                    } else {
                        try (PreparedStatement i = conn.prepareStatement(
                                "INSERT INTO NEWS_LIKES(like_id,news_link,user_num) VALUES(NEWS_LIKES_SEQ.NEXTVAL,?,?)")) {
                            i.setString(1, newsLink); i.setInt(2, userNum); i.executeUpdate();
                        }
                    }
                }
            }
            return getLikeCountInternal(conn, newsLink);
        } catch (SQLException e) { System.err.println("toggleLike: " + e.getMessage()); return 0; }
        finally { try { conn.close(); } catch (Exception ignore) {} }
    }

    private int getLikeCountInternal(Connection conn, String link) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM NEWS_LIKES WHERE news_link=?")) {
            ps.setString(1, link);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : 0; }
        }
    }

    // 특정 뉴스의 좋아요 수와 현재 사용자 좋아요 여부를 조회
    public Map<String, Object> getLikeInfo(String newsLink, int userNum) {
        Map<String, Object> r = new HashMap<>();
        r.put("count", 0); r.put("liked", false);
        Connection conn = connect(); if (conn == null) return r;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) as total, SUM(CASE WHEN user_num=? THEN 1 ELSE 0 END) as mine FROM NEWS_LIKES WHERE news_link=?")) {
            ps.setInt(1, userNum); ps.setString(2, newsLink);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) { r.put("count", rs.getInt("total"));
                    r.put("liked", rs.getInt("mine")>0); }
            }
        } catch (SQLException e) { System.err.println("getLikeInfo: " + e.getMessage()); }
        finally { try { conn.close(); } catch (Exception ignore) {} }
        return r;
    }

    // ─────────────────────────────────────────────────────────────
    // 댓글
    // ─────────────────────────────────────────────────────────────
    public List<Map<String, Object>> getComments(String newsLink) {
        List<Map<String, Object>> list = new ArrayList<>();
        String sql = "SELECT c.comment_id, c.user_num, u.name as user_name, c.content, " +
                     "TO_CHAR(c.created_at,'YYYY-MM-DD HH24:MI') as created_at " +
                     "FROM NEWS_COMMENTS c LEFT JOIN users u ON c.user_num=u.num " +
                     "WHERE c.news_link=? ORDER BY c.created_at ASC";
        Connection conn = connect(); if (conn == null) return list;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newsLink);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("commentId", rs.getInt("comment_id"));
                    row.put("userNum",   rs.getInt("user_num"));
                    row.put("userName",  rs.getString("user_name"));
                    row.put("content",   rs.getString("content"));
                    row.put("createdAt", rs.getString("created_at"));
                    list.add(row);
                }
            }
        } catch (SQLException e) { System.err.println("getComments: " + e.getMessage()); }
        finally { try { conn.close(); } catch (Exception ignore) {} }
        return list;
    }

    // 뉴스 댓글을 저장
    public int insertComment(String link, int userNum, String content) {
        Connection conn = connect(); if (conn == null) return 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO NEWS_COMMENTS(comment_id,news_link,user_num,content) VALUES(NEWS_COMMENTS_SEQ.NEXTVAL,?,?,?)")) {
            ps.setString(1, link); ps.setInt(2, userNum); ps.setString(3, content);
            return ps.executeUpdate();
        } catch (SQLException e) { System.err.println("insertComment: " + e.getMessage()); return 0; }
        finally { try { conn.close(); } catch (Exception ignore) {} }
    }

    // 본인 댓글만 삭제하도록 조건을 걸어 삭제
    public int deleteComment(int commentId, int userNum) {
        Connection conn = connect(); if (conn == null) return 0;
        try (PreparedStatement ps = conn.prepareStatement(
                "DELETE FROM NEWS_COMMENTS WHERE comment_id=? AND user_num=?")) {
            ps.setInt(1, commentId); ps.setInt(2, userNum); return ps.executeUpdate();
        } catch (SQLException e) { System.err.println("deleteComment: " + e.getMessage()); return 0; }
        finally { try { conn.close(); } catch (Exception ignore) {} }
    }

    // ─────────────────────────────────────────────────────────────
    // 내부 헬퍼
    // ─────────────────────────────────────────────────────────────
    /** sector = 섹터 필터("IT/플랫폼"), keyword = 제목 키워드, tableAlias 미사용 */
    private String buildWhere(String sector, String keyword, String alias) {
        List<String> conditions = new ArrayList<>();

        // 1. 섹터 조건 (괄호 추출 로직 적용)
        if (sector != null && !sector.trim().isEmpty()) {
            // category 전체에서 찾는게 아니라 추출된 sector_name과 정확히 일치하는지 확인
            conditions.add("(CASE WHEN INSTR(category,'(')>0 " +
                    "THEN TRIM(SUBSTR(category, INSTR(category,'(')+1, INSTR(category,')')-INSTR(category,'(')-1)) " +
                    "ELSE TRIM(category) END) = ?");
        }

        // 2. 키워드 조건
        if (keyword != null && !keyword.trim().isEmpty()) {
            conditions.add("title LIKE ?");
        }

        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    private int setWhereParams(PreparedStatement ps, int idx, String sector, String keyword) throws SQLException {
        if (sector != null && !sector.trim().isEmpty()) {
            ps.setString(idx++, sector); // LIKE가 아니면 %를 붙이지 않습니다.
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            ps.setString(idx++, "%" + keyword + "%");
        }
        return idx;
    }

    private void bindLinkParams(PreparedStatement ps, List<String> links) throws SQLException {
        for (int i = 0; i < links.size(); i++) {
            ps.setString(i + 1, links.get(i));
        }
    }

    // DB에서 읽은 발행일을 화면 표시용 문자열로 정리
    private String formatPubDate(ResultSet rs, String col) {
        try {
            String raw = rs.getString(col);
            if (raw != null && raw.length() >= 16) return raw.substring(0, 16);
            return raw != null ? raw : "";
        } catch (SQLException e) {
            try {
                Timestamp ts = rs.getTimestamp(col);
                if (ts != null) return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(ts);
            } catch (SQLException ignore) {}
            return "";
        }
    }

    // 문자열이 비어 있는지 공통으로 확인
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
