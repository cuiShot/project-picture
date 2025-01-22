package com.cc.ccPictureBackend.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * @Author cuicui
 * @Description 空间信息编辑请求，目前仅允许编辑空间名称
 */
@Data
public class SpaceEditRequest implements Serializable {

    /**
     * 空间 id
     */
    private Long id;

    /**
     * 空间名称
     */
    private String spaceName;

    private static final long serialVersionUID = 1L;
}

