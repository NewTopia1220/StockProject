package com.Midterm.stock.repository;

import com.Midterm.stock.dto.UserDto;
import org.springframework.stereotype.Repository;

import java.sql.*;

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
        String sql = "insert into users (num, name, email, password, role, phone) "
                + "values (user_seq.nextval, ?, ?, ?, ?, ?)";

        try {
            conn = connect();
            pstmt = conn.prepareStatement(sql);

            pstmt.setString(1, dto.getName());
            pstmt.setString(2, dto.getEmail());
            pstmt.setString(3, dto.getPassword());
            pstmt.setString(4, dto.getRole());
            pstmt.setString(5, dto.getPhone());

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

    // 이메일 대소문자만 다른 계정이 있는지 확인
    public boolean existsWithDifferentEmailCase(String email, String password) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        boolean result = false;

        String sql = "select email from users where lower(email) = lower(?) and password = ?";

        try {
            conn = connect();
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, email);
            pstmt.setString(2, password);
            rs = pstmt.executeQuery();

            while (rs.next()) {
                String dbEmail = rs.getString("email");

                if (dbEmail != null && !dbEmail.equals(email)) {
                    result = true;
                    break;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeResources(rs, pstmt, conn);
        }

        return result;
    }

    // 이름 + 번호로 이메일 찾기
    public String findEmailByNameAndPhone(String name, String phone) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        String email = null;

        String sql = "select email from users where name = ? and phone = ?";

        try {
            conn = connect();
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, name);
            pstmt.setString(2, phone);
            rs = pstmt.executeQuery();

            if (rs.next()){
                email = rs.getString("email");
            }
        }
        catch (SQLException e) {
            e.printStackTrace();
        } finally {
            closeResources(rs, pstmt, conn);
        }

        return email;
    }

    // 이름 + 이메일 + 전화번호 확인 후 비밀번호 바로 변경
    public int resetPasswordByUserInfo(String name, String email, String phone, String newPassword) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        int cnt = -1;

        String sql = "update users "
                + "set password = ? "
                + "where trim(name) = trim(?) "
                + "and lower(trim(email)) = lower(trim(?)) "
                + "and regexp_replace(phone, '[^0-9]', '') = ?";

        try {
            conn = connect();
            pstmt = conn.prepareStatement(sql);
            pstmt.setQueryTimeout(5);
            pstmt.setString(1, newPassword);
            pstmt.setString(2, name);
            pstmt.setString(3, email);
            pstmt.setString(4, phone);

            System.out.println("resetPasswordByUserInfo executeUpdate start");
            cnt = pstmt.executeUpdate();
            System.out.println("resetPasswordByUserInfo executeUpdate end");
            System.out.println("비밀번호 변경 결과: " + cnt);

        } catch (SQLTimeoutException e) {
            System.err.println("비밀번호 변경 쿼리 시간 초과");
            e.printStackTrace();
            cnt = 0;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeResources(null, pstmt, conn);
        }
        return cnt;
    }

    // 저장 이메일 토큰 INSERT
    public int insertSavedEmailToken(String token, String email) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        int cnt = -1;

        // INTERVAL '7' DAY = 현재 시각에서 7일 뒤(7일 동안만 유효)
        String sql = "insert into saved_email_token (token, email, expires_at) "
                + "values (?, ?, SYSTIMESTAMP + INTERVAL '7' DAY)";

        try {
            conn = connect();
            pstmt = conn.prepareStatement(sql);

            pstmt.setString(1, token);
            pstmt.setString(2, email);

            cnt = pstmt.executeUpdate();
            System.out.println("저장 이메일 토큰 등록 완료: " + cnt);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeResources(null, pstmt, conn);
        }

        return cnt;
    }

    // 토큰으로 저장 이메일 조회
    public String getSavedEmailByToken(String token) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;
        String savedEmail = null;

        // expires_at > SYSTIMESTAMP = 만료되지 않은 토큰만 유효
        String sql = "select email from saved_email_token "
                + "where token = ? and expires_at > SYSTIMESTAMP";

        try {
            conn = connect();
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, token);
            rs = pstmt.executeQuery();

            if (rs.next()) {
                savedEmail = rs.getString("email");
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeResources(rs, pstmt, conn);
        }

        return savedEmail;
    }

    // 토큰 삭제
    public int deleteSavedEmailToken(String token) {
        Connection conn = null;
        PreparedStatement pstmt = null;
        int cnt = -1;

        String sql = "delete from saved_email_token where token = ?";

        try {
            conn = connect();
            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, token);

            cnt = pstmt.executeUpdate();
            System.out.println("저장 이메일 토큰 삭제 완료: " + cnt);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeResources(null, pstmt, conn);
        }

        return cnt;
    }

    // UserDao.java에 아래 메서드를 새로 '추가'해
    public UserDto getUserInfoByEmail(String email) {
        UserDto dto = null;
        String sql = "SELECT * FROM users WHERE email = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, email);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    dto = new UserDto();
                    dto.setNum(rs.getInt("num")); // 여기서 num을 가져오는 게 핵심이야
                    dto.setName(rs.getString("name"));
                    dto.setEmail(rs.getString("email"));
                    dto.setPhone(rs.getString("phone"));
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return dto;
    }

    // 마이 페이지 때문에 추가 - user정보 가져오기
    public UserDto getUserInfo(int num) {
        UserDto dto = null;
        String sql = "SELECT * FROM users WHERE num = ?";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, num);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    dto = new UserDto();
                    dto.setNum(rs.getInt("num"));
                    dto.setName(rs.getString("name"));
                    dto.setEmail(rs.getString("email"));
                    dto.setRole(rs.getString("role"));
                    dto.setPhone(rs.getString("phone"));
                    dto.setNotifyStock(rs.getInt("notify_stock"));
                    // 비밀번호는 보안상 보통 마이페이지 조회시엔 잘 안 담지만 필요시 추가
                }
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return dto;
    }


    // 이름 변경때문에 추가
    // 이름 업데이트
    public int updateName(int num, String newName) {
        String sql = "UPDATE users SET name = ? WHERE num = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newName);
            pstmt.setInt(2, num);
            return pstmt.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    // 비밀번호 변경때문에 추가
    public int updatePassword(int num, String newPassword) {
        String sql = "UPDATE users SET password = ? WHERE num = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newPassword);
            pstmt.setInt(2, num);
            return pstmt.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    // 이메일 변경때문에 추가
    // phone 업데이트 메서드 추가
    public int updatePhone(int num, String newPhone) {
        String sql = "UPDATE users SET phone = ? WHERE num = ?";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, newPhone);
            pstmt.setInt(2, num);

            return pstmt.executeUpdate();

        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    // 마이페이지 - 회원탈퇴
    // 회원 탈퇴 (UserDao.java)
    public int deleteUser(int num) {
        // ⭐️ 주의: 만약 다른 테이블이 이 유저의 이메일을 참조하고 있다면
        // 해당 데이터들도 같이 지워지거나 처리가 되어 있어야 에러가 안 납니다.
        String sql = "DELETE FROM users WHERE num = ?";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, num);
            int result = pstmt.executeUpdate();
            System.out.println("회원 탈퇴 완료: " + num);
            return result;
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    // 자원 해제용 공통 메서드
    private void closeResources(ResultSet rs, PreparedStatement pstmt, Connection conn) {
        try {
            if (rs != null) rs.close();
            if (pstmt != null) pstmt.close();
            if (conn != null) conn.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    // ---------------------------------- 민경추가-----------------
    public int findNotifyStockStatusByNum(int userNum) {
        String sql = "SELECT notify_stock FROM users WHERE num = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, userNum);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) return rs.getInt("notify_stock");
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return 1; // 에러 나거나 데이터 없으면 기본값으로 알림 켬(1) 반환
    }

    public void updateNotifySetting(int userNum, int status) {
        String sql = "UPDATE users SET notify_stock = ? WHERE num = ?";
        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, status);
            pstmt.setInt(2, userNum);
            pstmt.executeUpdate();
            System.out.println("유저 " + userNum + " 알림 설정 변경 -> " + status);
        } catch (SQLException e) { e.printStackTrace(); }
    }
}
