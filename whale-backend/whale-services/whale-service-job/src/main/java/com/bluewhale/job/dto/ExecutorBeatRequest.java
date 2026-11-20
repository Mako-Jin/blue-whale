package com.bluewhale.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 执行器心跳 / 注销请求。
 *
 * @author BlueWhale
 */
@Data
public class ExecutorBeatRequest {

    /** 租户ID：为空时按默认租户处理 */
    private Long tenantId;

    /** 执行器应用名（Nacos 服务名） */
    @NotBlank(message = "{validation.job.executor.app-name-required}")
    @Size(max = 64, message = "{validation.job.executor.app-name-size}")
    private String appName;

    /** 实例地址 host:port */
    @NotBlank(message = "{validation.job.executor.address-required}")
    @Size(max = 128, message = "{validation.job.executor.address-size}")
    private String address;
}
