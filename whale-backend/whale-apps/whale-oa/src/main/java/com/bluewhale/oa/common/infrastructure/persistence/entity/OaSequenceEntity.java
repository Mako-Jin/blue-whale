package com.bluewhale.oa.common.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 业务序列号实体，对应表 {@code oa_sequence}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_sequence")
public class OaSequenceEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 业务类型 */
    private String bizType;

    /** 期间 yyyyMM */
    private String period;

    /** 当前序号 */
    private Long currentValue;

    /** 版本号 */
    private Long version;
}
