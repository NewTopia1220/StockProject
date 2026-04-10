package com.Midterm.stock.repository.community;

import org.springframework.stereotype.Repository;

import java.sql.*;

@Repository
public class CommunityLikeDao {
    private String driver = "oracle.jdbc.OracleDriver";
    private String url = "jdbc:oracle:thin:@stoxle_high?TNS_ADMIN=C:/oraclepw";
    private String id = "ADMIN";
    private String pw = "Heeyoun1220!";

    private Connection conn = null;
    private PreparedStatement pstmt = null;
    private ResultSet rs = null;

    // 생성자: 드라이버 로딩 및 지갑 설정
    public CommunityLikeDao() {
        System.out.println("CommunityLikeDao 생성자 호출 - 클라우드 설정 시작");
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

    // 좋아요 눌렀는지 확인
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

    // 좋아요 추가
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

    // 좋아요 취소
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

    // 좋아요 수 증가
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

    // 좋아요 수 감소
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

    // 게시글 좋아요 수 조회
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
