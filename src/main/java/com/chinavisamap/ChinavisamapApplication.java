package com.chinavisamap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class ChinavisamapApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChinavisamapApplication.class, args);
    }
}