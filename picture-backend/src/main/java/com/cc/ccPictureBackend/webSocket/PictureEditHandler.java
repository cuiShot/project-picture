package com.cc.ccPictureBackend.webSocket;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONUtil;
import com.cc.ccPictureBackend.model.entity.User;
import com.cc.ccPictureBackend.service.UserService;
import com.cc.ccPictureBackend.webSocket.disruptor.PictureEditEventProducer;
import com.cc.ccPictureBackend.webSocket.model.PictureEditActionEnum;
import com.cc.ccPictureBackend.webSocket.model.PictureEditMessageTypeEnum;
import com.cc.ccPictureBackend.webSocket.model.PictureEditRequestMessage;
import com.cc.ccPictureBackend.webSocket.model.PictureEditResponseMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author cuicui
 * @Description 定义webSocket处理器类
 * 实现 TextWebSocketHandler ，以字符串的方式发送和接受信息
 */
@Component
public class PictureEditHandler extends TextWebSocketHandler {

    @Resource
    private UserService userService;

    @Resource
    private PictureEditEventProducer pictureEditEventProducer;

    // 注意:由于客户端连接的并发性,使用并发包(JUC)中的ConcurrentHashMap,来保证线程安全
    // 每张图片的编辑状态，key:pictureId  value:正在编辑该图片的用户的id
    private final Map<Long, Long> pictureEditingUsers = new ConcurrentHashMap();
    // 保存所有链接的会话,key:pictureId  value:该 图片 下用户回话集合
    private final Map<Long, Set<WebSocketSession>> pictureSessions = new ConcurrentHashMap();


    /**
     * /由于消息需要传递给协作者，编写一个 广播消息 的方法。
     * 根据 pictureId 将消息发送给编辑该图片的所有会话
     *
     * @param pictureId
     * @param pictureEditResponseMessage
     * @param excludeSession             排除该会话
     */
    private void broadcastPictureEditing(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage, WebSocketSession excludeSession) throws IOException {
        // 获取所有该图片的会话
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (CollUtil.isNotEmpty(sessionSet)) {
            // 创建 ObjectMapper
            ObjectMapper objectMapper = new ObjectMapper();
            // 配置序列化，将 Long 转化成 String，解决精度丢失
            SimpleModule simpleModule = new SimpleModule();
            simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
            simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);
            objectMapper.registerModule(simpleModule);
            // 序列化为 JSON 字符串
            String message = objectMapper.writeValueAsString(pictureEditResponseMessage);
            TextMessage textMessage = new TextMessage(message);
            for (WebSocketSession session : sessionSet) {
                // 排除 session
                if (excludeSession != null && excludeSession.equals(session)) {
                    continue;
                }
                if (session.isOpen()) {
                    session.sendMessage(textMessage);
                }
            }
        }
    }


    /**
     * 全部广播
     *
     * @param pictureId
     * @param pictureEditResponseMessage
     * @throws Exception
     */
    private void broadcastToPicture(Long pictureId, PictureEditResponseMessage pictureEditResponseMessage) throws Exception {
        broadcastPictureEditing(pictureId, pictureEditResponseMessage, null);
    }


    /**
     * 建立了连接后，保存会话到集合中，并给其他会话发送信息
     *
     * @param session
     * @throws Exception
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        super.afterConnectionEstablished(session);
        // 保存会话到集合中
        User user = (User) session.getAttributes().get("user");
        Long pictureId = (Long) session.getAttributes().get("pictureId");
        pictureSessions.putIfAbsent(pictureId, ConcurrentHashMap.newKeySet());
        pictureSessions.get(pictureId).add(session);

        // 构造相应
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s加入编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        // 广播给同一张图片编辑的用户
        broadcastPictureEditing(pictureId, pictureEditResponseMessage, null);
    }


    /**
     * webSocket 链接关掉的时候，移除当前用户的编辑状态，并从集合中删除该会话，
     * 广播消息
     * @param session
     * @param status
     * @throws Exception
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Map<String, Object> attributes = session.getAttributes();
        Long pictureId = (Long) attributes.get("pictureId");
        User user = (User) attributes.get("user");
        // 移除当前用户的编辑状态
        handleEditActionMessage(null,session,user,pictureId);

        // 删除会话
        Set<WebSocketSession> sessionSet = pictureSessions.get(pictureId);
        if (sessionSet == null) {
            sessionSet.remove(session);
            if (sessionSet.isEmpty()){
                pictureSessions.remove(pictureId);
            }
        }

        // 响应，广播
        PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
        pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.INFO.getValue());
        String message = String.format("%s离开编辑", user.getUserName());
        pictureEditResponseMessage.setMessage(message);
        pictureEditResponseMessage.setUser(userService.getUserVO(user));
        broadcastToPicture(pictureId, pictureEditResponseMessage);

    }


    /**
     * @param session
     * @param message
     * @throws Exception
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // 将消息解析为 PictureEditMessage
        PictureEditRequestMessage pictureEditRequestMessage = JSONUtil.toBean(message.getPayload(), PictureEditRequestMessage.class);
        // 从 Session 属性中获取公共参数
        Map<String, Object> attributes = session.getAttributes();
        User user = (User) attributes.get("user");
        Long pictureId = (Long) attributes.get("pictureId");
        // 生产消息
        pictureEditEventProducer.publishPictureEditEvent(pictureEditRequestMessage, session, user, pictureId);
    }


    /**
     * 用户提交编辑动作，执行编辑操作
     * 将该操作同步给 其他加入编辑的成员
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    public void handleEditActionMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        if (pictureEditRequestMessage == null) {
            return;
        }
        Long editingUserId = pictureEditingUsers.get(pictureId);
        String editAction = pictureEditRequestMessage.getEditAction();
        PictureEditActionEnum actionEnum = PictureEditActionEnum.getEnumByValue(editAction);
        if (actionEnum == null) {
            return;
        }
        if(editingUserId != null && editingUserId.equals(user.getId())) {
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            String message = String.format("%s正在执行%s",user.getUserName(),actionEnum.getText());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setEditAction(editAction);
            broadcastPictureEditing(pictureId,pictureEditResponseMessage,session);
        }

    }

    /**
     * 用户退出编辑，移除当前用户的编辑状态，广播给其他成员
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     */
    public void handleExitEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        Long editingUserId = pictureEditingUsers.get(pictureId);
        if (editingUserId != null && editingUserId.equals(user.getId())) {
            // 移除当前用户的编辑状态
            pictureEditingUsers.remove(pictureId);
            // 构造相应，发送退出编辑的消息通知
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            String message = String.format("%s退出编辑%s",user.getUserName(),user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EXIT_EDIT.getValue());
            broadcastToPicture(pictureId,pictureEditResponseMessage);
        }

    }

    /**
     * 用户进入编辑状态，设置当前用户为编辑用户，并向其他客户端发送信息
     * @param pictureEditRequestMessage
     * @param session
     * @param user
     * @param pictureId
     * @throws Exception
     */
    public void handleEnterEditMessage(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession session, User user, Long pictureId) throws Exception {
        // 没有用户正在编辑该图片,该用户才能进入编辑状态
        if (!pictureEditingUsers.containsKey(pictureId)) {
            // 设置当前用户为编辑用户
            pictureEditingUsers.put(pictureId, user.getId());
            PictureEditResponseMessage pictureEditResponseMessage = new PictureEditResponseMessage();
            pictureEditResponseMessage.setType(PictureEditMessageTypeEnum.EDIT_ACTION.getValue());
            pictureEditResponseMessage.setUser(userService.getUserVO(user));
            String message = String.format("%s正在编辑该图片", user.getUserName());
            pictureEditResponseMessage.setMessage(message);
            broadcastToPicture(pictureId, pictureEditResponseMessage);
        }
    }
}
