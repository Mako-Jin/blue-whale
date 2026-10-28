package com.bluewhale.message.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息记录分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MessageRecordQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 业务类型（精确匹配） */
    private String bizType;

    /** 业务标识（精确匹配） */
    private String bizId;

    /** 消息状态（精确匹配） */
    private String status;

    /** 渠道（包含匹配，如 IM） */
    private String channel;
}
