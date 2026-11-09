package com.bluewhale.mall.catalog.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 商城会员实体，对应表 {@code mall_member}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_member")
public class MallMemberEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 平台用户 ID */
    private Long userId;

    /** 会员号（可空） */
    private String memberNo;

    /** 会员等级 */
    private String levelCode;

    /** 有效期至（空 = 长期） */
    private LocalDateTime validTo;

    /** 状态：ACTIVE / DISABLED */
    private String status;

    /** 备注 */
    private String remark;
}
