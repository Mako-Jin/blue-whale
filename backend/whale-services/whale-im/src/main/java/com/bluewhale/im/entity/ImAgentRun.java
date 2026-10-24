package com.bluewhale.im.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 智能体运行记录实体（{@code im_agent_run}）。
 *
 * <p>一次提问对应一条记录：请求消息、回复消息、运行状态、工具轨迹与确认信息。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("im_agent_run")
public class ImAgentRun extends BaseEntity {

    /** 会话ID */
    private Long conversationId;

    /** 触发本次运行的用户消息ID */
    private Long requestMessageId;

    /** 智能体回复消息ID（成功后回填） */
    private Long responseMessageId;

    /** 运行状态：PENDING / RUNNING / WAITING_CONFIRM / SUCCESS / FAILED */
    private String status;

    /** 工具调用轨迹与引用（JSON 文本） */
    private String toolTraceJson;

    /** 写操作确认信息（JSON 文本） */
    private String confirmationJson;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 结束时间 */
    private LocalDateTime finishedAt;
}
