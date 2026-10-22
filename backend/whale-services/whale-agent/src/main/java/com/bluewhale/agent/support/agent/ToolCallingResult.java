package com.bluewhale.agent.support.agent;

import java.util.List;

/**
 * 自主编排结果。
 *
 * @param content    最终回答（模型生成或兜底摘要）
 * @param traces     工具调用轨迹
 * @param evidence   去重后的证据（顺序即引用编号顺序 S1..Sn）
 * @param transcript 检索过程记录（用于排障与降级摘要）
 * @param degraded   是否因模型调用失败而回退
 * @author BlueWhale
 */
public record ToolCallingResult(String content, List<ToolTrace> traces,
                                List<ToolEvidence> evidence, String transcript, boolean degraded) {
}
