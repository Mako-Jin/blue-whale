package com.bluewhale.pdm.document.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 图文档 ACL 规则视图
 *
 * @author BlueWhale
 */
@Data
public class PdmDocAclVO {

    /** 规则ID */
    private Long id;

    /** 资源类型：FOLDER/DOCUMENT */
    private String resourceType;

    /** 资源 ID */
    private Long resourceId;

    /** 主体类型：USER/ROLE/ALL */
    private String subjectType;

    /** 主体标识：用户 ID / 角色 code / ALL 为 0 */
    private String subjectId;

    /** 动作 */
    private String action;

    /** 效果 */
    private String effect;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
