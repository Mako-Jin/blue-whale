package com.bluewhale.oa.collab.domain.model;

import com.bluewhale.oa.common.OaErrorCode;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 个人任务聚合根。
 *
 * <p>状态机：{@code TODO ↔ DOING}，{@code TODO/DOING → DONE/CANCELLED}；
 * 终态不可回流，完成时写入 finishTime。</p>
 *
 * @author BlueWhale
 */
@Data
public class OaTask {

    /** 主键ID */
    private Long id;

    /** 任务标题 */
    private String title;

    /** 任务描述 */
    private String description;

    /** 负责人用户ID */
    private Long ownerId;

    /** 负责人姓名快照 */
    private String ownerName;

    /** 创建人用户ID */
    private Long creatorId;

    /** 创建人姓名快照 */
    private String creatorName;

    /** 优先级 */
    private String priority;

    /** 状态 */
    private String status;

    /** 截止日期 */
    private LocalDate dueDate;

    /** 来源 */
    private String source;

    /** 来源业务ID */
    private Long sourceId;

    /** 完成时间 */
    private LocalDateTime finishTime;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 变更任务状态。
     *
     * @param target 目标状态
     * @param now 当前时间
     * @return 是否发生变更
     */
    public boolean changeStatus(TaskStatus target, LocalDateTime now) {
        TaskStatus current = TaskStatus.of(status);
        if (current == target) {
            return false;
        }
        if (current == TaskStatus.DONE || current == TaskStatus.CANCELLED) {
            throw OaErrorCode.exception(OaErrorCode.TASK_STATUS_CONFLICT, "已完成或已取消的任务不可再次变更");
        }
        if (target == TaskStatus.TODO) {
            this.status = TaskStatus.TODO.name();
            this.finishTime = null;
            return true;
        }
        if (target == TaskStatus.DOING) {
            this.status = TaskStatus.DOING.name();
            return true;
        }
        this.status = target.name();
        this.finishTime = target == TaskStatus.DONE ? now : null;
        return true;
    }
}
