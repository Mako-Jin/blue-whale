package com.bluewhale.contract.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 发起签署时的单个签署任务。
 *
 * @author BlueWhale
 */
@Data
public class SignTaskCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 签署人用户ID（外部相对方可为空） */
    private Long signerUserId;

    /** 签署方：A/B/OTHER */
    private String signerParty;

    /** 签署人姓名 */
    private String signerName;

    /** 关联工作流任务ID（三方签署完成后由内网接口推进，可空） */
    private Long workflowTaskId;
}
