package com.Midterm.stock.controller;

import com.Midterm.stock.dto.UserDto;
import com.Midterm.stock.repository.UserDao;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

// 로그인/회원가입 처리
@Controller
public class UserController {

    @Autowired
    private UserDao userDao;

    // 로그인 처리
    @PostMapping("/login")
    public String login(
            @RequestParam(value = "userEmail", required = false) String email,
            @RequestParam(value = "userPassword", required = false) String pw,
            HttpSession session
    ) {
        if (email == null || pw == null) {
            return "redirect:/login?error=1";
//            session.setAttribute("loginNum", loginUserDto.getNum()); // ⭐️ 핵심! 이 번호가 마이페이지의 열쇠입니다.
        }

        email = email.trim();
        pw = pw.trim();

        if (email.equals("") || pw.equals("")) {
            return "redirect:/login?error=1";
        }

        boolean loginResult = userDao.loginCheck(email, pw);

        if (loginResult) {
            UserDto loginUserDto = userDao.getUserInfoByEmail(email);  // 추가
            session.setAttribute("loginNum", loginUserDto.getNum());   // 추가
//            session.setAttribute("loginUser", userEmail);
//            session.setAttribute("loginUser", email);
            return "redirect:/asset/dashboard";   //return "redirect:/stock";
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
            @RequestParam(value = "reUserPassword", required = false) String repw,
            @RequestParam(value = "userPhone", required = false) String phone
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
        dto.setPhone(phone);

        int result = userDao.insertUser(dto);

        if (result > 0) {
            return "redirect:/login?register=1";
        } else {
            return "redirect:/register?error=1";
        }
    }




    // 마이페이지에서 정보 가져오기
    @GetMapping("/mypage")
    public String myPage(HttpSession session, Model model) {
        // 1. 세션에서 로그인한 유저 번호(이메일) 확인
        // String loginEmail = (String) session.getAttribute("loginUser");
        Integer loginNum = (Integer) session.getAttribute("loginNum");

        // 로그인이 안 되어 있으면 로그인 페이지로 튕겨내기 (보안)
        if (loginNum == null) {
            return "redirect:/login";
        }

        // 2. 실제 DB에서 유저 정보 가져오기
        UserDto user = userDao.getUserInfo(loginNum);
        // 만약 유저 정보가 없으면 예외 처리
        if (user == null) {
            return "redirect:/login";
        }

        model.addAttribute("user", user);
        model.addAttribute("currentPage", "mypage"); // 헤더 활성화용

//        // 3. 기존 설정에 있던 계좌 정보 등 추가 (하드코딩 데이터나 DAO 호출)
//        List<Map<String, Object>> accounts = new ArrayList<>();
//        // ... (기존 계좌 리스트 로직) ...
//        model.addAttribute("accounts", accounts);

//        return "asset/settings";
        return "mypage";
    }



    // 마이페이지 이름, 비번 변경
    @PostMapping("/user/update-profile")
    @ResponseBody
    public String updateProfile(@RequestParam int num, @RequestParam String type, @RequestParam String value, HttpSession session) {
        // 보안 체크: 세션의 유저와 수정하려는 유저가 같은지 확인하면 더 좋습니다.
        Integer loginNum = (Integer) session.getAttribute("loginNum");
        if (loginNum == null || loginNum != num) return "error";

        if ("name".equals(type)) {
            userDao.updateName(num, value);
            return "success";

         } else if ("phone".equals(type)) {
            // 간단 검증 (숫자만)
            if (!value.matches("\\d{10,11}")) {
                return "invalid";
            }
            userDao.updatePhone(num, value);
            return "success";

        } else if ("password".equals(type)) {
            // 비밀번호 변경 로직
            if (value.length() < 4) return "too_short"; // 간단한 유효성 검사
            userDao.updatePassword(num, value);
            return "success";
        }
        return "fail";
    }



    // 마이페이지 - 회원탈퇴
    // UserController.java
    @PostMapping("/user/withdraw")
    public String withdraw(HttpSession session) {
        Integer loginNum = (Integer) session.getAttribute("loginNum");

        // 세션 체크
        if (loginNum == null) {
            return "redirect:/login";
        }

        // DB 삭제 및 세션 제거
        userDao.deleteUser(loginNum);
        session.invalidate();


        // 3. 메인 화면이나 로그인 화면으로 이동
        return "redirect:/login?withdraw=1";
    }



    // UserController.java
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        // 1. 현재 세션을 완전히 무효화 (안의 모든 데이터 삭제)
        session.invalidate();

        // 2. 로그아웃 후 로그인 페이지로 이동 (알림용 파라미터 추가 가능)
        return "redirect:/login?logout=1";
    }



}


