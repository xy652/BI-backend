package com.example.bi.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.bi.common.ErrorCode;
import com.example.bi.entity.Chart;
import com.example.bi.exception.BusinessException;
import com.example.bi.service.ChartService;
import com.example.bi.mapper.ChartMapper;
import org.springframework.stereotype.Service;

/**
* @author xy
* @description 针对表【chart(图表信息表)】的数据库操作Service实现
*/
@Service
public class ChartServiceImpl extends ServiceImpl<ChartMapper, Chart>
    implements ChartService{

    @Override
    public boolean saveAiResult(String result, long chartId) {
        String[] splits = result.split("【【【【【");

        if(splits.length < 3){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "AI 生成错误");
        }
        String genChart = splits[1].trim();
        String genResult = splits[2].trim();

        //保存到数据库
        Chart updateChartResult = new Chart();
        updateChartResult.setId(chartId);
        updateChartResult.setGenChart(genChart);
        updateChartResult.setGenResult(genResult);
        updateChartResult.setStatus("succeed");

        return this.updateById(updateChartResult);
    }
}




