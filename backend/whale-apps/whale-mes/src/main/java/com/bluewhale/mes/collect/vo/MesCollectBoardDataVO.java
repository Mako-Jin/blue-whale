package com.bluewhale.mes.collect.vo;

import lombok.Data;

import java.util.List;

/**
 * 看板数据视图（配置 + 各采集点最新样本）
 *
 * @author BlueWhale
 */
@Data
public class MesCollectBoardDataVO {

    /** 看板配置 */
    private MesCollectBoardVO board;

    /** 各采集点最新样本 */
    private List<MesMetricSampleVO> latestSamples;

    /** 数据生成时间 */
    private String generatedAt;
}
