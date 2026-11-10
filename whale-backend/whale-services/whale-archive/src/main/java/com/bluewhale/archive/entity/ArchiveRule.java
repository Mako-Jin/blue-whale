package com.bluewhale.archive.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 归档规则实体，对应表 {@code archive_rule}。
 *
 * <p>一条规则把一个工作流定义编码映射到归档门类；{@code autoArchive=1} 时命中即自动归档，
 * 否则档案条目进入「待整理」由人工确认。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("archive_rule")
public class ArchiveRule extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 工作流定义编码（业务唯一） */
    private String definitionCode;

    /** 归档门类ID */
    private Long categoryId;

    /** 保管期限（年；空表示长期） */
    private Integer retentionYears;

    /** 密级：1 公开 / 2 内部 / 3 秘密 */
    private Integer securityLevel;

    /** 是否自动归档：1 是 / 0 否 */
    private Integer autoArchive;

    /** 是否启用：1 启用 / 0 停用 */
    private Integer enabled;
}
