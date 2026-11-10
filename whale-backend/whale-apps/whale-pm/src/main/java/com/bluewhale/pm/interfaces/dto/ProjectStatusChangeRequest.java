package com.bluewhale.pm.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 项目状态变更请求（ACTIVE / ARCHIVED）
 *
 * @author BlueWhale
 */
@Data
public class ProjectStatusChangeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标状态：ACTIVE 进行中 / ARCHIVED 已归档 */
    @NotBlank(message = "项目状态不能为空")
    @Pattern(regexp = "ACTIVE|ARCHIVED", message = "项目状态仅支持 ACTIVE / ARCHIVED")
    private String status;
}
