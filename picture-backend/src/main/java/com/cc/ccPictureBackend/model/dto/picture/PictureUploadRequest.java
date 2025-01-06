package com.cc.ccPictureBackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * @Author cuicui
 * @Description
 */
@Data
public class PictureUploadRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 图片 id 用于修改
     */
    private Long id;

}
