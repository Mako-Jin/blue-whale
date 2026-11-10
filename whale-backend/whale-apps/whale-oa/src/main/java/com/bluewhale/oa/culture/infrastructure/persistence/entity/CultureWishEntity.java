package com.bluewhale.oa.culture.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 祝福实体，对应表 {@code oa_culture_wish}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_culture_wish")
public class CultureWishEntity extends BaseEntity {

    private String wishType;

    private Long targetUserId;

    private String targetName;

    private String content;

    private Long senderId;

    private String senderName;

    private LocalDate wishDate;
}
