package com.bluewhale.sts.common.basic.model;

import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 分页出参。
 *
 * <p>字段名沿用前端分页组件的习惯（{@code records} / {@code total} / {@code page} / {@code size}），
 * 让前端拿到即用，不必再写一层适配。
 *
 * <p>{@code pages} 由服务端算好一并返回，而不是留给前端除一遍：总页数的取整方式（向上取整）
 * 是业务约定，两端各算一次迟早会不一致 —— 尤其是 {@code total} 为 0 这种边界。
 *
 * @param <T> 记录类型
 */
@Getter
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前页数据 */
    private final List<T> records;

    /** 满足条件的总条数 */
    private final long total;

    /** 当前页码，从 1 开始 */
    private final long page;

    /** 当前每页条数（经钳制后的实际值，可能与请求值不同） */
    private final long size;

    /** 总页数 */
    private final long pages;

    private PageResult(List<T> records, long total, long page, long size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
        this.pages = size <= 0L ? 0L : (total + size - 1L) / size;
    }

    public static <T> PageResult<T> of(List<T> records, long total, long page, long size) {
        return new PageResult<>(records, total, page, size);
    }

    /** 空结果，用于提前返回的场景（如查询条件互斥、无权限） */
    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(List.of(), 0L, page, size);
    }

}
