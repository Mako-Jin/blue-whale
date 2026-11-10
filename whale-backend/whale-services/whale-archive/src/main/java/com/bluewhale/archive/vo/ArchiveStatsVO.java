package com.bluewhale.archive.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 档案统计视图。
 *
 * @author BlueWhale
 */
@Data
public class ArchiveStatsVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 条目总数 */
    private Long total;

    /** 待整理数量 */
    private Long pending;

    /** 已归档数量 */
    private Long archived;

    /** 已销毁数量 */
    private Long destroyed;

    /** 按年度分布（年度 → 数量） */
    private Map<String, Long> byYear;

    /** 按密级分布（密级 → 数量） */
    private Map<String, Long> bySecurity;

    /** 按门类分布（门类ID → 数量） */
    private Map<String, Long> byCategory;
}
