package com.Midterm.stock.repository.community;

import com.Midterm.stock.dto.CommunityDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.sql.*;
import java.util.ArrayList;

@Repository
public class CommunityBoardDao {
    private String driver = "oracle.jdbc.OracleDriver";
    private String url = "jdbc:oracle:thin:@stoxle_high?TNS_ADMIN=C:/oraclepw";
    private String id = "ADMIN";
    private String pw = "Heeyoun1220!";

    private Connection conn = null;
    private PreparedStatement pstmt = null;
    private ResultSet rs = null;

    @Autowired
    private CommunityTagDao communityTagDao;

    // 생성자: 드라이버 로딩 및 지갑 설정
    public CommunityBoardDao() {
        System.out.println("CommunityBoardDao 생성자 호출 - 클라우드 설정 시작");
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

    // 전체 게시글 목록 조회
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

            while (rs.next()) {
                CommunityDto dto = new CommunityDto();
                dto.setBoard_id(rs.getInt("board_id"));
                dto.setUser_num(rs.getInt("user_num"));
                dto.setUserName(rs.getString("user_name"));
                dto.setCategory(rs.getString("category"));
                dto.setTitle(rs.getString("title"));
                dto.setNews_link(rs.getString("news_link"));
                dto.setContent(rs.getString("content"));
                dto.setView_count(rs.getInt("view_count"));
                dto.setLike_count(rs.getInt("like_count"));
                dto.setCreated_at(rs.getTimestamp("created_at"));
                dto.setUpdated_at(rs.getTimestamp("updated_at"));
                dto.setTagNames(communityTagDao.getTagNamesByBoardId(rs.getInt("board_id")));
                lists.add(dto);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }
        return lists;
    }

    // 카테고리별 게시글 목록 조회
    public ArrayList<CommunityDto> getArticlesByCategory(String category, int start, int end) {
        connect();

        ArrayList<CommunityDto> lists = new ArrayList<>();
        String sql = "select * from ( "
                + " select row_number() over(order by c.board_id desc) as rnum, "
                + " c.board_id, c.user_num, u.name as user_name, "
                + " c.category, c.title, c.news_link, c.content, c.view_count, c.like_count, c.created_at, c.updated_at "
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

            while (rs.next()) {
                CommunityDto dto = new CommunityDto();
                dto.setBoard_id(rs.getInt("board_id"));
                dto.setUser_num(rs.getInt("user_num"));
                dto.setUserName(rs.getString("user_name"));
                dto.setCategory(rs.getString("category"));
                dto.setTitle(rs.getString("title"));
                dto.setNews_link(rs.getString("news_link"));
                dto.setContent(rs.getString("content"));
                dto.setView_count(rs.getInt("view_count"));
                dto.setLike_count(rs.getInt("like_count"));
                dto.setCreated_at(rs.getTimestamp("created_at"));
                dto.setUpdated_at(rs.getTimestamp("updated_at"));
                dto.setTagNames(communityTagDao.getTagNamesByBoardId(rs.getInt("board_id")));
                lists.add(dto);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }
        return lists;
    }

    // 키워드 검색 목록 조회
    // 제목, 내용, 태그까지 같이 검색
    public ArrayList<CommunityDto> searchArticles(String keyword, int start, int end) {
        connect();
        ArrayList<CommunityDto> lists = new ArrayList<>();

        String sql = "select * from ( "
                + " select row_number() over(order by c.board_id desc) as rnum, "
                + " c.board_id, c.user_num, u.name as user_name, "
                + " c.category, c.title, c.news_link, c.content, c.view_count, c.like_count, c.created_at, c.updated_at "
                + " from community_board c "
                + " join users u on c.user_num = u.num "
                + " where c.title like ? "
                + " or c.content like ? "
                + " or exists ( "
                + "     select 1 "
                + "     from community_board_tag cbt "
                + "     join community_tag ct on cbt.tag_id = ct.tag_id "
                + "     where cbt.board_id = c.board_id "
                + "     and ct.tag_name like ? "
                + " ) "
                + ") where rnum between ? and ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, "%" + keyword + "%");
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
                dto.setNews_link(rs.getString("news_link"));
                dto.setContent(rs.getString("content"));
                dto.setView_count(rs.getInt("view_count"));
                dto.setLike_count(rs.getInt("like_count"));
                dto.setCreated_at(rs.getTimestamp("created_at"));
                dto.setUpdated_at(rs.getTimestamp("updated_at"));
                dto.setTagNames(communityTagDao.getTagNamesByBoardId(rs.getInt("board_id")));
                lists.add(dto);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }
        return lists;
    }

    // 카테고리 + 키워드 검색 목록 조회
    // 제목, 내용, 태그까지 같이 검색
    public ArrayList<CommunityDto> getArticlesByCategoryAndKeyword(String category, String keyword, int start, int end) {
        connect();
        ArrayList<CommunityDto> lists = new ArrayList<>();

        String sql = "select * from ( "
                + " select row_number() over(order by c.board_id desc) as rnum, "
                + " c.board_id, c.user_num, u.name as user_name, "
                + " c.category, c.title, c.news_link, c.content, c.view_count, c.like_count, c.created_at, c.updated_at "
                + " from community_board c "
                + " join users u on c.user_num = u.num "
                + " where c.category = ? "
                + " and ( "
                + "     c.title like ? "
                + "     or c.content like ? "
                + "     or exists ( "
                + "         select 1 "
                + "         from community_board_tag cbt "
                + "         join community_tag ct on cbt.tag_id = ct.tag_id "
                + "         where cbt.board_id = c.board_id "
                + "         and ct.tag_name like ? "
                + "     ) "
                + " ) "
                + ") where rnum between ? and ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, category);
            pstmt.setString(2, "%" + keyword + "%");
            pstmt.setString(3, "%" + keyword + "%");
            pstmt.setString(4, "%" + keyword + "%");
            pstmt.setInt(5, start);
            pstmt.setInt(6, end);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                CommunityDto dto = new CommunityDto();
                dto.setBoard_id(rs.getInt("board_id"));
                dto.setUser_num(rs.getInt("user_num"));
                dto.setUserName(rs.getString("user_name"));
                dto.setCategory(rs.getString("category"));
                dto.setTitle(rs.getString("title"));
                dto.setNews_link(rs.getString("news_link"));
                dto.setContent(rs.getString("content"));
                dto.setView_count(rs.getInt("view_count"));
                dto.setLike_count(rs.getInt("like_count"));
                dto.setCreated_at(rs.getTimestamp("created_at"));
                dto.setUpdated_at(rs.getTimestamp("updated_at"));
                dto.setTagNames(communityTagDao.getTagNamesByBoardId(rs.getInt("board_id")));
                lists.add(dto);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return lists;
    }

    // 전체 게시글 수 조회
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

    // 전체 인기글 목록 조회
    public ArrayList<CommunityDto> getPopularArticles(int start, int end) {
        return getPopularArticles(null, null, start, end);
    }

    // 인기글 키워드 검색 목록 조회
    public ArrayList<CommunityDto> searchPopularArticles(String keyword, int start, int end) {
        return getPopularArticles(null, keyword, start, end);
    }

    // 전체 인기글 수 조회
    public int getPopularArticleCount() {
        return getPopularArticleCount(null, null);
    }

    // 인기글 키워드 검색 게시글 수 조회
    public int getPopularArticleCountByKeyword(String keyword) {
        return getPopularArticleCount(null, keyword);
    }

    // 인기글 통합 조회
    // 테마 + 제목/내용 + 태그 검색
    public ArrayList<CommunityDto> getPopularArticles(String themeName, String keyword, int start, int end) {
        connect();
        ArrayList<CommunityDto> lists = new ArrayList<>();

        StringBuilder sql = new StringBuilder(
                "select * from ( "
                        + " select row_number() over(order by c.like_count desc, c.created_at desc, c.board_id desc) as rnum, "
                        + " c.board_id, c.user_num, u.name as user_name, "
                        + " c.category, c.title, c.news_link, c.content, c.view_count, c.like_count, c.created_at, c.updated_at "
                        + " from community_board c "
                        + " join users u on c.user_num = u.num "
                        + " where c.like_count > 0 "
        );

        if (themeName != null && !themeName.isBlank()) {
            sql.append(" and c.category = ? ");
        }

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and ( ");
            sql.append(" c.title like ? ");
            sql.append(" or c.content like ? ");
            sql.append(" or exists ( ");
            sql.append("     select 1 ");
            sql.append("     from community_board_tag cbt ");
            sql.append("     join community_tag ct on cbt.tag_id = ct.tag_id ");
            sql.append("     where cbt.board_id = c.board_id ");
            sql.append("     and ct.tag_name like ? ");
            sql.append(" ) ");
            sql.append(" ) ");
        }

        sql.append(") where rnum between ? and ?");

        try {
            pstmt = conn.prepareStatement(sql.toString());

            int idx = 1;

            if (themeName != null && !themeName.isBlank()) {
                pstmt.setString(idx++, themeName);
            }

            if (keyword != null && !keyword.isBlank()) {
                pstmt.setString(idx++, "%" + keyword + "%");
                pstmt.setString(idx++, "%" + keyword + "%");
                pstmt.setString(idx++, "%" + keyword + "%");
            }

            pstmt.setInt(idx++, start);
            pstmt.setInt(idx, end);

            rs = pstmt.executeQuery();

            while (rs.next()) {
                CommunityDto dto = new CommunityDto();
                dto.setBoard_id(rs.getInt("board_id"));
                dto.setUser_num(rs.getInt("user_num"));
                dto.setUserName(rs.getString("user_name"));
                dto.setCategory(rs.getString("category"));
                dto.setTitle(rs.getString("title"));
                dto.setNews_link(rs.getString("news_link"));
                dto.setContent(rs.getString("content"));
                dto.setView_count(rs.getInt("view_count"));
                dto.setLike_count(rs.getInt("like_count"));
                dto.setCreated_at(rs.getTimestamp("created_at"));
                dto.setUpdated_at(rs.getTimestamp("updated_at"));
                dto.setTagNames(communityTagDao.getTagNamesByBoardId(rs.getInt("board_id")));
                lists.add(dto);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return lists;
    }

    // 인기글 수 조회
    // 테마 + 제목/내용 + 태그 검색
    public int getPopularArticleCount(String themeName, String keyword) {
        connect();
        int count = 0;

        StringBuilder sql = new StringBuilder(
                "select count(*) "
                        + "from community_board c "
                        + "where c.like_count > 0 "
        );

        if (themeName != null && !themeName.isBlank()) {
            sql.append(" and c.category = ? ");
        }

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" and ( ");
            sql.append(" c.title like ? ");
            sql.append(" or c.content like ? ");
            sql.append(" or exists ( ");
            sql.append("     select 1 ");
            sql.append("     from community_board_tag cbt ");
            sql.append("     join community_tag ct on cbt.tag_id = ct.tag_id ");
            sql.append("     where cbt.board_id = c.board_id ");
            sql.append("     and ct.tag_name like ? ");
            sql.append(" ) ");
            sql.append(" ) ");
        }

        try {
            pstmt = conn.prepareStatement(sql.toString());

            int idx = 1;

            if (themeName != null && !themeName.isBlank()) {
                pstmt.setString(idx++, themeName);
            }

            if (keyword != null && !keyword.isBlank()) {
                pstmt.setString(idx++, "%" + keyword + "%");
                pstmt.setString(idx++, "%" + keyword + "%");
                pstmt.setString(idx++, "%" + keyword + "%");
            }

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

    // 메인 화면 추천글 조회
    public ArrayList<CommunityDto> getFeaturedArticles(int limit) {
        connect();

        ArrayList<CommunityDto> lists = new ArrayList<>();
        String sql = "select * from ( "
                + " select c.board_id, c.user_num, u.name as user_name, "
                + " c.category, c.title, c.news_link, c.content, c.view_count, c.like_count, c.created_at, c.updated_at "
                + " from community_board c "
                + " join users u on c.user_num = u.num "
                + " where c.like_count > 0 "
                + " order by c.like_count desc, c.created_at desc, c.board_id desc "
                + " ) where rownum <= ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, limit);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                CommunityDto dto = new CommunityDto();
                dto.setBoard_id(rs.getInt("board_id"));
                dto.setUser_num(rs.getInt("user_num"));
                dto.setUserName(rs.getString("user_name"));
                dto.setCategory(rs.getString("category"));
                dto.setTitle(rs.getString("title"));
                dto.setNews_link(rs.getString("news_link"));
                dto.setContent(rs.getString("content"));
                dto.setView_count(rs.getInt("view_count"));
                dto.setLike_count(rs.getInt("like_count"));
                dto.setCreated_at(rs.getTimestamp("created_at"));
                dto.setUpdated_at(rs.getTimestamp("updated_at"));
                dto.setTagNames(communityTagDao.getTagNamesByBoardId(rs.getInt("board_id")));
                lists.add(dto);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return lists;
    }

    // 카테고리별 게시글 수 조회
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

    // 키워드 검색 게시글 수 조회
    // 제목, 내용, 태그까지 같이 검색
    public int getArticleCountByKeyword(String keyword) {
        connect();
        int count = 0;

        String sql = "select count(*) "
                + "from community_board c "
                + "where c.title like ? "
                + "or c.content like ? "
                + "or exists ( "
                + "    select 1 "
                + "    from community_board_tag cbt "
                + "    join community_tag ct on cbt.tag_id = ct.tag_id "
                + "    where cbt.board_id = c.board_id "
                + "    and ct.tag_name like ? "
                + ")";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, "%" + keyword + "%");
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

    // 카테고리 + 키워드 검색 게시글 수 조회
    // 제목, 내용, 태그까지 같이 검색
    public int getArticleCountByCategoryAndKeyword(String category, String keyword) {
        connect();
        int count = 0;

        String sql = "select count(*) "
                + "from community_board c "
                + "where c.category = ? "
                + "and ( "
                + "    c.title like ? "
                + "    or c.content like ? "
                + "    or exists ( "
                + "        select 1 "
                + "        from community_board_tag cbt "
                + "        join community_tag ct on cbt.tag_id = ct.tag_id "
                + "        where cbt.board_id = c.board_id "
                + "        and ct.tag_name like ? "
                + "    ) "
                + ")";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, category);
            pstmt.setString(2, "%" + keyword + "%");
            pstmt.setString(3, "%" + keyword + "%");
            pstmt.setString(4, "%" + keyword + "%");
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

    // 게시글 작성
    // 게시글 저장 후 태그도 같이 저장
    public int insertArticle(CommunityDto dto) {
        connect();
        int count = -1;
        int boardId = 0;

        try {
            String seqSql = "select community_board_seq.nextval from dual";
            pstmt = conn.prepareStatement(seqSql);
            rs = pstmt.executeQuery();

            if (rs.next()) {
                boardId = rs.getInt(1);
            }

            rs.close();
            pstmt.close();

            String sql = "insert into community_board(board_id, user_num, category, title, content, news_link) "
                    + "values(?, ?, ?, ?, ?, ?)";

            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, boardId);
            pstmt.setInt(2, dto.getUser_num());
            pstmt.setString(3, dto.getCategory());
            pstmt.setString(4, dto.getTitle());
            pstmt.setString(5, dto.getContent());
            pstmt.setString(6, dto.getNews_link());

            count = pstmt.executeUpdate();

            if (count > 0) {
                communityTagDao.saveTags(boardId, dto.getTagNames());
            }

        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }
        return count;
    }

    // 게시글 상세 조회
    // 태그 문자열도 같이 세팅
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

            if (rs.next()) {
                dto = new CommunityDto();
                dto.setBoard_id(rs.getInt("board_id"));
                dto.setUser_num(rs.getInt("user_num"));
                dto.setUserName(rs.getString("user_name"));
                dto.setCategory(rs.getString("category"));
                dto.setTitle(rs.getString("title"));
                dto.setNews_link(rs.getString("news_link"));
                dto.setContent(rs.getString("content"));
                dto.setView_count(rs.getInt("view_count"));
                dto.setLike_count(rs.getInt("like_count"));
                dto.setCreated_at(rs.getTimestamp("created_at"));
                dto.setUpdated_at(rs.getTimestamp("updated_at"));
                dto.setTagNames(communityTagDao.getTagNamesByBoardId(board_id));
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
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }
    }

    // 게시글 수정
    // 수정 후 태그를 다시 저장
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

            if (count > 0) {
                communityTagDao.deleteBoardTags(dto.getBoard_id());
                communityTagDao.saveTags(dto.getBoard_id(), dto.getTagNames());
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }
        return count;
    }

    // 게시글 삭제
    // 삭제 전에 태그 매핑도 같이 삭제
    public int deleteArticle(int board_id) {
        connect();
        int count = -1;

        try {
            communityTagDao.deleteBoardTags(board_id);

            String sql = "delete from community_board where board_id = ?";
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

    // 특정 사용자의 게시글 수 조회
    public int getArticleCountByUserNum(int user_num) {
        connect();
        int count = 0;
        String sql = "select count(*) from community_board where user_num = ?";

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

