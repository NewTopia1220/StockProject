package com.Midterm.stock.repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.stereotype.Repository;
import com.Midterm.stock.dto.UserDto;

@Repository
public class UserDao {

    // 1. 클라우드 접속 정보로 변경 (경로 슬래시 / 주의!)
    private String driver = "oracle.jdbc.OracleDriver";
    private String url = "jdbc:oracle:thin:@stoxle_high?TNS_ADMIN=C:/oraclepw";
    private String id = "ADMIN";
    private String pw = "Heeyoun1220!";

    // 드라이버 연결 및 지갑 설정
    public UserDao() {
        System.out.println("UserDao 생성자 - 클라우드 설정 로드");
        try {
            Class.forName(driver);
            // ⭐️ 핵심: JDBC가 지갑 파일을 찾을 수 있도록 시스템 속성 설정
            System.setProperty("oracle.net.wallet_location", "(SOURCE=(METHOD=FILE)(METHOD_DATA=(DIRECTORY=C:/oraclepw)))");
            System.out.println("UserDao: 드라이버 로드 및 지갑 경로 설정 성공");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    // 계정 접속
    public Connection connect() {
        Connection conn = null;
        try {
            // 위에서 설정한 클라우드 url, id, pw로 접속합니다.
            conn = DriverManager.getConnection(url, id, pw);
            System.out.println("UserDao: 오라클 클라우드 접속 성공!");
        } catch (SQLException e) {
            System.err.println("UserDao 접속 실패: " + e.getMessage());
            e.printStackTrace();
        }
        return conn;
    }

    // 회원가입 (INSERT)
    public int insertUser(UserDto dto) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        int cnt = -1;

        // 테이블명과 시퀀스명이 클라우드 DB에 생성되어 있어야 합니다.
        String sql = "insert into users (num, name, email, password, role) "
                + "values (user_seq.nextval, ?, ?, ?, ?)";

        try {
            conn = connect();
            pstmt = conn.prepareStatement(sql);

            pstmt.setString(1, dto.getName());
            pstmt.setString(2, dto.getEmail());
            pstmt.setString(3, dto.getPassword());
            pstmt.setString(4, dto.getRole());

            cnt = pstmt.executeUpdate();
            System.out.println("회원가입 완료: " + cnt);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeResources(null, pstmt, conn);
        }

        return cnt;
    }

    // 로그인 체크
    public boolean loginCheck(String email, String password) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        boolean result = false;

        System.out.println("=== 로그인 시도 [" + email + "] ===");

        String sql = "select * from users where email = ? and password = ?";

        try {
            conn = connect();
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, email);
            pstmt.setString(2, password);
            rs = pstmt.executeQuery();

            if (rs.next()) {
                result = true;
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeResources(rs, pstmt, conn);
        }

        System.out.println("로그인 결과: " + (result ? "성공" : "실패"));
        return result;
    }

    // 자원 해제용 공통 메서드 (코드가 깔끔해집니다)
    private void closeResources(ResultSet rs, PreparedStatement pstmt, Connection conn) {
        try {
            if (rs != null) rs.close();
            if (pstmt != null) pstmt.close();
            if (conn != null) conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}