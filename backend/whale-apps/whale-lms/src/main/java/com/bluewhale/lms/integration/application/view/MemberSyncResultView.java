package com.bluewhale.lms.integration.application.view;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 员工目录同步结果视图。
 *
 * <p>分列 {@code created} / {@code updated} 而不是只给一个总数：同步出问题时
 * 「首次全量」与「增量刷新」的判断依据完全不同——两者都算成功，
 * 但 created 突然等于总人数说明快照被清空过。</p>
 *
 * @author BlueWhale
 */
@Data
public class MemberSyncResultView {

    /** 同步时刻 */
    private LocalDateTime syncedAt;

    /** 源头总条数（ERP 报告的总数） */
    private long sourceTotal;

    /** 新增条数 */
    private int created;

    /** 更新条数 */
    private int updated;

    /** 跳过条数（缺少员工编号等无效行） */
    private int skipped;

    /** 完成后快照总条数 */
    private long snapshotTotal;
}
