package com.bluewhale.oa.culture.domain.model;

import lombok.Data;

/**
 * 投票选项。
 *
 * @author BlueWhale
 */
@Data
public class PollOption {

    /** 主键ID */
    private Long id;

    /** 投票ID */
    private Long pollId;

    /** 选项文本 */
    private String optionText;

    /** 排序号 */
    private Integer sortNo;

    /** 得票数（视图填充，非持久化字段） */
    private Long voteCount;
}
