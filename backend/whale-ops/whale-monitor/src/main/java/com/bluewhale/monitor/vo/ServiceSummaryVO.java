package com.bluewhale.monitor.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 服务聚合概览视图对象：服务名、实例总数、健康实例数
 *
 * @author BlueWhale
 */
@Data
public class ServiceSummaryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 服务名（Nacos 注册名） */
    private String serviceName;

    /** 实例总数 */
    private int instanceCount;

    /** 健康实例数 */
    private int healthyInstanceCount;
}
