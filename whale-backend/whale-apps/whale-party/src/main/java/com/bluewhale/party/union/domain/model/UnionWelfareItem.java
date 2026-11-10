package com.bluewhale.party.union.domain.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 节日福利发放明细（领域模型）。
 *
 * <p>明细在发放时生成，员工编号、姓名与金额都是快照：会员后续改名或调岗，
 * 已发放名单仍要还原当时的账目。</p>
 *
 * @author BlueWhale
 */
@Data
public class UnionWelfareItem {

    /** 主键 */
    private Long id;

    /** 福利批次 ID */
    private Long welfareId;

    /** 工会会员 ID */
    private Long unionMemberId;

    /** 员工编号（发放时快照） */
    private String employeeNo;

    /** 会员姓名（发放时快照） */
    private String memberName;

    /** 发放金额（标准快照） */
    private BigDecimal amount;

    /** 签收状态 */
    private WelfareSignStatus signStatus;

    /** 签收时间 */
    private LocalDateTime signedAt;

    /** 签收操作人用户 ID */
    private Long signedBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 签收。
     *
     * <p>已签收再签直接返回、不改写签收时间：重复点击不应让签收时点漂移。</p>
     *
     * @param actorId 签收操作人用户 ID
     */
    public void sign(Long actorId) {
        if (signStatus == WelfareSignStatus.SIGNED) {
            return;
        }
        signStatus = WelfareSignStatus.SIGNED;
        signedAt = LocalDateTime.now();
        signedBy = actorId;
    }

    /**
     * 是否已签收。
     *
     * @return 已签收返回 true
     */
    public boolean isSigned() {
        return signStatus == WelfareSignStatus.SIGNED;
    }
}
