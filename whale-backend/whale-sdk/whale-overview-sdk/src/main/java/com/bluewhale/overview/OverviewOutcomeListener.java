package com.bluewhale.overview;

/**
 * 概览装载结果监听器：`overview.load{service,outcome}` 指标的唯一出口。
 *
 * <p>存在的理由：降级是<b>静默失效</b>的高发区 —— 接口挂了三周，厅里还在显示三周前的数字，
 * 前端标了"陈旧"但没人看 3D 城，于是没有人知道。计数器把这件事变成可告警的时序。</p>
 *
 * <p>做成接口而不是直接依赖 Micrometer：没有 actuator 的服务不该因为缺一个类起不来。</p>
 *
 * @author BlueWhale
 */
@FunctionalInterface
public interface OverviewOutcomeListener {

    /**
     * 记录一次装载结果
     *
     * @param service 服务前缀
     * @param outcome live / stale / degraded / denied
     */
    void onLoad(String service, String outcome);
}
