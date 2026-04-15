package com.Midterm.stock.repository.community;

import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Repository
public class CommunityExtraDao {
    private String driver = "oracle.jdbc.OracleDriver";
    private String url = "jdbc:oracle:thin:@stoxle_high?TNS_ADMIN=C:/oraclepw";
    private String id = "ADMIN";
    private String pw = "Heeyoun1220!";

    private Connection conn = null;
    private PreparedStatement pstmt = null;
    private ResultSet rs = null;

    // 생성자: 드라이버 로딩 및 지갑 설정
    public CommunityExtraDao() {
        /*System.out.println("CommunityExtraDao 생성자 호출 - 클라우드 설정 시작");*/
        try {
            Class.forName(driver);
            System.setProperty("oracle.net.wallet_location",
                    "(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=C:/oraclepw)))");
            /*System.out.println("드라이버 로드 및 클라우드 지갑 설정 성공");*/
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    // DB 연결
    public Connection connect() {
        try {
            conn = DriverManager.getConnection(url, id, pw);
            /*System.out.println("오라클 클라우드 DB 접속 성공!");*/
        } catch (SQLException e) {
            System.err.println("DB 접속 실패: " + e.getMessage());
            e.printStackTrace();
        }
        return conn;
    }

    // 뉴스 링크로 뉴스 제목 조회
    public String getNewsTitleByLink(String newsLink) {
        connect();
        String title = null;
        String sql = "select title from news_data where link = ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, newsLink);
            rs = pstmt.executeQuery();

            if (rs.next()) {
                title = rs.getString("title");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return title;
    }

    // 관련 뉴스 검색
    public ArrayList<Map<String, String>> searchRelatedNews(String keyword) {
        connect();
        ArrayList<Map<String, String>> newsList = new ArrayList<>();

        String sql = "select * from ( "
                + " select link, title, summary, pub_date "
                + " from news_data "
                + " where title like ? or summary like ? "
                + " order by pub_date desc "
                + ") where rownum <= 5";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, "%" + keyword + "%");
            pstmt.setString(2, "%" + keyword + "%");
            rs = pstmt.executeQuery();

            while (rs.next()) {
                Map<String, String> item = new HashMap<>();
                item.put("link", rs.getString("link"));
                item.put("title", rs.getString("title"));
                item.put("summary", rs.getString("summary"));
                item.put("pubDate", rs.getString("pub_date"));
                newsList.add(item);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return newsList;
    }

    // 인기 테마 카테고리 조회
    public ArrayList<Map<String, Object>> getPopularThemeCategories() {
        connect();
        ArrayList<Map<String, Object>> popularCategories = new ArrayList<>();

        String sql = "select * from ( "
                + " select category, count(*) as post_count "
                + " from community_board "
                + " where category in ("
                + " '반도체·AI', "
                + " '2차전지', "
                + " '제약·바이오', "
                + " '금융·밸류업', "
                + " '방산·우주항공', "
                + " 'IT·플랫폼', "
                + " '엔터·미디어', "
                + " '자동차·모빌리티' "
                + " ) "
                + " group by category "
                + " order by count(*) desc, category asc "
                + ") where rownum <= 3";

        try {
            pstmt = conn.prepareStatement(sql);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("category", rs.getString("category"));
                item.put("postCount", rs.getInt("post_count"));
                popularCategories.add(item);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return popularCategories;
    }

    // 공통 자원 해제
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
