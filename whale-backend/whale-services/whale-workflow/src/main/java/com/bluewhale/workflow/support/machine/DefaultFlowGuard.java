package com.bluewhale.workflow.support.machine;

import com.bluewhale.workflow.support.ProcessModelFlow;

/**
 * 默认流转守卫：仅 {@code isDefault=true} 的流转被接受（排他网关多分支兜底）。
 *
 * @author BlueWhale
 */
public final class DefaultFlowGuard implements Guard {

    /** 单例 */
    public static final DefaultFlowGuard INSTANCE = new DefaultFlowGuard();

    /** 私有构造：单例复用 */
    private DefaultFlowGuard() {
    }

    @Override
    public boolean accept(GuardContext context, ProcessModelFlow flow) {
        return flow != null && Boolean.TRUE.equals(flow.getIsDefault());
    }
}
