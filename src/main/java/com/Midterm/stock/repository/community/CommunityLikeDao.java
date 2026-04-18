package com.Midterm.stock.repository.community;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;

@Repository
public class CommunityLikeDao {

    @Autowired
    private DataSource dataSource;

    private Connection conn = null;
    private PreparedStatement pstmt = null;
    private ResultSet rs = null;

    public CommunityLikeDao() {
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

    public boolean existsLike(int board_id, int user_num) {
        connect();
        boolean exists = false;
        String sql = "select count(*) from community_like where board_id = ? and user_num = ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, board_id);
            pstmt.setInt(2, user_num);
            rs = pstmt.executeQuery();

            if (rs.next()) {
                exists = rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return exists;
    }

    public int insertLike(int board_id, int user_num) {
        connect();
        int count = -1;
        String sql = "insert into community_like(like_id, board_id, user_num, created_at) "
                + "values(community_like_seq.nextval, ?, ?, sysdate)";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, board_id);
            pstmt.setInt(2, user_num);
            count = pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return count;
    }

    public int deleteLike(int board_id, int user_num) {
        connect();
        int count = -1;
        String sql = "delete from community_like where board_id = ? and user_num = ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, board_id);
            pstmt.setInt(2, user_num);
            count = pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return count;
    }

    public int increaseLikeCount(int board_id) {
        connect();
        int count = -1;
        String sql = "update community_board set like_count = like_count + 1 where board_id = ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, board_id);
            count = pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return count;
    }

    public int decreaseLikeCount(int board_id) {
        connect();
        int count = -1;
        String sql = "update community_board "
                + "set like_count = case when like_count > 0 then like_count - 1 else 0 end "
                + "where board_id = ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, board_id);
            count = pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return count;
    }

    public int getLikeCount(int board_id) {
        connect();
        int likeCount = 0;
        String sql = "select like_count from community_board where board_id = ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, board_id);
            rs = pstmt.executeQuery();

            if (rs.next()) {
                likeCount = rs.getInt("like_count");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return likeCount;
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