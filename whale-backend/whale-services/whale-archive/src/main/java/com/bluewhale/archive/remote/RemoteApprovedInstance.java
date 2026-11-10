package com.bluewhale.archive.remote;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流已通过实例（跨服务内网响应体）。
 *
 * @author BlueWhale
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RemoteApprovedInstance {

    /** 实例ID */
    private Long id;

    /** 流程定义 ID */
    private Long definitionId;

    /** 流程定义编码 */
    private String definitionCode;

    /** 流程定义版本 */
    private Integer definitionVersion;

    /** 实例标题 */
    private String title;

    /** 业务键 */
    private String businessKey;

    /** 发起人账号 */
    private String initiator;

    /** 实例状态 */
    private Integer status;

    /** 办结时间 */
    private LocalDateTime finishedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
