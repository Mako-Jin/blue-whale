package com.bluewhale.lms.analytics.application.view;

import lombok.Data;

/**
 * 快照刷新结果。
 *
 * @author BlueWhale
 */
@Data
public class RefreshResultView {

    /** 统计日 */
    private String statDate;

    /** 是否新建（false 表示覆盖既有快照） */
    private boolean created;

    /** 刷新后的快照 */
    private MetricSnapshotView snapshot;
}
