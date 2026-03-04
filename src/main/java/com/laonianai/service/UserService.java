package com.laonianai.service;

import com.laonianai.entity.User;

public interface UserService {
    User findByUsername(String username);
    int add(User user);
    int updatePassword(User user);
    // 登录验证
    boolean login(String username, String password);
}
