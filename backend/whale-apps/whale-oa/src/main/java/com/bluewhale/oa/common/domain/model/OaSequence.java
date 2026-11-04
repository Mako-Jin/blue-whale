package com.bluewhale.oa.common.domain.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 业务序列号聚合。
 *
 * <p>按「租户 + 业务类型 + 期间」维护当前序号，递增由数据库唯一键保证并发安全。</p>
 *
 * @author BlueWhale
 */
@Data
public class OaSequence {

    /** 主键 */
    private Long id;

    /** 业务类型 */
    private String bizType;

    /** 期间 yyyyMM */
    private String period;

    /** 当前序号 */
    private long currentValue;

    /** 版本号 */
    private long version;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
