package com.bluewhale.oa.culture.domain.model;

import lombok.Data;

/**
 * 投票记录（多选时同一用户对应多行）。
 *
 * @author BlueWhale
 */
@Data
public class PollVote {

    /** 主键ID */
    private Long id;

    /** 投票ID */
    private Long pollId;

    /** 选项ID */
    private Long optionId;

    /** 投票人用户ID */
    private Long userId;

    /** 投票人姓名快照 */
    private String userName;
}
