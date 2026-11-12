package com.bluewhale.forum.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 论坛版块实体，对应表 {@code fm_board}。
 *
 * <p>三级结构由 {@code parentId} 自关联（大区 → 版块 → 子版）。
 * {@code code} 在租户内唯一，软删时必须改写为「原编码#D{id}」，否则同名版块无法重建。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fm_board")
public class ForumBoard extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户ID（行级隔离；插件写入时也会注入） */
    private Long tenantId;

    /** 上级版块ID（NULL 为根/大区） */
    private Long parentId;

    /** 版块编码（租户内唯一） */
    private String code;

    /** 版块名称 */
    private String name;

    /** 版块简介 */
    private String description;

    /** 图标标识（前端映射） */
    private String icon;

    /** 可见性：ALL / ROLE / DEPT */
    private String visibility;

    /** 可见角色码，逗号分隔（visibility=ROLE 生效） */
    private String visibleRoles;

    /** 可见部门ID，逗号分隔（visibility=DEPT 生效） */
    private String visibleDepts;

    /** 审核模式：0 先发后审 / 1 先审后发 */
    private Integer auditMode;

    /** 发帖策略：OPEN / READONLY / NOTICE_ONLY */
    private String postPolicy;

    /** 同级排序号（升序） */
    private Integer sortNo;

    /** 状态：0 停用 / 1 启用 */
    private Integer status;
}
