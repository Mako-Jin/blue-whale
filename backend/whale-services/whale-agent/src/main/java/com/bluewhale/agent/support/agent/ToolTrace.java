package com.bluewhale.agent.support.agent;

/**
 * 工具调用轨迹（用于 SSE {@code tool} 事件与排障）。
 *
 * @param step           轮次
 * @param name           工具名
 * @param arguments      参数 JSON
 * @param success        是否成功
 * @param elapsedMillis  耗时（毫秒）
 * @param evidenceCount  新增证据条数
 * @param error          错误信息（成功时为 {@code null}）
 * @author BlueWhale
 */
public record ToolTrace(int step, String name, String arguments, boolean success,
                        long elapsedMillis, int evidenceCount, String error) {
}
