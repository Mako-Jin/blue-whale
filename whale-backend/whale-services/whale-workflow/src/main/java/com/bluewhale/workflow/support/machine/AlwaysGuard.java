package com.bluewhale.workflow.support.machine;

import com.bluewhale.workflow.support.ProcessModelFlow;

/**
 * 恒真守卫：任何流转均接受（v1 用于非默认流转）。
 *
 * @author BlueWhale
 */
public final class AlwaysGuard implements Guard {

    /** 单例 */
    public static final AlwaysGuard INSTANCE = new AlwaysGuard();

    /** 私有构造：单例复用 */
    private AlwaysGuard() {
    }

    @Override
    public boolean accept(GuardContext context, ProcessModelFlow flow) {
        return true;
    }
}
