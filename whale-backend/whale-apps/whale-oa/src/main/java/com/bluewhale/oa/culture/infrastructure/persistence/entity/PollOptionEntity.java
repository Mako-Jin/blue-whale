package com.bluewhale.oa.culture.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 投票选项实体，对应表 {@code oa_poll_option}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_poll_option")
public class PollOptionEntity extends BaseEntity {

    private Long pollId;

    private String optionText;

    private Integer sortNo;
}
