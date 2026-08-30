package com.chinavisamap.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.web.filter.CharacterEncodingFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // 密码加密器（保留）
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 核心：配置SecurityFilterChain（替代WebSecurityConfigurerAdapter）
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 解决中文乱码
        CharacterEncodingFilter encodingFilter = new CharacterEncodingFilter();
        encodingFilter.setEncoding("UTF-8");
        encodingFilter.setForceEncoding(true);
        http.addFilterBefore(encodingFilter, CsrfFilter.class);

        // 1. 关闭CSRF（开发环境简化）
        http.csrf().disable()
                // 2. 配置请求授权规则
                .authorizeHttpRequests(auth -> auth
                        // 前端静态资源/接口全部放开
                        .antMatchers("/", "/index.html", "/list.html", "/detail.html", "/tutorial/**").permitAll()
                        // 后台登录接口放开
                        .antMatchers("/admin/login", "/admin/doLogin").permitAll()
                        // CMS接口需要认证（可选，开发环境可先放开）
                        // .antMatchers("/admin/cms/**").authenticated()
                        // 所有请求都放开（开发环境专用，生产需改回上面的规则）
                        .anyRequest().permitAll()
                )
                // 3. 禁用默认登录页/退出（用自己的登录逻辑）
                .formLogin().disable()
                .logout().disable();

        return http.build();
    }
}