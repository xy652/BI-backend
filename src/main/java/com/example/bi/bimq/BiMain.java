package com.example.bi.bimq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import java.util.HashMap;
import java.util.Map;


/**
 * 用于创建交换机和队列（只需在程序启动前执行一次）
  */
public class BiMain {
    public static void main(String[] args) {
        try{
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("localhost");
            Connection connection = factory.newConnection();
            Channel channel = connection.createChannel();
            //声明初始交换机
            channel.exchangeDeclare(BiMqConstant.BI_EXCHANGE_NAME,"direct",true);
            //声明死信交换机
            channel.exchangeDeclare(BiMqConstant.BI_DEAD_EXCHANGE_NAME,"direct",true);
            //声明死信队列
            channel.queueDeclare(BiMqConstant.BI_DEAD_QUEUE_NAME,true,false,false,null);
            //绑定死信队列到死信交换机
            channel.queueBind(BiMqConstant.BI_DEAD_QUEUE_NAME,BiMqConstant.BI_DEAD_EXCHANGE_NAME,BiMqConstant.BI_DEAD_EXCHANGE_NAME);

            // 设置初始队列的参数，包括死信交换机和死信路由键
            Map<String, Object> arguments = new HashMap<>();
            //消息在队列中滞留60秒后，成为死信将被发送到的交换机和路由键
            arguments.put("x-message-ttl",60000);
            arguments.put("x-dead-letter-exchange", BiMqConstant.BI_DEAD_EXCHANGE_NAME);
            arguments.put("x-dead-letter-routing-key", BiMqConstant.BI_DEAD_QUEUE_NAME);

            //声明初始队列,并绑定到死信交换机
            channel.queueDeclare(BiMqConstant.BI_QUEUE_NAME,true,false,false,arguments);
            channel.queueBind(BiMqConstant.BI_QUEUE_NAME,BiMqConstant.BI_EXCHANGE_NAME,BiMqConstant.BI_ROUTING_KEY);

            System.out.println("初始化 RabbitMQ 连接成功！");

        } catch(Exception e){
            // 处理异常
            e.printStackTrace();
        }
    }
}
