package com.bluewhale.common.core.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 统一分页响应体
 *
 * @author BlueWhale
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据 */
    private List<T> records;

    /** 总条数 */
    private long total;

    /** 每页条数 */
    private long size;

    /** 当前页码 */
    private long current;

    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        PageResult<T> pageResult = new PageResult<>();
        pageResult.setRecords(records);
        pageResult.setTotal(total);
        pageResult.setCurrent(current);
        pageResult.setSize(size);
        return pageResult;
    }

    public static <T> PageResult<T> of(IPage<T> page) {
        return of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }
}
