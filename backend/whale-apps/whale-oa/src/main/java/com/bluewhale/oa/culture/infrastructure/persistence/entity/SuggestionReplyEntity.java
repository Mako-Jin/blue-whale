package com.bluewhale.oa.culture.infrastructure.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 建议回复实体，对应表 {@code oa_suggestion_reply}。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("oa_suggestion_reply")
public class SuggestionReplyEntity extends BaseEntity {

    private Long suggestionId;

    private Long replierId;

    private String replierName;

    private String content;
}
