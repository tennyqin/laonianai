package com.laonianai.service.impl;

import com.laonianai.entity.Tutorial;
import com.laonianai.mapper.TutorialMapper;
import com.laonianai.service.TutorialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class TutorialServiceImpl implements TutorialService {

    @Autowired
    private TutorialMapper tutorialMapper;

    @Override
    public List<Tutorial> findByCategoryId(Long categoryId) {
        return tutorialMapper.findByCategoryId(categoryId);
    }

    @Override
    public List<Tutorial> findByCategoryName(String categoryName) {
        return tutorialMapper.findByCategoryName(categoryName);
    }

    @Override
    public Tutorial findById(Long id) {
        return tutorialMapper.findById(id);
    }

    @Override
    public int add(Tutorial tutorial) {
        return tutorialMapper.add(tutorial);
    }

    @Override
    public int update(Tutorial tutorial) {
        return tutorialMapper.update(tutorial);
    }

    @Override
    public int delete(Long id) {
        return tutorialMapper.delete(id);
    }

    @Override
    public List<Tutorial> findByCategory(String category) {
        return tutorialMapper.selectByCategory(category);
    }

    @Override
    public Tutorial findByUrl(String url) {
        return tutorialMapper.selectByUrl(url);
    }
}