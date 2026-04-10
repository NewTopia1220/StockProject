package com.Midterm.stock.repository.community;

import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

@Repository
public class CommunityTagDao {
    private String driver = "oracle.jdbc.OracleDriver";
    private String url = "jdbc:oracle:thin:@stoxle_high?TNS_ADMIN=C:/oraclepw";
    private String id = "ADMIN";
    private String pw = "Heeyoun1220!";

    private Connection conn = null;
    private PreparedStatement pstmt = null;
    private ResultSet rs = null;

    // 생성자: 드라이버 로딩 및 지갑 설정
    public CommunityTagDao() {
        System.out.println("CommunityTagDao 생성자 호출 - 클라우드 설정 시작");
        try {
            Class.forName(driver);
            System.setProperty("oracle.net.wallet_location",
                    "(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=C:/oraclepw)))");
            System.out.println("드라이버 로드 및 클라우드 지갑 설정 성공");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    // DB 연결
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

    // 태그 문자열 저장
    // 쉼표 방식과 #태그 방식 둘 다 처리
    public void saveTags(int boardId, String tagNames) throws SQLException {
        connect();

        try {
            Set<String> uniqueTags = parseTags(tagNames);

            for (String tagName : uniqueTags) {
                int tagId = findTagId(tagName);
                if (tagId == 0) {
                    tagId = insertTag(tagName);
                }
                insertBoardTag(boardId, tagId);
            }
        } finally {
            closeAll();
        }
    }

    // 입력된 태그 문자열을 파싱
    // 예: "네이버, AI" 또는 "#네이버 #AI"
    private Set<String> parseTags(String tagNames) {
        Set<String> uniqueTags = new LinkedHashSet<>();

        if (tagNames == null || tagNames.isBlank()) {
            return uniqueTags;
        }

        String normalized = tagNames.trim();

        if (normalized.contains("#")) {
            String[] parts = normalized.split("#");
            for (String part : parts) {
                String tag = part.trim();
                if (!tag.isEmpty()) {
                    uniqueTags.add(tag);
                }
            }
        } else {
            String[] parts = normalized.split(",");
            for (String part : parts) {
                String tag = part.trim();
                if (!tag.isEmpty()) {
                    uniqueTags.add(tag);
                }
            }
        }

        return uniqueTags;
    }

    // 태그명으로 tag_id 조회
    private int findTagId(String tagName) throws SQLException {
        String sql = "select tag_id from community_tag where tag_name = ?";
        pstmt = conn.prepareStatement(sql);
        pstmt.setString(1, tagName);
        rs = pstmt.executeQuery();

        if (rs.next()) {
            return rs.getInt("tag_id");
        }
        return 0;
    }

    // 새 태그 저장
    private int insertTag(String tagName) throws SQLException {
        String seqSql = "select community_tag_seq.nextval from dual";
        pstmt = conn.prepareStatement(seqSql);
        rs = pstmt.executeQuery();

        int tagId = 0;
        if (rs.next()) {
            tagId = rs.getInt(1);
        }

        rs.close();
        pstmt.close();

        String insertSql = "insert into community_tag(tag_id, tag_name) values(?, ?)";
        pstmt = conn.prepareStatement(insertSql);
        pstmt.setInt(1, tagId);
        pstmt.setString(2, tagName);
        pstmt.executeUpdate();

        return tagId;
    }

    // 게시글과 태그 연결 저장
    private void insertBoardTag(int boardId, int tagId) throws SQLException {
        String checkSql = "select count(*) from community_board_tag where board_id = ? and tag_id = ?";
        pstmt = conn.prepareStatement(checkSql);
        pstmt.setInt(1, boardId);
        pstmt.setInt(2, tagId);
        rs = pstmt.executeQuery();

        boolean exists = false;
        if (rs.next()) {
            exists = rs.getInt(1) > 0;
        }

        rs.close();
        pstmt.close();

        if (!exists) {
            String insertSql = "insert into community_board_tag(board_id, tag_id) values(?, ?)";
            pstmt = conn.prepareStatement(insertSql);
            pstmt.setInt(1, boardId);
            pstmt.setInt(2, tagId);
            pstmt.executeUpdate();
        }
    }

    // 게시글에 연결된 태그 매핑 삭제
    public void deleteBoardTags(int boardId) throws SQLException {
        connect();

        try {
            String sql = "delete from community_board_tag where board_id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, boardId);
            pstmt.executeUpdate();
        } finally {
            closeAll();
        }
    }

    // 게시글에 연결된 태그 목록 문자열로 조회
    public String getTagNamesByBoardId(int boardId) {
        connect();

        try {
            String sql = "select ct.tag_name "
                    + "from community_board_tag cbt "
                    + "join community_tag ct on cbt.tag_id = ct.tag_id "
                    + "where cbt.board_id = ? "
                    + "order by ct.tag_name asc";

            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, boardId);
            rs = pstmt.executeQuery();

            ArrayList<String> tags = new ArrayList<>();
            while (rs.next()) {
                tags.add(rs.getString("tag_name"));
            }

            return String.join(", ", tags);
        } catch (SQLException e) {
            e.printStackTrace();
            return "";
        } finally {
            closeAll();
        }
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
