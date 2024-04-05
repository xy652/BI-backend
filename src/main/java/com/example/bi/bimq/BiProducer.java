package com.example.bi.bimq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class BiProducer {
    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     发送消息
     */
    public void sendMessage(String message){

        rabbitTemplate.convertAndSend(BiMqConstant.BI_EXCHANGE_NAME,BiMqConstant.BI_ROUTING_KEY, message);
    }
}
