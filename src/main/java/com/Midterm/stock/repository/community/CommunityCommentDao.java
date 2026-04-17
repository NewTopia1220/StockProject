package com.Midterm.stock.repository.community;

import com.Midterm.stock.dto.CommunityCommentDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;

@Repository
public class CommunityCommentDao {

    @Autowired
    private DataSource dataSource;

    private Connection conn = null;
    private PreparedStatement pstmt = null;
    private ResultSet rs = null;

    public CommunityCommentDao() {
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

    public ArrayList<CommunityCommentDto> getCommentsByBoardId(int board_id) {
        connect();
        ArrayList<CommunityCommentDto> comments = new ArrayList<>();
        String sql = "select cc.comment_id, cc.board_id, cc.user_num, "
                + "u.name as user_name, u.email as user_email, "
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
                dto.setUserEmail(rs.getString("user_email"));
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

    public CommunityCommentDto getComment(int comment_id) {
        connect();
        CommunityCommentDto dto = null;

        String sql = "select cc.comment_id, cc.board_id, cc.user_num, "
                + "u.name as user_name, u.email as user_email, "
                + "cc.content, cc.created_at, cc.updated_at "
                + "from community_comment cc "
                + "join users u on cc.user_num = u.num "
                + "where cc.comment_id = ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, comment_id);
            rs = pstmt.executeQuery();

            if (rs.next()) {
                dto = new CommunityCommentDto();
                dto.setComment_id(rs.getInt("comment_id"));
                dto.setBoard_id(rs.getInt("board_id"));
                dto.setUser_num(rs.getInt("user_num"));
                dto.setUserName(rs.getString("user_name"));
                dto.setUserEmail(rs.getString("user_email"));
                dto.setContent(rs.getString("content"));
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

    public ArrayList<CommunityCommentDto> getCommentsByUserNum(int user_num) {
        connect();
        ArrayList<CommunityCommentDto> comments = new ArrayList<>();

        String sql = "select cc.comment_id, cc.board_id, cc.user_num, "
                + "u.name as user_name, u.email as user_email, "
                + "cb.title as board_title, cb.user_num as board_writer_user_num, "
                + "cc.content, cc.created_at, cc.updated_at "
                + "from community_comment cc "
                + "join users u on cc.user_num = u.num "
                + "join community_board cb on cc.board_id = cb.board_id "
                + "where cc.user_num = ? "
                + "order by cc.created_at desc";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, user_num);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                CommunityCommentDto dto = new CommunityCommentDto();
                dto.setComment_id(rs.getInt("comment_id"));
                dto.setBoard_id(rs.getInt("board_id"));
                dto.setUser_num(rs.getInt("user_num"));
                dto.setUserName(rs.getString("user_name"));
                dto.setUserEmail(rs.getString("user_email"));
                dto.setBoardTitle(rs.getString("board_title"));
                dto.setBoardWriterUserNum(rs.getInt("board_writer_user_num"));
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

    public ArrayList<CommunityCommentDto> getCommentsOnUserBoards(int boardOwnerUserNum) {
        connect();
        ArrayList<CommunityCommentDto> comments = new ArrayList<>();

        String sql = "select cc.comment_id, cc.board_id, cc.user_num, "
                + "u.name as user_name, u.email as user_email, "
                + "cb.title as board_title, cb.user_num as board_writer_user_num, "
                + "cc.content, cc.created_at, cc.updated_at "
                + "from community_comment cc "
                + "join users u on cc.user_num = u.num "
                + "join community_board cb on cc.board_id = cb.board_id "
                + "where cb.user_num = ? "
                + "and cc.user_num <> ? "
                + "order by cc.created_at desc";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, boardOwnerUserNum);
            pstmt.setInt(2, boardOwnerUserNum);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                CommunityCommentDto dto = new CommunityCommentDto();
                dto.setComment_id(rs.getInt("comment_id"));
                dto.setBoard_id(rs.getInt("board_id"));
                dto.setUser_num(rs.getInt("user_num"));
                dto.setUserName(rs.getString("user_name"));
                dto.setUserEmail(rs.getString("user_email"));
                dto.setBoardTitle(rs.getString("board_title"));
                dto.setBoardWriterUserNum(rs.getInt("board_writer_user_num"));
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

    public ArrayList<CommunityCommentDto> getCommentsByUserNumAndCategory(int user_num, String category) {
        connect();
        ArrayList<CommunityCommentDto> comments = new ArrayList<>();

        String sql = "select cc.comment_id, cc.board_id, cc.user_num, "
                + "u.name as user_name, u.email as user_email, "
                + "cb.title as board_title, cb.user_num as board_writer_user_num, "
                + "cc.content, cc.created_at, cc.updated_at "
                + "from community_comment cc "
                + "join users u on cc.user_num = u.num "
                + "join community_board cb on cc.board_id = cb.board_id "
                + "where cc.user_num = ? and cb.category = ? "
                + "order by cc.created_at desc";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, user_num);
            pstmt.setString(2, category);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                CommunityCommentDto dto = new CommunityCommentDto();
                dto.setComment_id(rs.getInt("comment_id"));
                dto.setBoard_id(rs.getInt("board_id"));
                dto.setUser_num(rs.getInt("user_num"));
                dto.setUserName(rs.getString("user_name"));
                dto.setUserEmail(rs.getString("user_email"));
                dto.setBoardTitle(rs.getString("board_title"));
                dto.setBoardWriterUserNum(rs.getInt("board_writer_user_num"));
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

    public ArrayList<CommunityCommentDto> getCommentsOnUserBoardsByCategory(int boardOwnerUserNum, String category) {
        connect();
        ArrayList<CommunityCommentDto> comments = new ArrayList<>();

        String sql = "select cc.comment_id, cc.board_id, cc.user_num, "
                + "u.name as user_name, u.email as user_email, "
                + "cb.title as board_title, cb.user_num as board_writer_user_num, "
                + "cc.content, cc.created_at, cc.updated_at "
                + "from community_comment cc "
                + "join users u on cc.user_num = u.num "
                + "join community_board cb on cc.board_id = cb.board_id "
                + "where cb.user_num = ? "
                + "and cc.user_num <> ? "
                + "and cb.category = ? "
                + "order by cc.created_at desc";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, boardOwnerUserNum);
            pstmt.setInt(2, boardOwnerUserNum);
            pstmt.setString(3, category);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                CommunityCommentDto dto = new CommunityCommentDto();
                dto.setComment_id(rs.getInt("comment_id"));
                dto.setBoard_id(rs.getInt("board_id"));
                dto.setUser_num(rs.getInt("user_num"));
                dto.setUserName(rs.getString("user_name"));
                dto.setUserEmail(rs.getString("user_email"));
                dto.setBoardTitle(rs.getString("board_title"));
                dto.setBoardWriterUserNum(rs.getInt("board_writer_user_num"));
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

    public int getReceivedCommentCountByBoardOwner(int boardOwnerUserNum) {
        connect();
        int count = 0;

        String sql = "select count(*) "
                + "from community_comment cc "
                + "join community_board cb on cc.board_id = cb.board_id "
                + "where cb.user_num = ? "
                + "and cc.user_num <> ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, boardOwnerUserNum);
            pstmt.setInt(2, boardOwnerUserNum);
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

    public int updateComment(int comment_id, String content) {
        connect();
        int count = -1;

        String sql = "update community_comment "
                + "set content = ?, updated_at = sysdate "
                + "where comment_id = ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, content);
            pstmt.setInt(2, comment_id);
            count = pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return count;
    }

    public int deleteComment(int comment_id) {
        connect();
        int count = -1;

        String sql = "delete from community_comment where comment_id = ?";

        try {
            pstmt = conn.prepareStatement(sql);
            pstmt.setInt(1, comment_id);
            count = pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeAll();
        }

        return count;
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
