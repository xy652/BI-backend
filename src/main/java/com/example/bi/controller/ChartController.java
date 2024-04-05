package com.example.bi.controller;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.bi.annotation.AuthCheck;
import com.example.bi.bimq.BiProducer;
import com.example.bi.common.BaseResponse;
import com.example.bi.common.DeleteRequest;
import com.example.bi.common.ErrorCode;
import com.example.bi.common.ResultUtils;
import com.example.bi.constant.CommonConstant;
import com.example.bi.constant.UserConstant;
import com.example.bi.dto.chart.AiChartRequest;
import com.example.bi.dto.chart.ChartAddRequest;
import com.example.bi.dto.chart.ChartQueryRequest;
import com.example.bi.dto.chart.ChartUpdateRequest;
import com.example.bi.entity.Chart;
import com.example.bi.entity.User;
import com.example.bi.exception.BusinessException;
import com.example.bi.exception.ThrowUtils;
import com.example.bi.manager.AiManager;
import com.example.bi.service.ChartService;
import com.example.bi.service.UserService;
import com.example.bi.utils.ExcelUtils;
import com.example.bi.utils.SqlUtils;
import com.example.bi.vo.BiResponse;
import com.example.bi.vo.LoginUserVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@RestController
@RequestMapping("/chart")
@Slf4j
public class ChartController {

    @Resource
    private ChartService chartService;

    @Resource
    private UserService userService;

    @Resource
    private AiManager aiManager;

    @Resource
    private ThreadPoolExecutor threadPoolExecutor;

    @Resource
    private BiProducer biProducer;

     // 获取查询包装类
    private QueryWrapper<Chart> getQueryWrapper(ChartQueryRequest chartQueryRequest) {
        QueryWrapper<Chart> queryWrapper = new QueryWrapper<>();
        if (chartQueryRequest == null) {
            return queryWrapper;
        }
        Long id = chartQueryRequest.getId();
        String name = chartQueryRequest.getName();
        String goal = chartQueryRequest.getGoal();
        String chartType = chartQueryRequest.getChartType();
        Long userId = chartQueryRequest.getUserId();
        String sortField = chartQueryRequest.getSortField();
        String sortOrder = chartQueryRequest.getSortOrder();
        // 根据获取到的条件值逐一构建查询条件
        queryWrapper.eq(id != null && id > 0, "id", id);
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        queryWrapper.eq(StringUtils.isNotBlank(goal), "goal", goal);
        queryWrapper.eq(StringUtils.isNotBlank(chartType), "chartType", chartType);
//        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(userId != null, "userId", userId);
        queryWrapper.eq("isDelete", false);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), sortOrder.equals(CommonConstant.SORT_ORDER_ASC),
                sortField);
        return queryWrapper;
    }

    //创建图表
    @PostMapping("/add")
    public BaseResponse<Long> addChart(@RequestBody ChartAddRequest chartAddRequest, HttpServletRequest request) {
        //请求是否为空
        if (chartAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartAddRequest, chart);
        User loginUser = userService.getLoginUser(request);
        chart.setUserId(loginUser.getId());
        boolean result = chartService.save(chart);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        long newChartId = chart.getId();
        return ResultUtils.success(newChartId);
    }

    //删除图表
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteChart(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldChart.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        boolean b = chartService.removeById(id);
        return ResultUtils.success(b);
    }

     // 更新（仅管理员）
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateChart(@RequestBody ChartUpdateRequest chartUpdateRequest) {
        if (chartUpdateRequest == null || chartUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = new Chart();
        BeanUtils.copyProperties(chartUpdateRequest, chart);
        long id = chartUpdateRequest.getId();
        // 判断是否存在
        Chart oldChart = chartService.getById(id);
        ThrowUtils.throwIf(oldChart == null, ErrorCode.NOT_FOUND_ERROR);
        boolean result = chartService.updateById(chart);
        return ResultUtils.success(result);
    }

     //根据 id 获取
    @GetMapping("/get")
    public BaseResponse<Chart> getChartById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Chart chart = chartService.getById(id);
        if (chart == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return ResultUtils.success(chart);
    }


     //分页获取列表（封装类）

    @PostMapping("/list/page")
    public BaseResponse<Page<Chart>> listChartByPage(@RequestBody ChartQueryRequest chartQueryRequest,
                                                     HttpServletRequest request) {
        long current = chartQueryRequest.getCurrent();
        long size = chartQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        //获取登录用户信息
        LoginUserVO loginUserVO = (LoginUserVO) request.getSession().getAttribute("loginUser");
        if( loginUserVO == null){
            return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR);
        }
        long userId = loginUserVO.getId();
        //建立查询条件，只查询当前登录用户创建的图表
        QueryWrapper<Chart> queryWrapper = getQueryWrapper(chartQueryRequest);
        queryWrapper.eq("userId", userId);
        //分页查询图表数据
        Page<Chart> chartPage = chartService.page(new Page<>(current, size), queryWrapper);
        return ResultUtils.success(chartPage);
    }

    //AI智能分析
    @PostMapping("/create")
    public BaseResponse<BiResponse> getChartByAi(@RequestPart("file") MultipartFile multipartFile, AiChartRequest aiChartRequest, HttpServletRequest request){

        //从请求对象中获取分析需要的信息
        //图表名称
        String name = aiChartRequest.getName();
        //分析的目标
        String goal = aiChartRequest.getGoal();
        //图表类型
        String chartType = aiChartRequest.getChartType();

        //检验参数
        //如果分析目标为空，则抛出异常
        ThrowUtils.throwIf(StringUtils.isBlank(goal),ErrorCode.PARAMS_ERROR,"目标为空");

        //检验传入文件大小
        long size = multipartFile.getSize();
        final long ONE_MB = 1024*1024L;
        ThrowUtils.throwIf(size > ONE_MB,ErrorCode.PARAMS_ERROR,"文件大小超过1M");

        User loginUser = userService.getLoginUser(request);

        //调用的AI模型ID
        long ModelId = CommonConstant.BI_MODEL_ID;

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

        //压缩数据（将multipartFile转换为CSV格式）
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");
        //调用AI模型
        String result = aiManager.doChat(ModelId,userInput.toString());
        String[] splits = result.split("【【【【【");
        if(splits.length < 3){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 生成错误");
        }
        String genChart = splits[1].trim();
        String genResult = splits[2].trim();
        //插入数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setGenChart(genChart);
        chart.setGenResult(genResult);
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");
        BiResponse biResponse = new BiResponse();
        biResponse.setGenChart(genChart);
        biResponse.setGenResult(genResult);
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);

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

    //AI智能分析异步
    @PostMapping("/create/async")
    public BaseResponse<BiResponse> genChartByAiAsync(@RequestPart("file") MultipartFile multipartFile, AiChartRequest aiChartRequest, HttpServletRequest request) {

        //从请求对象中获取分析需要的信息
        //图表名称
        String name = aiChartRequest.getName();
        //分析的目标
        String goal = aiChartRequest.getGoal();
        //图表类型
        String chartType = aiChartRequest.getChartType();

        //检验参数
        //如果分析目标为空，则抛出异常
        ThrowUtils.throwIf(StringUtils.isBlank(goal),ErrorCode.PARAMS_ERROR,"目标为空");

        //检验传入文件大小
        long size = multipartFile.getSize();
        final long ONE_MB = 1024*1024L;
        ThrowUtils.throwIf(size > ONE_MB,ErrorCode.PARAMS_ERROR,"文件大小超过1M");

        User loginUser = userService.getLoginUser(request);

        //调用的AI模型ID
        long ModelId = CommonConstant.BI_MODEL_ID;

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

        //压缩数据（将multipartFile转换为CSV格式）
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        //插入数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setStatus("wait");
        chart.setUserId(loginUser.getId());
        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        //异步处理
        CompletableFuture.runAsync(() -> {
            // 先修改图表任务状态为 “执行中”。等执行成功后，修改为 “已完成”、保存执行结果
            // 执行失败后，状态修改为 “失败”，记录任务失败信息。
            Chart updateChart = new Chart();
            updateChart.setId(chart.getId());
            updateChart.setStatus("running");
            boolean b = chartService.updateById(updateChart);
            if(!b){
                handleChartUpdateError(chart.getId(),"更新图表执行中状态失败");
            }
            //调用AI模型
            String result = aiManager.doChat(ModelId,userInput.toString());

            boolean save = chartService.saveAiResult(result, chart.getId());
            if (!save){
                handleChartUpdateError(chart.getId(), "图表数据保存失败");
            }
        },threadPoolExecutor);
        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);
    }

    //AI智能分析异步消息队列
    @PostMapping("/create/async/mq")
    public BaseResponse<BiResponse> genChartByAiAsyncMq(@RequestPart("file") MultipartFile multipartFile, AiChartRequest aiChartRequest, HttpServletRequest request) {

        //从请求对象中获取分析需要的信息
        //图表名称
        String name = aiChartRequest.getName();
        //分析的目标
        String goal = aiChartRequest.getGoal();
        //图表类型
        String chartType = aiChartRequest.getChartType();

        //检验参数
        //如果分析目标为空，则抛出异常
        ThrowUtils.throwIf(StringUtils.isBlank(goal),ErrorCode.PARAMS_ERROR,"目标为空");

        //检验传入文件大小
        long size = multipartFile.getSize();
        final long ONE_MB = 1024*1024L;
        ThrowUtils.throwIf(size > ONE_MB,ErrorCode.PARAMS_ERROR,"文件大小超过1M");

        User loginUser = userService.getLoginUser(request);


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

        //压缩数据（将multipartFile转换为CSV格式）
        String csvData = ExcelUtils.excelToCsv(multipartFile);
        userInput.append(csvData).append("\n");

        //插入数据库
        Chart chart = new Chart();
        chart.setName(name);
        chart.setGoal(goal);
        chart.setChartData(csvData);
        chart.setChartType(chartType);
        chart.setStatus("wait");
        chart.setUserId(loginUser.getId());

        boolean saveResult = chartService.save(chart);
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "图表保存失败");

        //异步消息队列处理
        biProducer.sendMessage(String.valueOf(chart.getId()));

        BiResponse biResponse = new BiResponse();
        biResponse.setChartId(chart.getId());
        return ResultUtils.success(biResponse);
    }
}
