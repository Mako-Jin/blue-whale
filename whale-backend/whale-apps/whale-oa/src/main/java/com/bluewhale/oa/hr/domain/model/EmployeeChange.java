package com.bluewhale.oa.hr.domain.model;

import com.bluewhale.oa.common.OaErrorCode;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 人员异动单聚合。
 *
 * <p>状态机：草稿 → 审批中 → 审批通过/驳回 → 已执行/执行失败。审批通过后由服务调用
 * Auth 生命周期端点并同步员工档案，重复审批事件按幂等忽略；停用不删除账号。</p>
 *
 * @author BlueWhale
 */
@Data
public class EmployeeChange {

    /** 主键 */
    private Long id;

    /** 异动单号 */
    private String changeNo;

    /** 异动类型 */
    private EmployeeChangeType changeType;

    /** 目标用户ID（入职未建号时为空） */
    private Long userId;

    /** 员工姓名快照 */
    private String employeeName;

    /** 登录账号（入职建号时填写） */
    private String username;

    /** 原部门ID */
    private Long fromDeptId;

    /** 目标部门ID */
    private Long toDeptId;

    /** 原在职状态 */
    private String fromStatus;

    /** 目标在职状态 */
    private String toStatus;

    /** 生效日期 */
    private LocalDate effectiveDate;

    /** 异动原因 */
    private String reason;

    /** 状态 */
    private EmployeeChangeStatus status;

    /** 审批实例ID */
    private Long approvalInstanceId;

    /** 申请人用户ID */
    private Long applicantId;

    /** 申请人姓名快照 */
    private String applicantName;

    /** 执行人用户ID */
    private Long executorId;

    /** 执行人姓名快照 */
    private String executorName;

    /** 执行时间 */
    private LocalDateTime executedTime;

    /** 执行结果摘要 */
    private String executeResult;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验异动单基本约束。
     *
     * <p>类型、员工、生效日期、申请人必填；入职需已有用户或登录账号；
     * 非入职类必须关联用户；调岗必须给出目标部门且与原部门不同。</p>
     */
    public void ensureValid() {
        if (changeType == null) {
            throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_INVALID, "异动类型不能为空");
        }
        if (employeeName == null || employeeName.isBlank()) {
            throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_INVALID, "员工姓名不能为空");
        }
        if (effectiveDate == null) {
            throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_INVALID, "生效日期不能为空");
        }
        if (applicantId == null) {
            throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_INVALID, "申请人不能为空");
        }
        if (changeType == EmployeeChangeType.ONBOARD) {
            if (userId == null && (username == null || username.isBlank())) {
                throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_INVALID, "入职必须填写登录账号");
            }
        } else if (userId == null) {
            throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_INVALID,
                    changeType.label() + "必须关联员工用户");
        }
        if (changeType == EmployeeChangeType.TRANSFER) {
            if (toDeptId == null) {
                throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_INVALID, "调岗必须填写目标部门");
            }
            if (fromDeptId != null && fromDeptId.equals(toDeptId)) {
                throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_INVALID, "调岗目标部门不能与原部门相同");
            }
        }
    }

    /**
     * 校验当前状态可编辑。
     */
    public void requireEditable() {
        if (status == null || !status.editable()) {
            throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_STATUS_CONFLICT,
                    "仅草稿、已驳回或执行失败的异动单可编辑");
        }
    }

    /**
     * 校验当前状态可提交审批。
     */
    public void requireSubmittable() {
        if (status == null || !status.submittable()) {
            throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_STATUS_CONFLICT,
                    "仅草稿或已驳回的异动单可提交审批");
        }
    }

    /**
     * 标记为审批中。
     *
     * @param approvalInstanceId 审批实例ID
     */
    public void markSubmitted(Long approvalInstanceId) {
        status = EmployeeChangeStatus.SUBMITTED;
        this.approvalInstanceId = approvalInstanceId;
    }

    /**
     * 标记审批通过（待执行）。
     */
    public void markApproved() {
        status = EmployeeChangeStatus.APPROVED;
    }

    /**
     * 标记审批驳回。
     *
     * @param reason 驳回原因
     */
    public void markRejected(String reason) {
        status = EmployeeChangeStatus.REJECTED;
        executeResult = reason;
    }

    /**
     * 标记执行成功。
     *
     * @param executorId 执行人ID（系统自动执行时为空）
     * @param executorName 执行人姓名
     * @param result 执行结果摘要
     */
    public void markExecuted(Long executorId, String executorName, String result) {
        status = EmployeeChangeStatus.EXECUTED;
        this.executorId = executorId;
        this.executorName = executorName;
        executedTime = LocalDateTime.now();
        executeResult = truncate(result, 255);
    }

    /**
     * 标记执行失败，允许稍后重试。
     *
     * @param result 失败原因
     */
    public void markExecuteFailed(String result) {
        status = EmployeeChangeStatus.EXEC_FAILED;
        executeResult = truncate(result, 255);
    }

    /**
     * 四眼原则：申请人与执行人不能为同一人。
     *
     * @param operatorId 执行操作人ID
     */
    public void assertFourEyes(Long operatorId) {
        if (operatorId != null && applicantId != null && operatorId.equals(applicantId)) {
            throw OaErrorCode.exception(OaErrorCode.HR_CHANGE_FOUR_EYES);
        }
    }

    /**
     * 是否审批中。
     *
     * @return 审批中返回 true
     */
    public boolean isSubmitted() {
        return status == EmployeeChangeStatus.SUBMITTED;
    }

    /**
     * 是否已执行。
     *
     * @return 已执行返回 true
     */
    public boolean isExecuted() {
        return status == EmployeeChangeStatus.EXECUTED;
    }

    /**
     * 截断字符串到指定长度。
     *
     * @param value 原值
     * @param max 最大长度
     * @return 截断结果
     */
    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
