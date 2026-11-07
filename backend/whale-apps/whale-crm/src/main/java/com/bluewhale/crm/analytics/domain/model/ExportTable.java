package com.bluewhale.crm.analytics.domain.model;

import lombok.Data;

import java.util.List;

/**
 * 导出数据表（只读查询端口返回的二维数据）。
 *
 * @author BlueWhale
 */
@Data
public class ExportTable {

    /** 表头 */
    private List<String> headers;

    /** 数据行 */
    private List<List<String>> rows;

    /** 实际数据行数（可能大于 maxRows，用于上限判断） */
    private int rowCount;

    /**
     * 是否超过单次导出行数上限。
     *
     * @param maxRows 上限
     * @return 超过返回 true
     */
    public boolean isOverLimit(int maxRows) {
        return rowCount > maxRows;
    }
}
