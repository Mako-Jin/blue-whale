package com.bluewhale.mall.catalog.application.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 类目保存命令。
 *
 * @author BlueWhale
 */
@Data
public class CategorySaveCommand {

    /** 上级类目 ID，0 或空为一级 */
    private Long parentId;

    /** 类目名称 */
    @NotBlank(message = "类目名称不能为空")
    @Size(max = 64, message = "类目名称不得超过 64 字符")
    private String categoryName;

    /** 排序号 */
    private Integer sortNo;

    /** 状态：ACTIVE / DISABLED */
    private String status;

    /** 备注 */
    @Size(max = 255, message = "备注不得超过 255 字符")
    private String remark;
}
