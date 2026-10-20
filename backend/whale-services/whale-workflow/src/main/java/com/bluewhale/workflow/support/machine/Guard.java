package com.bluewhale.workflow.support.machine;

import com.bluewhale.workflow.support.ProcessModelFlow;

/**
 * 迁移守卫（<b>状态机唯一扩展点</b>）：决定某条流转在当前上下文下是否可选。
 *
 * <p>v1 提供 {@link AlwaysGuard}（恒真）与 {@link DefaultFlowGuard}（仅默认流转为真）；
 * 后续增量可加入基于流程变量的守卫（如条件比较），无需改动内核。</p>
 *
 * @author BlueWhale
 */
@FunctionalInterface
public interface Guard {

    /**
     * 判定流转是否可选。
     *
     * @param context 守卫上下文
     * @param flow 待判定流转
     * @return 可选返回 {@code true}
     */
    boolean accept(GuardContext context, ProcessModelFlow flow);
}
