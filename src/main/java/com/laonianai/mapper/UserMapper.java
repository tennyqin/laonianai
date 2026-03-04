package com.laonianai.mapper;

import com.laonianai.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {
    // 根据用户名查询用户
    User findByUsername(String username);
    // 添加用户
    int add(User user);
    // 修改密码
    int updatePassword(User user);
}