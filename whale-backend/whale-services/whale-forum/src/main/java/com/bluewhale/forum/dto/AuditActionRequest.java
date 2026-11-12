package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 审核动作请求（通过/驳回共用）。
 *
 * @author BlueWhale
 */
@Data
public class AuditActionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 审核意见（驳回必填，通过可空） */
    private String remark;

    /** 对象类型：THREAD / POST（缺省 THREAD） */
    private String targetType;

    /** 对象ID */
    private Long targetId;
}
