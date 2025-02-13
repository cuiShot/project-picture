package com.cc.ccPictureBackend.webSocket.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author cuicui
 * @Description
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PictureEditRequestMessage {

    /**
     * 消息类型，例如“ENTER_EDIT”,"EDIT_ACTION"
     */
    private String type;


    /**
     * 执行的编辑动作 左旋，右旋。。。。
     */
    private String editAction;
}
