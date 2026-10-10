package com.bluewhale.sts.track.clientevent;

import lombok.Getter;
import lombok.Setter;

/**
 * 「事件名 → 条数」聚合行。
 *
 * <p>用普通 POJO 而非 record：MyBatis 对 record 的构造器映射依赖编译期 {@code -parameters}
 * 与较高版本，属于"能跑但不稳"的用法。这个类只有一个用途、字段固定，
 * 用 POJO 换来的是在任何版本的 MyBatis 上都确定可用。
 */
@Getter
@Setter
public class ClientEventNameCount {

    /** 事件名，对应 {@code event_name} 列 */
    private String eventName;

    /** 条数，对应 {@code event_name_count} 列 */
    private Long eventNameCount;

}
