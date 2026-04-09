package com.Midterm.stock.repository.community;

import com.Midterm.stock.dto.CommunityCommentDto;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;

@Repository
public class CommunityCommentDao {
    private String driver = "oracle.jdbc.OracleDriver";
    private String url = "jdbc:oracle:thin:@stoxle_high?TNS_ADMIN=C:/oraclepw";
    private String id = "ADMIN";
    private String pw = "Heeyoun1220!";

    private Connection conn = null;
    private PreparedStatement pstmt = null;
    private ResultSet rs = null;

    // 생성자: 드라이버 로딩 및 지갑 설정
    public CommunityCommentDao() {
        System.out.println("CommunityCommentDao 생성자 호출 - 클라우드 설정 시작");
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

    // 댓글 목록 조회
    public ArrayList<CommunityCommentDto> getCommentsByBoardId(int board_id) {
        connect();
        ArrayList<CommunityCommentDto> comments = new ArrayList<>();
        String sql = "select cc.comment_id, cc.board_id, cc.user_num, u.name as user_name, "
                + "cc.content, cc.created_at, cc.updated_at "
                + "from community_comment cc "
                + "join users u on cc.user_num = u.num "
                + "where cc.board_id = ? "
                + "order by cc.comment_id asc";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, board_id);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                CommunityCommentDto dto = new CommunityCommentDto();
                dto.setComment_id(rs.getInt("comment_id"));
                dto.setBoard_id(rs.getInt("board_id"));
                dto.setUser_num(rs.getInt("user_num"));
                dto.setUserName(rs.getString("user_name"));
                dto.setContent(rs.getString("content"));
                dto.setCreated_at(rs.getTimestamp("created_at"));
                dto.setUpdated_at(rs.getTimestamp("updated_at"));
                comments.add(dto);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return comments;
    }

    // 댓글 등록
    public int insertComment(int board_id, int user_num, String content) {
        connect();
        int count = -1;
        String sql = "insert into community_comment(comment_id, board_id, user_num, content, created_at, updated_at) "
                + "values(community_comment_seq.nextval, ?, ?, ?, sysdate, sysdate)";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, board_id);
            pstmt.setInt(2, user_num);
            pstmt.setString(3, content);
            count = pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return count;
    }

    // 특정 사용자의 댓글 수 조회
    public int getCommentCountByUserNum(int user_num) {
        connect();
        int count = 0;
        String sql = "select count(*) from community_comment where user_num = ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, user_num);
            rs = pstmt.executeQuery();

            if (rs.next()) {
                count = rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return count;
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
