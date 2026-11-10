package com.bluewhale.party.integration.application.view;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工快照同步结果（M-P4）。
 *
 * @author BlueWhale
 */
@Data
public class EmployeeSyncResultView {

    /** 同步时刻 */
    private LocalDateTime syncedAt;

    /** 源头返回的总条数 */
    private long sourceTotal;

    /** 新增条数 */
    private int created;

    /** 更新条数 */
    private int updated;

    /** 跳过条数（脏数据） */
    private int skipped;

    /** 同步后在册员工数（覆盖率分母） */
    private long activeTotal;
}
