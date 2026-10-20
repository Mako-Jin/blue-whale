package com.bluewhale.workflow.support.machine;

import com.bluewhale.workflow.support.ProcessModelFlow;
import lombok.Getter;

/**
 * 迁移：一条流转 + 其守卫（守卫为空视为 {@link AlwaysGuard}）。
 *
 * @author BlueWhale
 */
@Getter
public class Transition {

    /** 流转 */
    private final ProcessModelFlow flow;

    /** 守卫 */
    private final Guard guard;

    /**
     * 构造迁移。
     *
     * @param flow 流转
     * @param guard 守卫，可为 {@code null}（按恒真处理）
     */
    public Transition(ProcessModelFlow flow, Guard guard) {
        this.flow = flow;
        this.guard = guard == null ? AlwaysGuard.INSTANCE : guard;
    }

    /**
     * 源节点键。
     *
     * @return 源节点键
     */
    public String from() {
        return flow.getFrom();
    }

    /**
     * 目标节点键。
     *
     * @return 目标节点键
     */
    public String to() {
        return flow.getTo();
    }

    /**
     * 判定本迁移在当前上下文是否可用。
     *
     * @param context 守卫上下文
     * @return 可用返回 {@code true}
     */
    public boolean matches(GuardContext context) {
        return guard.accept(context, flow);
    }
}
