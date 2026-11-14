package com.bluewhale.common.core.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.io.Serializable;

/**
 * 通用分页查询参数
 *
 * @author BlueWhale
 */
@Data
public class PageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 单页最大条数：与各服务分页上限一致，防止深度分页拖垮数据库 */
    public static final long MAX_PAGE_SIZE = 200L;

    /** 当前页码（从 1 开始） */
    @Min(value = 1, message = "页码不能小于 1")
    private long current = 1;

    /** 每页条数（1..200） */
    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = MAX_PAGE_SIZE, message = "每页条数不能超过 200")
    private long size = 10;

    /**
     * 转换为 MyBatis-Plus 分页对象。
     *
     * <p>统一收口页码与页大小：页码小于 1 归一为 1，页大小限制在 1..{@value #MAX_PAGE_SIZE}。
     * 未显式使用 {@code @Valid} 的调用方也不会因缺省或异常入参触发全表扫描。</p>
     *
     * @param <T> 记录类型
     * @return 分页对象
     */
    public <T> Page<T> toPage() {
        long safeCurrent = Math.max(current, 1L);
        long safeSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        return new Page<>(safeCurrent, safeSize);
    }
}
