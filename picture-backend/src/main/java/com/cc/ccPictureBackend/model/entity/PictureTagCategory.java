package com.cc.ccPictureBackend.model.entity;

import lombok.Data;

import java.util.List;

/**
 * @Author cuicui
 * @Description
 */
@Data
public class PictureTagCategory {
    /**
     * tag list 在PictureController 中写死了
     */
    private List<String> tagList;

    /**
     * PictureController 中写死了
     */
    private List<String> categoryList;
}
