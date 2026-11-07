package com.bluewhale.crm.marketing.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 渠道伙伴实体，对应表 {@code crm_partner}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("crm_partner")
public class PartnerEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户 ID */
    private Long tenantId;

    /** 伙伴编码 */
    private String partnerCode;

    /** 伙伴名称 */
    private String partnerName;

    /** 伙伴类型 */
    private String partnerType;

    /** 伙伴等级 */
    private String level;

    /** 所属区域 */
    private String region;

    /** 联系人姓名 */
    private String contactName;

    /** 联系人电话 */
    private String contactPhone;

    /** 状态 */
    private String status;

    /** 负责人 */
    private Long ownerId;

    /** 归属部门 */
    private Long deptId;

    /** 乐观锁版本号 */
    @Version
    private Integer lockVersion;
}
