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
    @NotBlank(message = "{validation.mall.category.category-name-required}")
    @Size(max = 64, message = "{validation.mall.category.category-name-size}")
    private String categoryName;

    /** 排序号 */
    private Integer sortNo;

    /** 状态：ACTIVE / DISABLED */
    private String status;

    /** 备注 */
    @Size(max = 255, message = "{validation.mall.category.remark-size}")
    private String remark;
}
