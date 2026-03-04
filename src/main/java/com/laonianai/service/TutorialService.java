package com.laonianai.service;

import com.laonianai.entity.Tutorial;
import java.util.List;

public interface TutorialService {
    List<Tutorial> findByCategoryId(Long categoryId);
    List<Tutorial> findByCategoryName(String categoryName);
    Tutorial findById(Long id);
    int add(Tutorial tutorial);
    int update(Tutorial tutorial);
    int delete(Long id);

    List<Tutorial> findByCategory(String category);

    Tutorial findByUrl(String url);
}