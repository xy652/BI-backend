package com.example.bi.bimq;

import com.example.bi.common.ErrorCode;
import com.example.bi.entity.Chart;
import com.example.bi.exception.BusinessException;
import com.example.bi.service.ChartService;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class BiDeadConsumer {

    @Resource
    private ChartService chartService;


    @SneakyThrows
    @RabbitListener(queues = {BiMqConstant.BI_DEAD_QUEUE_NAME}, ackMode = "MANUAL")
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag){
        log.warn("接收到死信队列信息，receiveMessage={}=======================================",message);
        if (StringUtils.isBlank(message)){
            //消息为空，消息拒绝，不重复发送，不重新放入队列
            channel.basicNack(deliveryTag,false,false);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"消息为空");
        }
        long chartId = Long.parseLong(message);
        Chart chart = chartService.getById(chartId);
        if (chart == null){
            channel.basicNack(deliveryTag,false,false);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"图表为空");
        }

        //修改表状态为执行中，执行成功修改为“已完成”；执行失败修改为“失败”
        Chart updateChart = new Chart();
        updateChart.setId(chart.getId());
        updateChart.setStatus("failed");
        boolean updateResult = chartService.updateById(updateChart);
        if (!updateResult){
            handleChartUpdateError(chart.getId(),"更新图表执行状态失败");
            return;
        }
        //消息确认
        channel.basicAck(deliveryTag,false);
    }

    private void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus("failed");
        updateChartResult.setExecMessage("execMessage");
        boolean updateResult = chartService.updateById(updateChartResult);
        if (!updateResult) {
            log.error("更新图表失败状态失败" + chartId + "," + execMessage);
        }
    }
}
