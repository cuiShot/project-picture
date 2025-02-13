package com.cc.ccPictureBackend.webSocket.disruptor;
import cn.hutool.json.JSONUtil;
import com.cc.ccPictureBackend.model.entity.User;

import com.cc.ccPictureBackend.service.UserService;
import com.cc.ccPictureBackend.webSocket.PictureEditHandler;
import com.cc.ccPictureBackend.webSocket.model.PictureEditMessageTypeEnum;
import com.cc.ccPictureBackend.webSocket.model.PictureEditRequestMessage;
import com.cc.ccPictureBackend.webSocket.model.PictureEditResponseMessage;
import com.lmax.disruptor.WorkHandler;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.Resource;

/**
 * 实现 WorkHandler 接口
 * 是 Disruptor 的消费者类
 * @Author cuicui
 * @Description
 */
@Component
public class PictureEditEventWorkHandler implements WorkHandler<PictureEditEvent> {

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private PictureEditHandler pictureEditHandler;

    @Override
    public void onEvent(PictureEditEvent event) throws Exception {
        PictureEditRequestMessage pictureEditRequestMessage = event.getPictureEditRequestMessage();
        WebSocketSession session = event.getSession();
        User user = event.getUser();
        Long pictureId = event.getPictureId();
        // 获取消息类别
        String type = pictureEditRequestMessage.getType();
        PictureEditMessageTypeEnum pictureEditMessageTypeEnum = PictureEditMessageTypeEnum.getEnumByValue(type);
        switch (pictureEditMessageTypeEnum){
            case ENTER_EDIT:
                pictureEditHandler.handleEnterEditMessage(pictureEditRequestMessage, session, user, pictureId);
                break;
            case EXIT_EDIT:
                pictureEditHandler.handleExitEditMessage(pictureEditRequestMessage, session, user, pictureId);
                break;
            case EDIT_ACTION:
                pictureEditHandler.handleEditActionMessage(pictureEditRequestMessage, session, user, pictureId);
                break;
            default:// 消息类型错误
                PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
                pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.ERROR.getValue());
                pictureEditResponseMessage.setMessage("消息类型错误");
                pictureEditResponseMessage.setUser(userService.getUserVO(user));
                session.sendMessage(new TextMessage(JSONUtil.toJsonStr(pictureEditResponseMessage)));
        }
    }
}
