package com.Midterm.stock.repository.community;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

@Repository
public class CommunityTagDao {

    @Autowired
    private DataSource dataSource;

    public CommunityTagDao() {
        System.setProperty("oracle.net.wallet_location",
                "(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=C:/oraclepw)))");
    }

    // 게시글에 입력된 태그 문자열을 저장합니다.
    // 외부에서 커넥션을 직접 넘기지 않는 일반 호출용 메서드입니다.
    public void saveTags(int boardId, String tagNames) throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            saveTags(conn, boardId, tagNames);
        }
    }

    // 게시글 저장/수정 트랜잭션 안에서 같은 커넥션을 공유해 태그를 저장합니다.
    public void saveTags(Connection conn, int boardId, String tagNames) throws SQLException {
        Set<String> uniqueTags = parseTags(tagNames);

        if (uniqueTags.isEmpty()) {
            return;
        }

        for (String tagName : uniqueTags) {
            int tagId = findTagId(conn, tagName);
            if (tagId == 0) {
                tagId = insertTag(conn, tagName);
            }
            insertBoardTag(conn, boardId, tagId);
        }
    }

    // 사용자가 입력한 태그 문자열을 중복 없는 태그 집합으로 정리합니다.
    private Set<String> parseTags(String tagNames) {
        Set<String> tags = new LinkedHashSet<>();

        if (tagNames == null || tagNames.trim().isEmpty()) {
            return tags;
        }

        String normalized = tagNames.replace("#", " ");
        String[] split = normalized.split("[,\\s]+");

        for (String raw : split) {
            String tag = raw == null ? "" : raw.trim();
            if (!tag.isEmpty()) {
                tags.add(tag);
            }
        }

        return tags;
    }

    // 태그 이름으로 기존 태그 ID를 찾습니다.
    private int findTagId(Connection conn, String tagName) throws SQLException {
        String sql = "select tag_id from community_tag where tag_name = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, tagName);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("tag_id");
                }
            }
        }

        return 0;
    }

    // 새 태그를 community_tag 테이블에 등록하고 생성된 tag_id를 반환합니다.
    private int insertTag(Connection conn, String tagName) throws SQLException {
        int tagId = 0;

        String seqSql = "select community_tag_seq.nextval from dual";
        try (PreparedStatement pstmt = conn.prepareStatement(seqSql);
             ResultSet rs = pstmt.executeQuery()) {

            if (rs.next()) {
                tagId = rs.getInt(1);
            }
        }

        String insertSql = "insert into community_tag(tag_id, tag_name) values(?, ?)";
        try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            pstmt.setInt(1, tagId);
            pstmt.setString(2, tagName);
            pstmt.executeUpdate();
        }

        return tagId;
    }

    // 게시글과 태그 연결 정보가 없을 때만 community_board_tag에 추가합니다.
    private void insertBoardTag(Connection conn, int boardId, int tagId) throws SQLException {
        String checkSql = "select count(*) from community_board_tag where board_id = ? and tag_id = ?";

        int count = 0;
        try (PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
            pstmt.setInt(1, boardId);
            pstmt.setInt(2, tagId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    count = rs.getInt(1);
                }
            }
        }

        if (count == 0) {
            String insertSql = "insert into community_board_tag(board_id, tag_id) values(?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
                pstmt.setInt(1, boardId);
                pstmt.setInt(2, tagId);
                pstmt.executeUpdate();
            }
        }
    }

    // 게시글에 연결된 태그를 모두 삭제합니다.
    // 일반 호출용 메서드입니다.
    public int deleteBoardTags(int boardId) {
        try (Connection conn = dataSource.getConnection()) {
            return deleteBoardTags(conn, boardId);
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    // 게시글 삭제/수정 트랜잭션 안에서 같은 커넥션으로 태그 연결을 삭제합니다.
    public int deleteBoardTags(Connection conn, int boardId) throws SQLException {
        String sql = "delete from community_board_tag where board_id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, boardId);
            return pstmt.executeUpdate();
        }
    }

    // 게시글 상세 조회 시 연결된 태그 이름들을 문자열로 묶어서 반환합니다.
    public String getTagNamesByBoardId(int boardId) {
        StringBuilder tagNames = new StringBuilder();

        String sql = "select ct.tag_name "
                + "from community_board_tag cbt "
                + "join community_tag ct on cbt.tag_id = ct.tag_id "
                + "where cbt.board_id = ? "
                + "order by ct.tag_id";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, boardId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    if (tagNames.length() > 0) {
                        tagNames.append(", ");
                    }
                    tagNames.append(rs.getString("tag_name"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return tagNames.toString();
    }
}
