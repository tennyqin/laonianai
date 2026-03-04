package com.laonianai.service.impl;

import com.laonianai.entity.User;
import com.laonianai.mapper.UserMapper;
import com.laonianai.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;

    @Resource
    private BCryptPasswordEncoder passwordEncoder;

    @Override
    public User findByUsername(String username) {
        return userMapper.findByUsername(username);
    }

    @Override
    public int add(User user) {
        // 密码加密
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userMapper.add(user);
    }

    @Override
    public int updatePassword(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userMapper.updatePassword(user);
    }

    @Override
    public boolean login(String username, String password) {
        User user = findByUsername(username);
        if (user == null) {
            return false;
        }
        // 验证密码
        return passwordEncoder.matches(password, user.getPassword());
    }
}
