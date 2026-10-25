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
    @NotNull(message = "目标工作项ID不能为空")
    private Long targetItemId;

    /** 关联类型：BLOCKS / BLOCKED_BY / RELATES / DUPLICATES / DUPLICATED_BY */
    @NotBlank(message = "关联类型不能为空")
    @Pattern(regexp = "BLOCKS|BLOCKED_BY|RELATES|DUPLICATES|DUPLICATED_BY",
            message = "关联类型仅支持 BLOCKS / BLOCKED_BY / RELATES / DUPLICATES / DUPLICATED_BY")
    private String linkType;
}
