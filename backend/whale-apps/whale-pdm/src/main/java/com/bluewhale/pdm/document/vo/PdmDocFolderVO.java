package com.bluewhale.pdm.document.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 图文档目录视图（树形）
 *
 * @author BlueWhale
 */
@Data
public class PdmDocFolderVO {

    /** 目录ID */
    private Long id;

    /** 上级目录ID */
    private Long parentId;

    /** 目录名称 */
    private String name;

    /** 目录路径 */
    private String path;

    /** 目录类型 */
    private String folderType;

    /** 排序号 */
    private Integer sortNo;

    /** 备注 */
    private String remark;

    /** 子目录 */
    private List<PdmDocFolderVO> children = new ArrayList<>();
}
