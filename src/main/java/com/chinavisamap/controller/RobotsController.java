package com.chinavisamap.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.nio.charset.StandardCharsets;

@RestController
public class RobotsController {

    // JDK 8 不支持 """ 文本块，改回传统的字符串拼接
    private static final String ROBOTS = "User-agent: *\n" +
            "Allow: /\n" +
            "\n" +
            "Disallow: /api/\n" +
            "\n" +
            "Sitemap: https://chinavisamap.com/sitemap.xml\n";

    @GetMapping(
            value = "/robots.txt",
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    public ResponseEntity<String> robots() {
        return ResponseEntity
                .ok()
                .contentType(new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8))
                .body(ROBOTS);
    }
}