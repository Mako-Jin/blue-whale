package com.bluewhale.oa.culture.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 建议回复。
 *
 * @author BlueWhale
 */
@Data
public class SuggestionReply {

    /** 主键ID */
    private Long id;

    /** 建议ID */
    private Long suggestionId;

    /** 回复人用户ID */
    private Long replierId;

    /** 回复人姓名快照 */
    private String replierName;

    /** 回复内容 */
    private String content;

    /** 创建时间 */
    private LocalDateTime createTime;
}
