package com.bluewhale.forum.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 敏感词词条视图（管理端列表用）。
 *
 * <p>不让 controller 直接返回实体：实体带 {@code tenantId}/{@code deleted} 等内部字段，
 * 直出会把租户结构泄露给前端，也破坏「controller 不依赖 entity」的分层约束。</p>
 *
 * @author BlueWhale
 */
@Data
public class SensitiveWordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 词条ID */
    private Long id;

    /** 敏感词 */
    private String word;

    /** 动作：BLOCK / REPLACE / PENDING */
    private String action;

    /** 分类 */
    private String category;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
