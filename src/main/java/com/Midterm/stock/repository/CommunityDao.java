package com.Midterm.stock.repository;

import com.Midterm.stock.dto.CommunityCommentDto;
import com.Midterm.stock.dto.CommunityDto;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Repository
public class CommunityDao {
    // 1. 오라클 클라우드 접속 정보 (경로는 반드시 슬래시 / 사용)
    private String driver = "oracle.jdbc.OracleDriver";
    private String url = "jdbc:oracle:thin:@stoxle_high?TNS_ADMIN=C:/oraclepw";
    private String id = "ADMIN";
    private String pw = "Heeyoun1220!";

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
        String sql = "select * from ( "
                + " select row_number() over(order by c.board_id desc) as rnum, "
                + " c.board_id, c.user_num, u.name as user_name, "
                + " c.category, c.title, c.news_link, c.content, c.view_count, c.like_count, c.created_at, c.updated_at "
                + " from community_board c "
                + " join users u on c.user_num = u.num "
                + ") where rnum between ? and ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, start);
            pstmt.setInt(2, end);
            rs = pstmt.executeQuery();

            while (rs.next()){
                CommunityDto dto = new CommunityDto();

                dto.setBoard_id(rs.getInt("board_id"));
                dto.setUser_num(rs.getInt("user_num"));
                dto.setUserName(rs.getString("user_name"));
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

    // 카테고리별
    public ArrayList<CommunityDto> getArticlesByCategory(String category, int start, int end) {
        connect();

        ArrayList<CommunityDto> lists = new ArrayList<>();
        String sql = "select * from ( "
                + " select row_number() over(order by c.board_id desc) as rnum, "
                + " c.board_id, c.user_num, u.name as user_name, "
                + " c.category, c.title, c.content, c.view_count, c.like_count, c.created_at, c.updated_at "
                + " from community_board c "
                + " join users u on c.user_num = u.num "
                + " where c.category = ? "
                + ") where rnum between ? and ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, category);
            pstmt.setInt(2, start);
            pstmt.setInt(3, end);
            rs = pstmt.executeQuery();

            while (rs.next()){
                CommunityDto dto = new CommunityDto();

                dto.setBoard_id(rs.getInt("board_id"));
                dto.setUser_num(rs.getInt("user_num"));
                dto.setUserName(rs.getString("user_name"));
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

    // 검색용
    public ArrayList<CommunityDto> searchArticles(String keyword, int start, int end){
        connect();
        ArrayList<CommunityDto> lists = new ArrayList<>();

        String sql = "select * from ( "
                + " select row_number() over(order by c.board_id desc) as rnum, "
                + " c.board_id, c.user_num, u.name as user_name, "
                + " c.category, c.title, c.content, c.view_count, c.like_count, c.created_at, c.updated_at "
                + " from community_board c "
                + " join users u on c.user_num = u.num "
                + " where c.title like ? or c.content like ? "
                + ") where rnum between ? and ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, "%" + keyword + "%");
            pstmt.setString(2, "%" + keyword + "%");
            pstmt.setInt(3, start);
            pstmt.setInt(4, end);

            rs = pstmt.executeQuery();

            while (rs.next()){
                CommunityDto dto = new CommunityDto();

                dto.setBoard_id(rs.getInt("board_id"));
                dto.setUser_num(rs.getInt("user_num"));
                dto.setUserName(rs.getString("user_name"));
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

    // 카테고리 + 검색
    public ArrayList<CommunityDto> getArticlesByCategoryAndKeyword(String category, String keyword, int start, int end) {
        connect();
        ArrayList<CommunityDto> lists = new ArrayList<>();

        String sql = "select * from ( "
                + " select row_number() over(order by c.board_id desc) as rnum, "
                + " c.board_id, c.user_num, u.name as user_name, "
                + " c.category, c.title, c.content, c.view_count, c.like_count, c.created_at, c.updated_at "
                + " from community_board c "
                + " join users u on c.user_num = u.num "
                + " where c.category = ? and (c.title like ? or c.content like ?) "
                + ") where rnum between ? and ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, category);
            pstmt.setString(2, "%" + keyword + "%");
            pstmt.setString(3, "%" + keyword + "%");
            pstmt.setInt(4, start);
            pstmt.setInt(5, end);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                CommunityDto dto = new CommunityDto();
                dto.setBoard_id(rs.getInt("board_id"));
                dto.setUser_num(rs.getInt("user_num"));
                dto.setUserName(rs.getString("user_name"));
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

    // 전체 게시글 수
    public int getArticleCount() {
        connect();
        int count = 0;
        String sql = "select count(*) from community_board";

        try {
            pstmt = conn.prepareStatement(sql);
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

    // 카테고리병 게시글 수
    public int getArticleCountByCategory(String category) {
        connect();
        int count = 0;
        String sql = "select count(*) from community_board where category = ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, category);
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

    // 검색 결과 게시글 수
    public int getArticleCountByKeyword(String keyword) {
        connect();
        int count = 0;
        String sql = "select count(*) "
                + "from community_board "
                + "where title like ? or content like ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, "%" + keyword + "%");
            pstmt.setString(2, "%" + keyword + "%");
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

    // 카테고리 + 검색 결과 게시글 수
    public int getArticleCountByCategoryAndKeyword(String category, String keyword) {
        connect();
        int count = 0;
        String sql = "select count(*) "
                + "from community_board "
                + "where category = ? and (title like ? or content like ?)";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, category);
            pstmt.setString(2, "%" + keyword + "%");
            pstmt.setString(3, "%" + keyword + "%");
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


    // 글 작성
    public int insertArticle(CommunityDto dto) {
        connect();
        int count = -1;
        String sql = "insert into community_board(board_id, user_num, category, title, content, news_link) "
                + "values(community_board_seq.nextval, ?, ?, ?, ?, ?)";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, dto.getUser_num());
            pstmt.setString(2, dto.getCategory());
            pstmt.setString(3, dto.getTitle());
            pstmt.setString(4, dto.getContent());
            pstmt.setString(5, dto.getNews_link());

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
        String sql = "select c.board_id, c.user_num, u.name as user_name, "
                + "c.category, c.title, c.content, c.news_link, c.view_count, c.like_count, c.created_at, c.updated_at "
                + "from community_board c "
                + "join users u on c.user_num = u.num "
                + "where c.board_id = ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, board_id);
            rs = pstmt.executeQuery();

            if (rs.next()){
                dto = new CommunityDto();

                dto.setBoard_id(rs.getInt("board_id"));
                dto.setUser_num(rs.getInt("user_num"));
                dto.setUserName(rs.getString("user_name"));
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
    public void updateViewcount(int board_id) {
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

    // 좋아요가 눌렸는지
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

    // 좋아요 클릭(추가)
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

    // 좋아요 더블 클릭(취소)
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

    // 증가
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

    // 감소
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

    // 게시글의 현재 돟아요 수 조회
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

    // 특정 게시글의 댓글 목록 조회
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

    // 게시글 수정
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

    // 게시글 삭제
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

    // 게시글 => detail.html => 사용자의 글 수 & 댓글 수
    public int getArticleCountByUserNum(int user_num) {
        connect();
        int count = 0;

        String sql = "select count(*) from community_board where user_num = ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, user_num);
            rs = pstmt.executeQuery();

            if (rs.next()){
                count = rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return count;
    }

    public int getCommentCountByUserNum(int user_num) {
        connect();
        int count = 0;

        String sql = "select count(*) from community_comment where user_num = ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, user_num);
            rs = pstmt.executeQuery();

            if (rs.next()){
                count = rs.getInt(1);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return count;
    }

    // detail.html -> 뉴스 링크 => 뉴스 제목 조회
    public String getNewsTitleByLink(String newsLink) {
        connect();
        String title = null;

        String sql = "select title from news_data where link = ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, newsLink);
            rs = pstmt.executeQuery();

            if (rs.next()) {
                title = rs.getString("title");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return title;
    }

    public ArrayList<Map<String, String>> searchRelatedNews(String keyword) {
        connect();
        ArrayList<Map<String, String>> newsList = new ArrayList<>();

        String sql = "select * from ( "
                + " select link, title, summary, pub_date "
                + " from news_data "
                + " where title like ? or summary like ? "
                + " order by pub_date desc "
                + ") where rownum <= 5";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, "%" + keyword + "%");
            pstmt.setString(2, "%" + keyword + "%");
            rs = pstmt.executeQuery();

            while (rs.next()) {
                Map<String, String> item = new HashMap<>();
                item.put("link", rs.getString("link"));
                item.put("title", rs.getString("title"));
                item.put("summary", rs.getString("summary"));
                item.put("pubDate", rs.getString("pub_date"));
                newsList.add(item);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return newsList;
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
