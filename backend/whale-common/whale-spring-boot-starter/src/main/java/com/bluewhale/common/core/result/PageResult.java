package com.bluewhale.common.core.result;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 统一分页响应体
 *
 * @param <T> 分页记录的数据类型
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

    /**
     * 构建分页响应体
     *
     * @param records 当前页数据
     * @param total 总条数
     * @param current 当前页码
     * @param size 每页条数
     * @return 分页响应体
     */
    public static <T> PageResult<T> of(List<T> records, long total, long current, long size) {
        PageResult<T> pageResult = new PageResult<>();
        pageResult.setRecords(records);
        pageResult.setTotal(total);
        pageResult.setCurrent(current);
        pageResult.setSize(size);
        return pageResult;
    }

    /**
     * 由 MyBatis-Plus 分页对象构建分页响应体
     *
     * @param page MyBatis-Plus 分页查询结果
     * @return 分页响应体
     */
    public static <T> PageResult<T> of(IPage<T> page) {
        return of(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }
}
