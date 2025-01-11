package com.cc.ccPictureBackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * @Author cuicui
 * @Description
 */
@Data
public class PictureUploadRequest implements Serializable {

    /**
     * 图片 id（用于修改）
     */
    private Long id;

    /**
     * 文件地址
     */
    private String fileUrl;

    /**
     * 图片名称
     * 批量抓取图片的时候，用来设置抓取到的图片的名字 name
     */
    private String picName;

    private static final long serialVersionUID = 1L;
}
