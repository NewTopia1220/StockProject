package com.Midterm.stock.controller;

import com.Midterm.stock.dto.UserDto;
import com.Midterm.stock.repository.UserDao;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// 로그인/회원가입 처리
@Controller
public class UserController {

    @Autowired
    private UserDao userDao;

    // 프로그램 공통 상수
    private static final String REMEMBER_EMAIL_COOKIE = "REMEMBER_EMAIL_TOKEN";
    private static final int REMEMBER_EMAIL_COOKIE_AGE = 60 * 60 * 24 * 7; // 7일

    // 로그인 처리
    @PostMapping("/login")
    public String login(
            @RequestParam(value = "userEmail", required = false) String email,
            @RequestParam(value = "userPassword", required = false) String pw,
            @RequestParam(value = "saveId", required = false) String saveId,
            HttpSession session,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        if (email == null || pw == null) {
            return "redirect:/login?error=1";
        }

        email = email.trim();
        pw = pw.trim();

        if (email.equals("") || pw.equals("")) {
            return "redirect:/login?error=1";
        }

        boolean loginResult = userDao.loginCheck(email, pw);

        if (loginResult) {
            session.setAttribute("loginUser", email);

            // 이전에 저장된 토큰O -> 정리
            String oldToken = getCookieValue(request, REMEMBER_EMAIL_COOKIE);
            if (oldToken != null && !oldToken.trim().equals("")) {
                userDao.deleteSavedEmailToken(oldToken);
            }

            // 이메일 저장 체크 여부
            if (saveId != null) {
                // UUID = 거의 고유한 랜덤한 문자열
                String newToken = UUID.randomUUID().toString();
                int saveResult = userDao.insertSavedEmailToken(newToken, email);

                if (saveResult > 0) { // 저장 성공 시
                    Cookie cookie = new Cookie(REMEMBER_EMAIL_COOKIE, newToken);
                    cookie.setPath("/"); // 사이트 전체 허용
                    cookie.setHttpOnly(true); // 보안
                    cookie.setMaxAge(REMEMBER_EMAIL_COOKIE_AGE);
                    response.addCookie(cookie);
                }
            } else {
                removeSavedEmailCookie(request, response);
            }
            return "redirect:/stock";

        } else {
            return "redirect:/login?error=1";
        }
    }

    // 회원가입 처리
    @PostMapping("/register")
    public String register(
            @RequestParam(value = "userName", required = false) String name,
            @RequestParam(value = "userEmail", required = false) String email,
            @RequestParam(value = "userPassword", required = false) String pw,
            @RequestParam(value = "reUserPassword", required = false) String repw
    ) {
        if (name == null || email == null || pw == null || repw == null) {
            return "redirect:/register?error=1";
        }

        name = name.trim();
        email = email.trim();
        pw = pw.trim();
        repw = repw.trim();

        if (name.equals("") || email.equals("") || pw.equals("") || repw.equals("")) {
            return "redirect:/register?error=1";
        }

        if (pw.length() < 5 || pw.length() > 8) {
            return "redirect:/register?error=1";
        }

        if (repw.length() < 5 || repw.length() > 8) {
            return "redirect:/register?error=1";
        }

        try {
            Integer.parseInt(pw);
            Integer.parseInt(repw);
        } catch (Exception e) {
            return "redirect:/register?error=1";
        }

        if (!pw.equals(repw)) {
            return "redirect:/register?error=1";
        }

        UserDto dto = new UserDto();
        dto.setName(name);
        dto.setEmail(email);
        dto.setPassword(pw);
        dto.setRole("user");

        int result = userDao.insertUser(dto);

        if (result > 0) {
            return "redirect:/login?register=1";
        } else {
            return "redirect:/register?error=1";
        }
    }

    // 저장된 이메일 조회
    @GetMapping("/login/saved-email")
    @ResponseBody
    public Map<String, String> getSavedEmail(HttpServletRequest request) {
        Map<String, String> result = new HashMap<>();

        String token = getCookieValue(request, REMEMBER_EMAIL_COOKIE);
        String savedEmail = "";

        if (token != null && !token.trim().equals("")){
            String dbEmail = userDao.getSavedEmailByToken(token);
            if (dbEmail != null){
                savedEmail = dbEmail;
            }
        }

        result.put("savedEmail", savedEmail);
        return result;
    }

    // 저장된 이메일 삭제
    @DeleteMapping("/login/saved-email")
    @ResponseBody
    public Map<String, String> deleteSavedEmail(HttpServletRequest request, HttpServletResponse response) {
        removeSavedEmailCookie(request, response);

        Map<String, String> result = new HashMap<>();
        result.put("message", "saved email deleted");
        return result;
    }

    // 이메일 찾기
    @PostMapping("/findEmail")
    public String findEmail(@RequestParam(value = "userName", required = false) String name,
                            @RequestParam(value = "userPhone", required = false) String phone,
                            Model model) {
        if (name == null || phone == null) {
            model.addAttribute("errorMessage", "이름과 전화번호를 입력해주세요");
            return "findEmail";
        }
        name = name.trim();
        phone = phone.trim();

        if (name.equals("") || phone.equals("")) {
            model.addAttribute("errorMessage", "이름과 전화번호를 입력해주세요");
            return "findEmail";
        }

        // 이메일을 찾기 위해 이름과 번호를 넘김
        String email = userDao.findEmailByNameAndPhone(name, phone);

        if (email == null) {
            model.addAttribute("errorMessage", "일치하는 회원 정보가 없습니다");
        } else {
            model.addAttribute("maskedEmail", maskEmail(email));
        }

        return "findEmail";
    }

    // 비밀번호 찾기   // 본인 확인 후 바로 새 비밀번호 입력
    @PostMapping("/findPassword")
    public String findPassword(@RequestParam(value = "userName", required = false) String name,
                               @RequestParam(value = "userEmail", required = false) String email,
                               @RequestParam(value = "userPhone", required = false) String phone,
                               @RequestParam(value = "newPassword", required = false) String newPassword,
                               @RequestParam(value = "reNewPassword", required = false) String reNewPassword,
                               Model model) {
        if (name == null || email == null || phone == null || newPassword == null || reNewPassword == null) {
            model.addAttribute("errorMessage", "모든 값을 입력해주세요");
            return "findPassword";
        }

        name = name.trim();
        email = email.trim();
        phone = phone.trim();
        newPassword = newPassword.trim();
        reNewPassword = reNewPassword.trim();

        if (name.equals("") || email.equals("") || phone.equals("") || newPassword.equals("") || reNewPassword.equals("")) {
            model.addAttribute("errorMessage", "모든 값을 입력해주세요");
            return "findPassword";
        }

        if (!newPassword.equals(reNewPassword)) {
            model.addAttribute("errorMessage", "새 비밀번호가 일치하지 않습니다.");
            return "findPassword";
        }

        int result = userDao.resetPasswordByUserInfo(name, email, phone, newPassword);

        if (result > 0) {
            return "redirect:/login?reset=1";
        } else {
            model.addAttribute("errorMessage", "일치하는 회원 정보가 없거나 비밀번호 변경에 실패했습니다.");
            return "findPassword";
        }
    }

    private String maskEmail(String email) {
        if (email == null || email.trim().equals("") || !email.contains("@")) {
            return "";
        }

        String[] parts = email.split("@");
        String local = parts[0];    // ex. ya
        String domain = parts[1];   // ex. naver.com

        if (local.length() == 1) {
            return local + "***@" + domain;
        }

        if (local.length() == 2) {  // y***@naver.com
            return local.substring(0, 1) + "***@" + domain;
        }

        return local.substring(0, 3) + "***@" + domain;
    }


    private void removeSavedEmailCookie(HttpServletRequest request, HttpServletResponse response) {
        String token = getCookieValue(request, REMEMBER_EMAIL_COOKIE);

        if (token != null && !token.trim().equals("")) {
            userDao.deleteSavedEmailToken(token);
        }

        // 삭제용 쿠키 생성
        Cookie cookie = new Cookie(REMEMBER_EMAIL_COOKIE, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);   // 0 = 즉시 삭제
        response.addCookie(cookie);
    }

    // 쿠키 찾는
    private String getCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();

        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue(); // 값만 반환
            }
        }

        return null;  // 못 찾음
    }
}