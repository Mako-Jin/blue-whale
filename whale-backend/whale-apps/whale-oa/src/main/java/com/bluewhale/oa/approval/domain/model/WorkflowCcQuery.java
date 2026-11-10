package com.bluewhale.oa.approval.domain.model;

import lombok.Data;

import java.util.List;

/**
 * Workflow 抄送查询条件。
 *
 * @author BlueWhale
 */
@Data
public class WorkflowCcQuery {

    /** 流程实例ID */
    private Long instanceId;

    /** 收件人（单值） */
    private String recipient;

    /** 候选收件人（非空时优先于单值） */
    private List<String> recipientCandidates;

    /** 已读标记 */
    private Integer readFlag;

    /** 页码 */
    private long current = 1;

    /** 每页条数 */
    private long size = 10;
}
