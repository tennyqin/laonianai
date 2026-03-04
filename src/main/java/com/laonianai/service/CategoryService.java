package com.laonianai.service;

import com.laonianai.entity.Category;
import java.util.List;

public interface CategoryService {
    List<Category> findAll();
    Category findByName(String name);
    int add(Category category);
    int update(Category category);
    int delete(Long id);
}