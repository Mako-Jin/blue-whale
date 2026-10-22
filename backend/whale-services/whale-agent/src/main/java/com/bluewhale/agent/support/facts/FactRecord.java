package com.bluewhale.agent.support.facts;

import java.util.Map;

/**
 * 采集到的一条结构化事实（写入 {@code agent_fact} 前的内存形态）。
 *
 * @param factType 事实类型：SERVICE / ROUTE / PORT / DEPENDENCY / TABLE / COLUMN / COMMIT / ENDPOINT / RUNTIME_SERVICE
 * @param factKey  事实键（服务名、路由 id、表名、依赖坐标等）
 * @param value    事实值（结构化字段，落库时序列化为 JSON）
 * @param sourcePath 来源（仓库相对路径、git 提交哈希或 runtime:nacos）
 * @author BlueWhale
 */
public record FactRecord(String factType, String factKey, Map<String, Object> value, String sourcePath) {
}
