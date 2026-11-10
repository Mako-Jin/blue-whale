package com.bluewhale.crm.marketing.application.view;

import com.bluewhale.crm.marketing.domain.model.NurtureTask;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 客户培育任务视图。
 *
 * @author BlueWhale
 */
@Data
public class NurtureTaskView {

    /** 任务 ID */
    private Long id;

    /** 任务编号 */
    private String taskNo;

    /** 客户 ID */
    private Long customerId;

    /** 客户名称 */
    private String customerName;

    /** 线索 ID */
    private Long leadId;

    /** 内容文档 ID */
    private Long contentDocId;

    /** 内容文档版本 */
    private Integer contentDocVersion;

    /** 渠道 */
    private String channel;

    /** 计划日期 */
    private LocalDate planDate;

    /** 状态 */
    private String status;

    /** 执行人 ID */
    private Long assigneeId;

    /** 执行人姓名 */
    private String assigneeName;

    /** 备注 */
    private String note;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 由聚合构建视图。
     *
     * @param task 任务聚合
     * @return 视图
     */
    public static NurtureTaskView from(NurtureTask task) {
        NurtureTaskView view = new NurtureTaskView();
        view.setId(task.getId());
        view.setTaskNo(task.getTaskNo());
        view.setCustomerId(task.getCustomerId());
        view.setCustomerName(task.getCustomerName());
        view.setLeadId(task.getLeadId());
        view.setContentDocId(task.getContentDocId());
        view.setContentDocVersion(task.getContentDocVersion());
        view.setChannel(task.getChannel());
        view.setPlanDate(task.getPlanDate());
        view.setStatus(task.getStatus() == null ? null : task.getStatus().name());
        view.setAssigneeId(task.getAssigneeId());
        view.setAssigneeName(task.getAssigneeName());
        view.setNote(task.getNote());
        view.setOwnerId(task.getOwnerId());
        view.setDeptId(task.getDeptId());
        view.setCreateTime(task.getCreateTime());
        view.setUpdateTime(task.getUpdateTime());
        return view;
    }
}
