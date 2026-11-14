package com.bluewhale.workflow.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 已通过流程实例摘要视图对象（档案同步内网接口契约）。
 *
 * <p>{@code finishedAt} 取实例独立办结时间 {@code process_instance.finished_at}：
 * 审批通过时写入；历史数据为空时归档侧回落 {@code update_time}，保证保管到期日可计算。</p>
 *
 * @author BlueWhale
 */
@Data
public class ApprovedInstanceVO implements Serializable {

    private static final long serialVersionUID = 1L;

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

    /** 实例状态（固定为 1 已通过） */
    private Integer status;

    /** 办结时间（取实例更新时间） */
    private LocalDateTime finishedAt;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
