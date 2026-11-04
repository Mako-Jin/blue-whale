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
    @NotBlank(message = "分类编码不能为空")
    @Size(max = 64, message = "分类编码长度不能超过 64")
    private String code;

    /** 分类名称 */
    @NotBlank(message = "分类名称不能为空")
    @Size(max = 128, message = "分类名称长度不能超过 128")
    private String name;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
