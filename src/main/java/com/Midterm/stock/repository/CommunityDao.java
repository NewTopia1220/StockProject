package com.Midterm.stock.repository;

import com.Midterm.stock.dto.CommunityDto;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;

@Repository
public class CommunityDao {
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
        connect();
        ArrayList<CommunityDto> lists = new ArrayList<>();
        String sql = "select * "
                + "from ( "
                + "    select row_number() over(order by board_id desc) as rnum, "
                + "           board_id, user_num, category, title, content, "
                + "           view_count, like_count, created_at, updated_at "
                + "    from community_board "
                + ") "
                + "where rnum between ? and ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, start);
            pstmt.setInt(2, end);
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