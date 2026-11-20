package com.bluewhale.workflow.request;

import com.bluewhale.common.core.query.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

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

    /** 收件人账号（可选，最长 64） */
    @Size(max = 64, message = "{validation.workflow.cc.recipient-account-size}")
    private String recipient;

    /** 候选收件人账号（可选，最多 100；非空时优先于 {@link #recipient}） */
    @Size(max = 100, message = "{validation.workflow.cc.candidate-limit}")
    private List<@Size(max = 64, message = "{validation.workflow.cc.recipient-account-size}") String> recipientCandidates;

    /** 已读标记：0 未读 / 1 已读（可选） */
    private Integer readFlag;
}
