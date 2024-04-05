package com.example.bi.service;

import com.example.bi.entity.Chart;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author xy
* @description 针对表【chart(图表信息表)】的数据库操作Service
*/
public interface ChartService extends IService<Chart> {

    //处理AI返回结果
    boolean saveAiResult(String result, long chartId);

}
