package com.bluewhale.job.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 执行器注册信息视图。
 *
 * @author BlueWhale
 */
@Data
public class JobExecutorVO {

    /** 执行器应用名（Nacos 服务名） */
    private String appName;

    /** 实例地址 host:port */
    private String address;

    /** 该实例支持的任务编码 */
    private List<String> jobCodes;

    /** 最近心跳时间 */
    private LocalDateTime lastBeat;

    /** 是否在线：状态正常且心跳未超时 */
    private Boolean online;
}
