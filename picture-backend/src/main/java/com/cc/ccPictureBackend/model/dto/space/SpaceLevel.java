package com.cc.ccPictureBackend.model.dto.space;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @Author cuicui
 * @Description
 */
@Data
@AllArgsConstructor
public class SpaceLevel {

    private int value;

    private String text;

    private long maxCount;

    private long maxSize;
}

