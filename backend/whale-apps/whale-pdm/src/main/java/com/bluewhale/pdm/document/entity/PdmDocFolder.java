package com.bluewhale.pdm.document.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PDM 图文档目录
 *
 * <p>目录按树形组织，{@code path} 保存从根目录到当前目录的路径（如 {@code /1/2/}），
 * 用于 ACL 继承与子树查询。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_doc_folder")
public class PdmDocFolder extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 上级目录ID，0 表示根目录 */
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
}
