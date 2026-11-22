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
    @NotNull(message = "{validation.pm.transition.from-status-id-required}")
    private Long fromStatusId;

    /** 目标状态ID */
    @NotNull(message = "{validation.pm.transition.to-status-id-required}")
    private Long toStatusId;

    /** 流转动作名称 */
    @NotBlank(message = "{validation.pm.transition.name-required}")
    @Size(max = 64, message = "{validation.pm.transition.name-size}")
    private String name;

    /** 是否要求填写评论 */
    private Boolean requireComment;

    /** 附加权限编码（可选） */
    @Size(max = 128, message = "{validation.pm.transition.require-permission-size}")
    private String requirePermission;
}
