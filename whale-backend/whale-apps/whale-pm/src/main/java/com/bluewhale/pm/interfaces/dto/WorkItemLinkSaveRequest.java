package com.bluewhale.pm.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * 工作项关联新增请求
 *
 * @author BlueWhale
 */
@Data
public class WorkItemLinkSaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标工作项ID */
    @NotNull(message = "{validation.pm.work-item-link.target-item-id-required}")
    private Long targetItemId;

    /** 关联类型：BLOCKS / BLOCKED_BY / RELATES / DUPLICATES / DUPLICATED_BY */
    @NotBlank(message = "{validation.pm.work-item-link.link-type-required}")
    @Pattern(regexp = "BLOCKS|BLOCKED_BY|RELATES|DUPLICATES|DUPLICATED_BY",
            message = "{validation.pm.work-item-link.link-type-pattern}")
    private String linkType;
}
