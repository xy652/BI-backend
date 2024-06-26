package com.example.bi.websocket;

import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class WebSocketTask {

    @Resource
    private WebSocketServer webSocketServer;

    /**
     * 通过WebSocket每隔5秒向客户端发送消息
     */
    //@Scheduled(cron = "0/5 * * * * ?")
    public void sendMessageToClient() {
        webSocketServer.sendAllClient("这是来自服务端的消息" );
    }
}
