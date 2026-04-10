package com.Midterm.stock.repository;

import com.Midterm.stock.dto.CommunityDto;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class CommunityDao {
    private static final List<String> THEME_CATEGORIES = List.of(
            "반도체·AI",
            "2차전지",
            "제약·바이오",
            "금융·밸류업",
            "방산·우주항공",
            "IT·플랫폼",
            "엔터·미디어",
            "자동차·모빌리티"
    );

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
    public CommunityDao() {
        System.out.println("CommunityDao 생성자 호출 - 클라우드 설정 시작");
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

    // 게시글의 목록 (기존 로직 유지)
    public ArrayList<CommunityDto> getArticles(int start, int end) {
        return getArticles(start, end, null, null);
    }

    public ArrayList<CommunityDto> getArticles(int start, int end, String category, String keyword) {
        connect();
        ArrayList<CommunityDto> lists = new ArrayList<>();
        StringBuilder sql = new StringBuilder();
        sql.append("select * ")
                .append("from ( ")
                .append("    select row_number() over(order by board_id desc) as rnum, ")
                .append("           board_id, user_num, category, title, content, ")
                .append("           view_count, like_count, created_at, updated_at ")
                .append("    from community_board ")
                .append("    where 1 = 1 ");

        boolean hasCategory = category != null && !category.isBlank();
        boolean hasKeyword = keyword != null && !keyword.isBlank();

        if (hasCategory) {
            sql.append(" and category = ? ");
        }
        if (hasKeyword) {
            sql.append(" and (title like ? or content like ?) ");
        }

        sql.append(") ")
                .append("where rnum between ? and ?");

        try {
            pstmt = conn.prepareStatement(sql.toString());

            int parameterIndex = 1;
            if (hasCategory) {
                pstmt.setString(parameterIndex++, category.trim());
            }
            if (hasKeyword) {
                String keywordPattern = "%" + keyword.trim() + "%";
                pstmt.setString(parameterIndex++, keywordPattern);
                pstmt.setString(parameterIndex++, keywordPattern);
            }

            pstmt.setInt(parameterIndex++, start);
            pstmt.setInt(parameterIndex, end);
            rs = pstmt.executeQuery();

            while (rs.next()){
                CommunityDto dto = new CommunityDto();
                dto.setBoard_id(rs.getInt("board_id"));
                dto.setUser_num(rs.getInt("user_num"));
                dto.setCategory(rs.getString("category"));
                dto.setTitle(rs.getString("title"));
                dto.setContent(rs.getString("content"));
                dto.setView_count(rs.getInt("view_count"));
                dto.setLike_count(rs.getInt("like_count"));
                dto.setCreated_at(rs.getTimestamp("created_at"));
                dto.setUpdated_at(rs.getTimestamp("updated_at"));
                lists.add(dto);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }
        return lists;
    }

    public ArrayList<Map<String, Object>> getPopularThemeCategories() {
        connect();
        ArrayList<Map<String, Object>> categories = new ArrayList<>();
        String placeholders = String.join(", ", Collections.nCopies(THEME_CATEGORIES.size(), "?"));
        String sql = "select category, count(*) as post_count "
                + "from community_board "
                + "where category in (" + placeholders + ") "
                + "group by category "
                + "order by count(*) desc, max(created_at) desc, category asc";

        try {
            pstmt = conn.prepareStatement(sql);
            for (int i = 0; i < THEME_CATEGORIES.size(); i++) {
                pstmt.setString(i + 1, THEME_CATEGORIES.get(i));
            }
            rs = pstmt.executeQuery();

            while (rs.next()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("category", rs.getString("category"));
                item.put("postCount", rs.getInt("post_count"));
                categories.add(item);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return categories;
    }

    // 글 작성
    public int insertArticle(CommunityDto dto) {
        connect();
        int count = -1;
        String sql = "insert into community_board(board_id, user_num, category, title, content) "
                + "values(community_board_seq.nextval, ?, ?, ?, ?)";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, dto.getUser_num());
            pstmt.setString(2, dto.getCategory());
            pstmt.setString(3, dto.getTitle());
            pstmt.setString(4, dto.getContent());
            count = pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }
        return count;
    }

    // 게시글 상세조회
    public CommunityDto getArticle(int board_id) {
        connect();
        CommunityDto dto = null;
        String sql = "select * from community_board where board_id=?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, board_id);
            rs = pstmt.executeQuery();

            if (rs.next()){
                dto = new CommunityDto();
                dto.setBoard_id(rs.getInt("board_id"));
                dto.setUser_num(rs.getInt("user_num"));
                dto.setCategory(rs.getString("category"));
                dto.setTitle(rs.getString("title"));
                dto.setContent(rs.getString("content"));
                dto.setView_count(rs.getInt("view_count"));
                dto.setLike_count(rs.getInt("like_count"));
                dto.setCreated_at(rs.getTimestamp("created_at"));
                dto.setUpdated_at(rs.getTimestamp("updated_at"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }
        return dto;
    }

    // 조회수 증가
    public void updateViewcount(int board_id){
        connect();
        String sql = "update community_board set view_count = view_count + 1 where board_id = ?";
        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, board_id);
            pstmt.executeUpdate();
        } catch(SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }
    }

    // 수정
    public int updateArticle(CommunityDto dto) {
        connect();
        int count = -1;
        String sql = "update community_board "
                + "set category = ?, title = ?, content = ?, updated_at = sysdate "
                + "where board_id = ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, dto.getCategory());
            pstmt.setString(2, dto.getTitle());
            pstmt.setString(3, dto.getContent());
            pstmt.setInt(4, dto.getBoard_id());
            count = pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }
        return count;
    }

    // 삭제
    public int deleteArticle(int board_id){
        connect();
        int count = -1;
        String sql = "delete from community_board where board_id = ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, board_id);
            count = pstmt.executeUpdate();
        } catch(SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }
        return count;
    }

    // 자원 해제 공통 메서드 (코드 중복 방지)
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
