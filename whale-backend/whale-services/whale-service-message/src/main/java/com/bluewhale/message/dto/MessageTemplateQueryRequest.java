package com.bluewhale.message.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息模板分页查询条件。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MessageTemplateQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 模板编码（模糊匹配） */
    private String templateCode;

    /** 模板名称（模糊匹配） */
    private String templateName;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;
}
