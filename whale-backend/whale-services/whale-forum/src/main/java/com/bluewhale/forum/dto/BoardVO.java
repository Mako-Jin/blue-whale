package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 版块视图对象（扁平结构，前端按 {@code parentId} 组树）。
 *
 * @author BlueWhale
 */
@Data
public class BoardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 版块ID */
    private Long id;

    /** 上级版块ID（根为 null） */
    private Long parentId;

    /** 版块编码 */
    private String code;

    /** 版块名称 */
    private String name;

    /** 版块简介 */
    private String description;

    /** 图标标识 */
    private String icon;

    /** 可见性：ALL / ROLE / DEPT */
    private String visibility;

    /** 审核模式：0 先发后审 / 1 先审后发 */
    private Integer auditMode;

    /** 发帖策略：OPEN / READONLY / NOTICE_ONLY */
    private String postPolicy;

    /** 同级排序号 */
    private Integer sortNo;

    /** 状态：0 停用 / 1 启用 */
    private Integer status;
}
