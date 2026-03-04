package com.laonianai.entity;

import lombok.Data;
import java.util.Date;

@Data
public class Category {
    private Long id;
    private String name;
    private Integer sort;
    private Date createTime;
    private Date updateTime;
}