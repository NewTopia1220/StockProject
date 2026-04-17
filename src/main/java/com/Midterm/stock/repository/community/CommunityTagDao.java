package com.Midterm.stock.repository.community;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.LinkedHashSet;
import java.util.Set;

@Repository
public class CommunityTagDao {

    @Autowired
    private DataSource dataSource;

    private Connection conn = null;
    private PreparedStatement pstmt = null;
    private ResultSet rs = null;

    public CommunityTagDao() {
        System.setProperty("oracle.net.wallet_location",
                "(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=C:/oraclepw)))");
    }

    public Connection connect() {
        try {
            conn = dataSource.getConnection();
        } catch (SQLException e) {
            System.err.println("DB 접속 실패: " + e.getMessage());
            e.printStackTrace();
        }
        return conn;
    }

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

    private int findTagId(String tagName) throws SQLException {
        String sql = "select tag_id from community_tag where tag_name = ?";
        PreparedStatement localPstmt = null;
        ResultSet localRs = null;

        try {
            localPstmt = conn.prepareStatement(sql);
            localPstmt.setString(1, tagName);
            localRs = localPstmt.executeQuery();

            if (localRs.next()) {
                return localRs.getInt("tag_id");
            }
            return 0;
        } finally {
            if (localRs != null) localRs.close();
            if (localPstmt != null) localPstmt.close();
        }
    }

    private int insertTag(String tagName) throws SQLException {
        int tagId = 0;
        PreparedStatement localPstmt = null;
        ResultSet localRs = null;

        try {
            String seqSql = "select community_tag_seq.nextval from dual";
            localPstmt = conn.prepareStatement(seqSql);
            localRs = localPstmt.executeQuery();

            if (localRs.next()) {
                tagId = localRs.getInt(1);
            }

            localRs.close();
            localPstmt.close();

            String insertSql = "insert into community_tag(tag_id, tag_name) values(?, ?)";
            localPstmt = conn.prepareStatement(insertSql);
            localPstmt.setInt(1, tagId);
            localPstmt.setString(2, tagName);
            localPstmt.executeUpdate();

            return tagId;
        } finally {
            if (localRs != null) localRs.close();
            if (localPstmt != null) localPstmt.close();
        }
    }

    private void insertBoardTag(int boardId, int tagId) throws SQLException {
        PreparedStatement localPstmt = null;
        ResultSet localRs = null;

        try {
            String checkSql = "select count(*) from community_board_tag where board_id = ? and tag_id = ?";
            localPstmt = conn.prepareStatement(checkSql);
            localPstmt.setInt(1, boardId);
            localPstmt.setInt(2, tagId);
            localRs = localPstmt.executeQuery();

            int count = 0;
            if (localRs.next()) {
                count = localRs.getInt(1);
            }

            localRs.close();
            localPstmt.close();

            if (count == 0) {
                String insertSql = "insert into community_board_tag(board_id, tag_id) values(?, ?)";
                localPstmt = conn.prepareStatement(insertSql);
                localPstmt.setInt(1, boardId);
                localPstmt.setInt(2, tagId);
                localPstmt.executeUpdate();
            }
        } finally {
            if (localRs != null) localRs.close();
            if (localPstmt != null) localPstmt.close();
        }
    }

    public void deleteBoardTags(int boardId) {
        connect();

        try {
            String sql = "delete from community_board_tag where board_id = ?";
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, boardId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }
    }

    public String getTagNamesByBoardId(int boardId) {
        connect();
        StringBuilder tagNames = new StringBuilder();

        String sql = "select ct.tag_name "
                + "from community_board_tag cbt "
                + "join community_tag ct on cbt.tag_id = ct.tag_id "
                + "where cbt.board_id = ? "
                + "order by ct.tag_id";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, boardId);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                if (tagNames.length() > 0) {
                    tagNames.append(", ");
                }
                tagNames.append(rs.getString("tag_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return tagNames.toString();
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
}
