package com.Midterm.stock.controller;

import com.Midterm.stock.dto.UserDto;
import com.Midterm.stock.repository.UserDao;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
        }

        email = email.trim();
        pw = pw.trim();

        if (email.equals("") || pw.equals("")) {
            return "redirect:/login?error=1";
        }

        boolean loginResult = userDao.loginCheck(email, pw);

        if (loginResult) {
            session.setAttribute("loginUser", email);
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
}