package com.bluewhale.workflow.dto;

import com.bluewhale.common.core.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 抄送分页查询请求。
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ProcessCcQueryRequest extends PageQuery {

    private static final long serialVersionUID = 1L;

    /** 流程实例ID（可选） */
    private Long instanceId;

    /** 收件人（账号或用户ID文本，可选） */
    private String recipient;

    /** 已读标记：0 未读 / 1 已读（可选） */
    private Integer readFlag;
}
