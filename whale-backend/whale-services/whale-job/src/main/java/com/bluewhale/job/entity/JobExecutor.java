package com.bluewhale.job.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 执行器注册实体，对应表 {@code job_executor}。
 *
 * <p>业务服务的执行器启动时向调度中心注册本实例地址与支持的任务编码，
 * 之后周期心跳；调度时按 {@code app_name + job_code} 选出在线实例，
 * 避免把目标地址写死在任务参数里（容器重建、扩缩容、灰度多版本都会让写死的地址失效）。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("job_executor")
public class JobExecutor extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户ID */
    private Long tenantId;

    /** 执行器应用名（Nacos 服务名），如 whale-crm */
    private String appName;

    /** 实例地址 host:port */
    private String address;

    /** 该实例支持的任务编码，逗号分隔 */
    private String jobCodes;

    /** 最近心跳时间：超过阈值未心跳视为离线 */
    private LocalDateTime lastBeat;

    /** 状态：1 在线 / 0 已注销 */
    private Integer status;
}
