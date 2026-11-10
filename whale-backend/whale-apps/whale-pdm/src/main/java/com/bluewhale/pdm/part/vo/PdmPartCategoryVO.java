package com.bluewhale.pdm.part.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 物料分类视图
 *
 * @author BlueWhale
 */
@Data
public class PdmPartCategoryVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 上级分类ID */
    private Long parentId;

    /** 分类编码 */
    private String code;

    /** 分类名称 */
    private String name;

    /** 分类路径 */
    private String path;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    private String remark;

    /** 子分类 */
    private List<PdmPartCategoryVO> children = new ArrayList<>();
}
