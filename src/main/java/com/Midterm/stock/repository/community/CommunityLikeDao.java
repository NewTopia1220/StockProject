package com.Midterm.stock.repository.community;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

@Repository
public class CommunityLikeDao {

    @Autowired
    private DataSource dataSource;

    public CommunityLikeDao() {
        System.setProperty("oracle.net.wallet_location",
                "(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=C:/oraclepw)))");
    }

    // 특정 사용자가 게시글에 좋아요를 눌렀는지 확인합니다.
    public boolean existsLike(int board_id, int user_num) {
        String sql = "select count(*) from community_like where board_id = ? and user_num = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, board_id);
            pstmt.setInt(2, user_num);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return false;
    }

    // 게시글에 저장된 현재 좋아요 수를 조회합니다.
    public int getLikeCount(int board_id) {
        String sql = "select like_count from community_board where board_id = ?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, board_id);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("like_count");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return 0;
    }

    // 좋아요 추가/취소와 게시글 like_count 반영을 하나의 트랜잭션으로 처리합니다.
    // 같은 사용자가 같은 글에 동시에 좋아요를 눌러도 DB 유니크 제약조건을 기준으로 중복을 막습니다.
    public Map<String, Object> toggleLike(int board_id, int user_num) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("liked", false);
        result.put("likeCount", 0);

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try {
                boolean exists = existsLikeForUpdate(conn, board_id, user_num);

                if (exists) {
                    deleteLike(conn, board_id, user_num);
                    decreaseLikeCount(conn, board_id);
                    result.put("liked", false);
                } else {
                    try {
                        insertLike(conn, board_id, user_num);
                        increaseLikeCount(conn, board_id);
                        result.put("liked", true);
                    } catch (SQLException e) {
                        // 동시 요청으로 이미 같은 좋아요가 먼저 들어간 경우를 방어합니다.
                        if (isUniqueConstraintViolation(e)) {
                            result.put("liked", true);
                        } else {
                            throw e;
                        }
                    }
                }

                result.put("likeCount", getLikeCount(conn, board_id));
                conn.commit();
                result.put("success", true);

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return result;
    }

    // 현재 트랜잭션 안에서 좋아요 존재 여부를 확인합니다.
    private boolean existsLikeForUpdate(Connection conn, int board_id, int user_num) throws SQLException {
        String sql = "select count(*) from community_like where board_id = ? and user_num = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, board_id);
            pstmt.setInt(2, user_num);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    // 좋아요 테이블에 좋아요 행을 추가합니다.
    private void insertLike(Connection conn, int board_id, int user_num) throws SQLException {
        String sql = "insert into community_like(like_id, board_id, user_num, created_at) "
                + "values(community_like_seq.nextval, ?, ?, sysdate)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, board_id);
            pstmt.setInt(2, user_num);
            pstmt.executeUpdate();
        }
    }

    // 좋아요 테이블에서 해당 사용자의 좋아요를 삭제합니다.
    private void deleteLike(Connection conn, int board_id, int user_num) throws SQLException {
        String sql = "delete from community_like where board_id = ? and user_num = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, board_id);
            pstmt.setInt(2, user_num);
            pstmt.executeUpdate();
        }
    }

    // 게시글의 좋아요 수를 1 증가시킵니다.
    private void increaseLikeCount(Connection conn, int board_id) throws SQLException {
        String sql = "update community_board set like_count = like_count + 1 where board_id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, board_id);
            pstmt.executeUpdate();
        }
    }

    // 게시글의 좋아요 수를 1 감소시키되, 0 아래로 내려가지 않게 합니다.
    private void decreaseLikeCount(Connection conn, int board_id) throws SQLException {
        String sql = "update community_board "
                + "set like_count = case when like_count > 0 then like_count - 1 else 0 end "
                + "where board_id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, board_id);
            pstmt.executeUpdate();
        }
    }

    // 현재 트랜잭션 안에서 최신 좋아요 수를 읽어 반환합니다.
    private int getLikeCount(Connection conn, int board_id) throws SQLException {
        String sql = "select like_count from community_board where board_id = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, board_id);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("like_count");
                }
            }
        }

        return 0;
    }

    // Oracle 유니크 제약조건 위반(ORA-00001)인지 확인합니다.
    private boolean isUniqueConstraintViolation(SQLException e) {
        return e.getErrorCode() == 1;
    }
}
