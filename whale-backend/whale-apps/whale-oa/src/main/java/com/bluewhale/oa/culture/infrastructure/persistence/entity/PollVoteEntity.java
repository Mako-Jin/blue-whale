package com.bluewhale.oa.culture.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 投票记录实体，对应表 {@code oa_poll_vote}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_poll_vote")
public class PollVoteEntity extends BaseEntity {

    private Long pollId;

    private Long optionId;

    private Long userId;

    private String userName;
}
