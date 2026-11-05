package com.bluewhale.mes.workforce.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 月度绩效锁定结果视图
 *
 * @author BlueWhale
 */
@Data
public class MesPerformanceLockVO {

    /** 期间编码 */
    private String periodCode;

    /** 本次锁定条数 */
    private int lockedCount;

    /** 已锁定的条数 */
    private int alreadyLockedCount;

    /** 锁定时间 */
    private LocalDateTime lockedAt;
}
