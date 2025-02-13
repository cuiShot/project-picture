package com.cc.ccPictureBackend.webSocket.disruptor;

import com.cc.ccPictureBackend.model.entity.User;
import com.cc.ccPictureBackend.webSocket.model.PictureEditRequestMessage;
import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

/**
 * @Author cuicui
 * @Description 图片协同编辑需要的信息，封装，之后可以放到 Disruptor 事件中
 */
@Data
public class PictureEditEvent {

    /**
     * 消息
     */
    private PictureEditRequestMessage pictureEditRequestMessage;

    /**
     * 当前用户的 session
     */
    private WebSocketSession session;

    /**
     * 当前用户
     */
    private User user;

    /**
     * 图片 id
     */
    private Long pictureId;

}
