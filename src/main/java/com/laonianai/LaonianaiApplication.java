package com.laonianai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.laonianai.mapper")
public class LaonianaiApplication {
    public static void main(String[] args) {
        SpringApplication.run(LaonianaiApplication.class, args);
    }
}