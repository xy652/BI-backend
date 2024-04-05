package com.example.bi.bimq;

import com.example.bi.common.ErrorCode;
import com.example.bi.constant.CommonConstant;
import com.example.bi.entity.Chart;
import com.example.bi.exception.BusinessException;
import com.example.bi.manager.AiManager;
import com.example.bi.service.ChartService;
import com.example.bi.websocket.WebSocketServer;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
@Slf4j
public class BiConsumer {

    @Resource
    private ChartService chartService;

    @Resource
    private AiManager aiManager;
    @Resource
    private WebSocketServer webSocketServer;

    @SneakyThrows
    @RabbitListener(queues = {BiMqConstant.BI_QUEUE_NAME}, ackMode = "MANUAL")
    public void receiveMessage(String message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag){
        log.info("receiveMessage message = {}", message);
        //如果消息为空，则拒绝该消息，不重新发送，不重新放入队列
        if(StringUtils.isBlank(message)){
            //拒绝此消息，也不重新放到队列里
            channel.basicNack(deliveryTag,false,false);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"消息为空");
        }
        long chartId = Long.parseLong(message);
        Chart chart = chartService.getById(chartId);
        if(chart == null){
            channel.basicNack(deliveryTag,false,false);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,"图表为空");
        }
        // 先修改图表任务状态为 “执行中”。等执行成功后，修改为 “已完成”、保存执行结果
        // 执行失败后，状态修改为 “失败”，记录任务失败信息。
        Chart updateChart = new Chart();
        updateChart.setId(chart.getId());
        updateChart.setStatus("running");
        boolean updateResult = chartService.updateById(updateChart);
        if(!updateResult){
            channel.basicNack(deliveryTag,false,false);
            handleChartUpdateError(chart.getId(),"更新图表执行中状态失败");
        }
        //调用AI模型
        String result = aiManager.doChat(CommonConstant.BI_MODEL_ID,buildUserInput(chart));
        boolean saveResult = chartService.saveAiResult(result, chart.getId());
        if (!saveResult){
            handleChartUpdateError(chart.getId(), "图表数据保存失败");
        }
        webSocketServer.sendAllClient("图表已生成，请前往“我的图表”查看！");

        //消息确认
        channel.basicAck(deliveryTag,false);
    }

    //构造用户输入
    private String buildUserInput(Chart chart){
        String goal = chart.getGoal();
        String chartType = chart.getChartType();
        String csvData = chart.getChartData();

        //构造用户输入
        StringBuilder userInput = new StringBuilder();
        userInput.append("分析需求：").append("\n");

        //拼接分析目标
        String userGoal = goal;
        if(StringUtils.isNotBlank(chartType)){
            userGoal +="，请使用" + chartType;
        }
        userInput.append(userGoal).append("\n");
        userInput.append("原始数据：").append("\n");
        userInput.append(csvData).append("\n");

        return userInput.toString();
    }
    private void handleChartUpdateError(long chartId, String execMessage) {
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setStatus("failed");
        updateChartResult.setExecMessage("execMessage");
        boolean updateResult = chartService.updateById(updateChartResult);
        webSocketServer.sendAllClient("系统好像出现了一点问题");
        if (!updateResult) {
            log.error("更新图表失败状态失败" + chartId + "," + execMessage);
        }
    }
}
