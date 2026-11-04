package com.bluewhale.workflow.dto;

import jakarta.validation.constraints.NotBlank;
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

    /** 催办人账号（必填） */
    @NotBlank(message = "催办人不能为空")
    @Size(max = 64, message = "催办人账号长度不能超过 64")
    private String urger;

    /** 被催办人账号（可选；为空表示全部待办） */
    @Size(max = 64, message = "被催办人账号长度不能超过 64")
    private String targetAccount;

    /** 催办说明（可选） */
    @Size(max = 255, message = "催办说明长度不能超过 255")
    private String comment;
}
