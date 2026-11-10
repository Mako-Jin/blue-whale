package com.bluewhale.archive.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 归档规则视图。
 *
 * @author BlueWhale
 */
@Data
public class ArchiveRuleVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 规则ID */
    private String id;

    /** 流程定义编码 */
    private String definitionCode;

    /** 归档门类ID */
    private String categoryId;

    /** 归档门类名称（联表补全） */
    private String categoryName;

    /** 保管期限（年；空表示长期） */
    private Integer retentionYears;

    /** 密级：1 公开 / 2 内部 / 3 秘密 */
    private Integer securityLevel;

    /** 是否自动归档：1 是 / 0 否 */
    private Integer autoArchive;

    /** 是否启用：1 启用 / 0 停用 */
    private Integer enabled;

    /** 创建时间 */
    private LocalDateTime createTime;
}
