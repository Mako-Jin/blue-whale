package com.bluewhale.monitor.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 服务实例视图对象：服务名、主机、端口、健康标记与状态文案
 *
 * @author BlueWhale
 */
@Data
public class ServiceInstanceVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 服务名（Nacos 注册名） */
    private String serviceName;

    /** 实例主机地址 */
    private String host;

    /** 实例端口 */
    private int port;

    /** 是否健康 */
    private boolean healthy;

    /** 状态文案：UP 健康 / DOWN 不健康 */
    private String status;
}
