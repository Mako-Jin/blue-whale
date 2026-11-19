package com.bluewhale.workflow.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 催办请求。
 *
 * @author BlueWhale
 */
@Data
public class UrgeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 指定任务ID（可选；为空表示实例级催办） */
    private Long taskId;

    /** 催办人账号（服务端以 X-Username 覆盖，请求体值不采信；最长 64） */
    @Size(max = 64, message = "{validation.workflow.urge.urger-account-size}")
    private String urger;

    /** 被催办人账号（可选；为空表示全部待办） */
    @Size(max = 64, message = "{validation.workflow.urge.urged-account-size}")
    private String targetAccount;

    /** 催办说明（可选） */
    @Size(max = 255, message = "{validation.workflow.urge.comment-size}")
    private String comment;
}
