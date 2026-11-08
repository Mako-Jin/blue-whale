package com.bluewhale.job.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 执行器注册请求（业务服务启动时上报）。
 *
 * @author BlueWhale
 */
@Data
public class ExecutorRegisterRequest {

    /** 租户ID：为空时按默认租户注册 */
    private Long tenantId;

    /** 执行器应用名（Nacos 服务名） */
    @NotBlank(message = "执行器应用名不能为空")
    @Size(max = 64, message = "执行器应用名长度不能超过 64")
    private String appName;

    /** 实例地址 host:port */
    @NotBlank(message = "执行器地址不能为空")
    @Size(max = 128, message = "执行器地址长度不能超过 128")
    private String address;

    /** 该实例支持的任务编码 */
    private List<String> jobCodes;
}
