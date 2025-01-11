package com.cc.ccPictureBackend.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * @Author cuicui
 * @Description 审核状态枚举类
 */
@Getter
public enum PictureReviewStatusEnmu {
    REVIEWING("待审核",0),
    PASS("通过",1),
    REJECT("拒绝",2);

    private final String text;
    private final int value;

    PictureReviewStatusEnmu(String text, int value) {
        this.text = text;
        this.value = value;
    }

    public static PictureReviewStatusEnmu getEnum(Integer value) {
        if(ObjUtil.isEmpty(value)) {
            return null;
        }
        for(PictureReviewStatusEnmu enmu : PictureReviewStatusEnmu.values()) {
            if(enmu.getValue() == value) {
                return enmu;
            }
        }
        return null;
    }

}
