package com.bluewhale.pdm.part.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 零件人工合并请求
 *
 * @author BlueWhale
 */
@Data
public class PdmPartMergeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 源零件ID（合并后停用） */
    @NotNull(message = "源零件不能为空")
    private Long sourceId;

    /** 保留零件ID（存活并承接 BOM 引用） */
    @NotNull(message = "保留零件不能为空")
    private Long targetId;

    /** 合并说明 */
    @Size(max = 200, message = "合并说明长度不能超过 200")
    private String remark;
}
