package com.bluewhale.video.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 视频分类（树形，编码在租户内唯一）。
 *
 * @author BlueWhale
 */
@Data
public class Category {

    /** 主键 */
    private Long id;

    /** 父分类 ID，0 表示顶级 */
    private Long parentId;

    /** 分类名称 */
    private String name;

    /** 分类编码（租户内唯一） */
    private String code;

    /** 适用范围：ALL / SHORT_VIDEO / VIDEO / MOVIE */
    private String contentTypeScope;

    /** 排序 */
    private Integer sortNo;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 判断分类是否可用于指定内容类型。
     *
     * @param type 内容类型
     * @return 通用或范围匹配返回 {@code true}
     */
    public boolean appliesTo(ContentType type) {
        return "ALL".equals(contentTypeScope) || (type != null && type.name().equals(contentTypeScope));
    }
}
