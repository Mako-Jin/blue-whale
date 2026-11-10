package com.bluewhale.oa.administrative.domain.model;

import com.bluewhale.oa.common.OaErrorCode;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用章申请聚合根。
 *
 * <p>用印登记仅允许 {@code APPROVED} 且未登记过；同一用章单只能登记一次。</p>
 *
 * @author BlueWhale
 */
@Data
public class SealUse {

    /** 主键ID */
    private Long id;

    /** 用章单号 */
    private String code;

    /** 申请人账号 */
    private String applicant;

    /** 申请人用户ID */
    private Long applicantId;

    /** 申请部门ID */
    private Long deptId;

    /** 印章ID（Contract 主数据） */
    private Long sealId;

    /** 印章名称快照 */
    private String sealName;

    /** 用章事由 */
    private String reason;

    /** 用印文件数 */
    private Integer fileCount;

    /** 用印时间 */
    private LocalDateTime useTime;

    /** 状态 */
    private String status;

    /** 用印登记时间 */
    private LocalDateTime registeredTime;

    /** 用印登记人用户ID */
    private Long registeredBy;

    /** 审批实例ID */
    private Long approvalInstanceId;

    /** 备注 */
    private String remark;

    /** 用章文件 */
    private List<SealUseFile> files = new ArrayList<>();

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 变更状态：终态不可回流，重复终态幂等返回 false。
     *
     * @param target 目标状态
     * @return 是否发生变更
     */
    public boolean changeStatus(SealUseStatus target) {
        SealUseStatus current = SealUseStatus.of(status);
        if (current == target) {
            return false;
        }
        if (current != SealUseStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.SEAL_STATUS_CONFLICT, "用章申请已处于终态，不可再次变更");
        }
        if (target == SealUseStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.SEAL_STATUS_CONFLICT, "用章申请不能回到待审批状态");
        }
        this.status = target.name();
        return true;
    }

    /**
     * 校验是否可编辑。
     */
    public void assertEditable() {
        if (SealUseStatus.of(status) != SealUseStatus.PENDING) {
            throw OaErrorCode.exception(OaErrorCode.SEAL_STATUS_CONFLICT, "仅待审批的用章申请可编辑");
        }
    }

    /**
     * 用印登记。
     *
     * @param operatorId 登记人用户ID
     */
    public void register(Long operatorId) {
        if (SealUseStatus.of(status) != SealUseStatus.APPROVED) {
            throw OaErrorCode.exception(OaErrorCode.SEAL_STATUS_CONFLICT, "仅审批通过的用章申请可登记");
        }
        if (registeredTime != null) {
            throw OaErrorCode.exception(OaErrorCode.SEAL_ALREADY_REGISTERED, "该用章申请已登记");
        }
        this.registeredTime = LocalDateTime.now();
        this.registeredBy = operatorId;
        if (useTime == null) {
            this.useTime = registeredTime;
        }
    }
}
