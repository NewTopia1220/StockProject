package com.Midterm.stock.repository.community;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Repository
public class CommunityExtraDao {

    @Autowired
    private DataSource dataSource;

    public CommunityExtraDao() {
        System.setProperty("oracle.net.wallet_location",
                "(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=C:/oraclepw)))");
    }

    // 뉴스 링크를 기준으로 뉴스 제목을 조회합니다.
    public String getNewsTitleByLink(String newsLink) {
        if (newsLink == null || newsLink.isBlank()) {
            return null;
        }

        String sql = "select title from ( "
                + " select title from news_data where trim(link) = ? "
                + " union all "
                + " select title from news_data_sec where trim(link) = ? "
                + " ) where rownum = 1";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            String normalizedLink = newsLink.trim();
            pstmt.setString(1, normalizedLink);
            pstmt.setString(2, normalizedLink);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("title");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    // 입력한 키워드와 관련된 뉴스 목록을 조회합니다.
    public ArrayList<Map<String, String>> searchRelatedNews(String keyword) {
        ArrayList<Map<String, String>> newsList = new ArrayList<>();

        String sql = "select * from ( "
                + "  select link, title, summary, pub_date from news_data "
                + "  where title like ? or summary like ? "
                + "  union all "
                + "  select link, title, summary, pub_date from news_data_sec "
                + "  where title like ? or summary like ? "
                + "  order by pub_date desc "
                + ") where rownum <= 5";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, "%" + keyword + "%");
            pstmt.setString(2, "%" + keyword + "%");
            pstmt.setString(3, "%" + keyword + "%");
            pstmt.setString(4, "%" + keyword + "%");

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    Map<String, String> item = new HashMap<>();
                    item.put("link", rs.getString("link"));
                    item.put("title", rs.getString("title"));
                    item.put("summary", rs.getString("summary"));
                    item.put("pubDate", rs.getString("pub_date"));
                    newsList.add(item);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return newsList;
    }

    // 커뮤니티에서 많이 언급된 인기 테마 카테고리를 조회합니다.
    @Cacheable("popularThemeCategories")
    public ArrayList<Map<String, Object>> getPopularThemeCategories() {
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

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                Map<String, Object> item = new HashMap<>();
                item.put("category", rs.getString("category"));
                item.put("postCount", rs.getInt("post_count"));
                popularCategories.add(item);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return popularCategories;
    }
}
