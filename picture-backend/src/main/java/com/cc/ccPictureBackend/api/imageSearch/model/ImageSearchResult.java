package com.cc.ccPictureBackend.api.imageSearch.model;

import lombok.Data;

/**
 * @Author cuicui
 * @Description
 */
@Data
public class ImageSearchResult {

    /**
     * 缩略图地址
     */
    private String thumbUrl;

    /**
     * 来源地址
     */
    private String fromUrl;
}

