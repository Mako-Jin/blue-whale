package com.bluewhale.lms.integration.domain.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * ERP 员工目录分页结果。
 *
 * @author BlueWhale
 */
@Data
public class ErpDirectoryPage {

    /** 总条数 */
    private long total;

    /** 当前页条目 */
    private List<ErpEmployeeEntry> records = new ArrayList<>();

    /**
     * 是否还有下一页。
     *
     * @param current 当前页码（从 1 起）
     * @param size 每页条数
     * @return 有下一页返回 {@code true}
     */
    public boolean hasNext(long current, long size) {
        return current * Math.max(size, 1) < total;
    }
}
