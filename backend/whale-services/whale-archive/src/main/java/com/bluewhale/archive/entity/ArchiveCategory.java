package com.bluewhale.archive.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 档案门类实体，对应表 {@code archive_category}。
 *
 * <p>门类构成分类树，{@code parentId=0} 表示根节点；{@code code} 业务唯一，
 * 作为档号组成段（{@code BW-{code}-{year}-{seq}}）。</p>
 *
 * @author BlueWhale
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("archive_category")
public class ArchiveCategory extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 门类编码（业务唯一，大写字母/数字/下划线） */
    private String code;

    /** 门类名称 */
    private String name;

    /** 上级门类ID（0 表示根节点） */
    private Long parentId;

    /** 层级（根节点为 1） */
    private Integer level;

    /** 默认保管期限（年；空表示长期） */
    private Integer retentionYears;

    /** 密级：1 公开 / 2 内部 / 3 秘密 */
    private Integer securityLevel;

    /** 排序号（同级升序） */
    private Integer sort;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;
}
