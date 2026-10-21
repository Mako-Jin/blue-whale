package com.bluewhale.workflow.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 审批动作请求（同意/驳回共用）。
 *
 * @author BlueWhale
 */
@Data
public class ApprovalRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 审批意见（可选，最长 255） */
    @Size(max = 255, message = "审批意见长度不能超过 255")
    private String comment;

    /** 本次审批追加的流程变量（可与已有变量合并，供后续条件选路使用） */
    private Map<String, Object> variables;
}
