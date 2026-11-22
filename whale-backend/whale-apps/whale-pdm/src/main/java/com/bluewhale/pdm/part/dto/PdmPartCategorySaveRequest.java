package com.bluewhale.pdm.part.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * 物料分类新增/更新请求
 *
 * @author BlueWhale
 */
@Data
public class PdmPartCategorySaveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 上级分类ID，0 或空表示根 */
    private Long parentId;

    /** 分类编码（租户内唯一） */
    @NotBlank(message = "{validation.pdm.pdm-part-category.code-required}")
    @Size(max = 64, message = "{validation.pdm.pdm-part-category.code-size}")
    private String code;

    /** 分类名称 */
    @NotBlank(message = "{validation.pdm.pdm-part-category.name-required}")
    @Size(max = 128, message = "{validation.pdm.pdm-part-category.name-size}")
    private String name;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    @Size(max = 255, message = "{validation.pdm.pdm-part-category.remark-size}")
    private String remark;
}
