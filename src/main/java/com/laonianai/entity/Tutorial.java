package com.laonianai.entity;

import lombok.Data;
import java.util.Date;

@Data
public class Tutorial {
    private Long id;
    private String title;
    private String content;
    private Long categoryId;
    private Date createTime;
    private Date updateTime;
    // 关联分类名称（用于前端展示）
    private String categoryName;
    private String url;
}