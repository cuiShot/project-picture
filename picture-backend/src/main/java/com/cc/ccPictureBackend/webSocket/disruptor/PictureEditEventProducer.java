package com.cc.ccPictureBackend.webSocket.disruptor;

import com.cc.ccPictureBackend.model.entity.User;
import com.cc.ccPictureBackend.webSocket.model.PictureEditRequestMessage;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;

/**
 * @Author cuicui
 * @Description Disruptor 缓冲区的生产者，通过 shutDown 方式优雅停机
 */
@Component
public class PictureEditEventProducer {

    @Resource
    Disruptor<PictureEditEvent> pictureEditEventDisruptor;

    public void publishPictureEditEvent(PictureEditRequestMessage pictureEditRequestMessage, WebSocketSession webSocketSession, User user,Long pictureId) {
        RingBuffer<PictureEditEvent> ringBuffer = pictureEditEventDisruptor.getRingBuffer();
        // 获取可以生成的位置
        long sequence = ringBuffer.next();
        // 设置属性
        PictureEditEvent pictureEditEvent = ringBuffer.get(sequence);
        pictureEditEvent.setPictureId(pictureId);
        pictureEditEvent.setUser(user);
        pictureEditEvent.setPictureEditRequestMessage(pictureEditRequestMessage);
        pictureEditEvent.setSession(webSocketSession);
        // 发布事件
        ringBuffer.publish(sequence);
    }


    /**
     * 优雅停机，即处理完缓冲区才停机
     */
    @PreDestroy
    public void close(){
        pictureEditEventDisruptor.shutdown();
    }
}
