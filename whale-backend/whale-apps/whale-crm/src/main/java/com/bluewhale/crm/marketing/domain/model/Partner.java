package com.bluewhale.crm.marketing.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 渠道伙伴档案聚合。
 *
 * @author BlueWhale
 */
@Data
public class Partner {

    /** 主键 */
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 伙伴编码 */
    private String partnerCode;

    /** 伙伴名称 */
    private String partnerName;

    /** 伙伴类型 */
    private PartnerType partnerType;

    /** 伙伴等级 */
    private String level;

    /** 所属区域 */
    private String region;

    /** 联系人姓名 */
    private String contactName;

    /** 联系人电话 */
    private String contactPhone;

    /** 状态 */
    private PartnerStatus status;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 乐观锁版本 */
    private Integer lockVersion;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /**
     * 校验伙伴可用于报单。
     */
    public void requireEnabled() {
        if (status != PartnerStatus.ENABLED) {
            throw CrmErrorCode.exception(CrmErrorCode.PARTNER_STATE_CONFLICT, "渠道伙伴已停用，不允许报单");
        }
    }
}
