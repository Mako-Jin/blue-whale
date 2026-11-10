package com.bluewhale.pdm.document.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * PDM 图文档访问控制规则
 *
 * <p>资源为目录或文档；主体为全体、用户或角色。
 * {@code subject_id} 为字符串：{@code ALL} 固定 {@code 0}，
 * {@code USER} 为用户 ID 文本，{@code ROLE} 为角色 code。
 * 显式 {@code DENY} 优先于 {@code ALLOW}。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pdm_doc_acl")
public class PdmDocAcl extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 资源类型：FOLDER/DOCUMENT */
    private String resourceType;

    /** 资源 ID */
    private Long resourceId;

    /** 主体类型：USER/ROLE/ALL */
    private String subjectType;

    /** 主体标识：用户 ID / 角色 code / ALL 时为 0 */
    private String subjectId;

    /** 动作：VIEW/EDIT/DOWNLOAD/DELETE/MANAGE */
    private String action;

    /** 效果：ALLOW/DENY */
    private String effect;
}
