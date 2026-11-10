package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.oa.common.OaErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 人员异动任务（离职清单）。
 *
 * @author BlueWhale
 */
@Data
public class EmployeeChangeTask {

    /** 主键 */
    private Long id;

    /** 异动单ID */
    private Long changeId;

    /** 任务编码 */
    private String taskCode;

    /** 任务名称 */
    private String taskName;

    /** 负责人用户ID */
    private Long assigneeId;

    /** 负责人姓名快照 */
    private String assigneeName;

    /** 状态 */
    private EmployeeChangeTaskStatus status;

    /** 完成时间 */
    private LocalDateTime doneTime;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 标记任务完成。
     */
    public void markDone() {
        if (status == EmployeeChangeTaskStatus.DONE) {
            throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_STATUS_CONFLICT, "该任务已完成");
        }
        status = EmployeeChangeTaskStatus.DONE;
        doneTime = LocalDateTime.now();
    }
}
