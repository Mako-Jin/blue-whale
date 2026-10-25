package com.bluewhale.pm.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 状态流转新增请求
 *
 * @author BlueWhale
 */
@Data
public class TransitionSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 源状态ID */
    @NotNull(message = "源状态ID不能为空")
    private Long fromStatusId;

    /** 目标状态ID */
    @NotNull(message = "目标状态ID不能为空")
    private Long toStatusId;

    /** 流转动作名称 */
    @NotBlank(message = "流转动作名称不能为空")
    @Size(max = 64, message = "流转动作名称长度不能超过 64")
    private String name;

    /** 是否要求填写评论 */
    private Boolean requireComment;

    /** 附加权限编码（可选） */
    @Size(max = 128, message = "附加权限编码长度不能超过 128")
    private String requirePermission;
}
