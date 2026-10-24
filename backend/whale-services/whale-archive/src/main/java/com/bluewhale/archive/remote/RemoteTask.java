package com.bluewhale.archive.remote;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工作流任务轨迹节点（跨服务内网响应体）。
 *
 * @author BlueWhale
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class RemoteTask {

    /** 任务ID */
    private Long id;

    /** 节点下标 */
    private Integer nodeIndex;

    /** 节点名称 */
    private String nodeName;

    /** 节点键 */
    private String nodeKey;

    /** 节点表单编码 */
    private String formCode;

    /** 审批人账号 */
    private String assignee;

    /** 任务类型 */
    private String signType;

    /** 会签策略 */
    private String signPolicy;

    /** 状态：0 待办 / 1 同意 / 2 驳回 / 3 取消 */
    private Integer status;

    /** 审批意见 */
    private String comment;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
