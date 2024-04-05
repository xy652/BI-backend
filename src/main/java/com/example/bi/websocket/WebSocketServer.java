package com.example.bi.websocket;


import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.Session;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

@Component
@ServerEndpoint("/ws/{sid}")
public class WebSocketServer {

    //存放会话对象
    private static  Map<String,Session> sessionMap = new HashMap();

    //连接建立成功，调用方法
    @OnOpen
    public void onOpen(Session session, @PathParam("sid")String sid){
        System.out.println("客户端" + sid + "建立连接");
        sessionMap.put(sid,session);
    }

    //收到客户端消息，调用方法
    @OnMessage
    public void onMessage(String message, @PathParam("sid")String sid){
        System.out.println("收到来自客户端" + sid + "的消息：" + message);
    }

    //连接关闭，调用方法
    @OnClose
    public void onClose(@PathParam("sid")String sid){
        System.out.println("连接断开" + sid);
        sessionMap.remove(sid);
    }

    //群发
    public void sendAllClient(String message){

        Collection<Session> sessions = sessionMap.values();
        for (Session session : sessions) {
            try {
                //服务器向客户端发送消息
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
