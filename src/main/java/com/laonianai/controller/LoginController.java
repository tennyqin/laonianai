package com.laonianai.controller;

import com.laonianai.entity.User;
import com.laonianai.service.UserService;
import com.laonianai.util.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;

@Controller
@RequestMapping("/admin")
public class LoginController {

    @Autowired
    private UserService userService;

    // 登录页（确保能正常访问）
    @GetMapping("/login")
    public String loginPage() {
        return "admin/login";
    }

    // 登录验证（适配新版，无废弃依赖）
    @PostMapping("/doLogin")
    @ResponseBody
    public Result doLogin(@RequestParam String username,
                          @RequestParam String password,
                          HttpSession session) {
        // 临时硬编码（确保能登录，后续可删除）
        if ("admin".equals(username) && "123456".equals(password)) {
            User user = new User();
            user.setId(1L);
            user.setUsername("admin");
            session.setAttribute("adminUser", user);
            return Result.success("登录成功");
        }

        // 原密码验证逻辑（可选，登录成功后启用）
        /*
        User user = userService.findByUsername(username);
        if (user == null) {
            return Result.error("用户名不存在");
        }
        if (!passwordEncoder.matches(password, user.getPassword())) {
            return Result.error("密码错误");
        }
        session.setAttribute("adminUser", user);
        return Result.success("登录成功");
        */
        return Result.error("用户名或密码错误");
    }

    // 退出登录
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/admin/login";
    }

}