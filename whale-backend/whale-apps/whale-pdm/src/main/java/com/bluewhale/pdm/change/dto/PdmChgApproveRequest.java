package com.bluewhale.pdm.change.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 变更审批请求
 *
 * @author BlueWhale
 */
@Data
public class PdmChgApproveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 审批意见（驳回必填） */
    @Size(max = 500, message = "{validation.pdm.pdm-chg-approve.comment-size}")
    private String comment;
}
