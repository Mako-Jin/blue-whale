package com.bluewhale.sts.log.clientlog;

import lombok.Getter;
import lombok.Setter;

/**
 * 「级别 → 条数」聚合行。
 *
 * <p>用普通 POJO 而非 record：MyBatis 对 record 的构造器映射依赖编译期 {@code -parameters}
 * 与较高版本，属于"能跑但不稳"的用法。这个类只有一个用途、字段固定，
 * 用 POJO 换来的是在任何版本的 MyBatis 上都确定可用。
 */
@Getter
@Setter
public class ClientLogLevelCount {

    /** 级别（小写） */
    private String level;

    /** 条数，对应 {@code level_count} 列 */
    private Long levelCount;

}
