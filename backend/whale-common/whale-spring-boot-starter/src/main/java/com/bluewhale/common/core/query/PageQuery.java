package com.bluewhale.common.core.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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

    /** 当前页码 */
    private long current = 1;

    /** 每页条数 */
    private long size = 10;

    /**
     * 转换为 MyBatis-Plus 分页对象
     */
    public <T> Page<T> toPage() {
        return new Page<>(current, size);
    }
}
