package com.cc.ccPictureBackend.model.dto.reviewPicture;

import lombok.Data;

import java.io.Serializable;

/**
 * @Author cuicui
 * @Description 审核图片的请求封装类
 */
@Data
public class PictureReviewRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 状态：0-待审核, 1-通过, 2-拒绝
     */
    private Integer reviewStatus;

    /**
     * 审核信息
     */
    private String reviewMessage;


    private static final long serialVersionUID = 1L;
}

