package com.bluewhale.crm.commerce.domain.port;

import java.util.List;
import java.util.Map;

/**
 * Workflow 实例状态查询端口（审批结果对账用）。
 *
 * <p>应用层只依赖本端口，具体 workflow 内网 HTTP 交互由基础设施层实现；
 * 常量随端口下沉，避免应用层反向引用 infrastructure 客户端类。</p>
 *
 * @author BlueWhale
 */
public interface WorkflowStatusPort {

    /** 批量查询单次上限（与 workflow 侧约束一致） */
    int MAX_BATCH_SIZE = 100;

    /** 实例状态：运行中 */
    int STATUS_RUNNING = 0;

    /** 实例状态：已通过 */
    int STATUS_APPROVED = 1;

    /** 实例状态：已驳回 */
    int STATUS_REJECTED = 2;

    /** 实例状态：已撤销 */
    int STATUS_CANCELLED = 3;

    /**
     * 批量查询流程实例状态摘要。
     *
     * @param instanceIds 实例 ID 列表（最多 {@value #MAX_BATCH_SIZE} 个，超出部分截断）
     * @return 实例 ID → 状态码（0 运行中 / 1 已通过 / 2 已驳回 / 3 已撤销）
     */
    Map<Long, Integer> batchStatus(List<Long> instanceIds);
}
