package com.cc.ccPictureBackend.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * @Author cuicui
 * @Description
 */
@Data
public class SearchPictureByPictureRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    private static final long serialVersionUID = 1L;
}
