package com.example.bi.dto.chart;

import lombok.Data;

import java.io.Serializable;

@Data
public class AiChartRequest implements Serializable {

    /**
     * 名称
     */
    private String name;

    /**
     * 分析目标
     */
    private String goal;

    /**
     * 图表类型
     */
    private String chartType;

    private static final long serialVersionUID = 1L;
}
