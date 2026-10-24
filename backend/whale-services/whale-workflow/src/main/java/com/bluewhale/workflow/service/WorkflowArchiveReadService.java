package com.bluewhale.workflow.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.workflow.vo.ApprovedInstanceVO;
import com.bluewhale.workflow.vo.ArchiveInstanceDetailVO;

import java.time.LocalDateTime;

/**
 * 流程实例归档只读服务（内网接口专用）。
 *
 * <p>仅供档案服务（whale-archive）增量拉取「审批通过」实例并生成不可变快照，
 * 不含任何写操作；外部经网关不可达（网关 {@code internal-deny} 对 {@code /workflow/internal/**} 返回 403）。</p>
 *
 * @author BlueWhale
 */
public interface WorkflowArchiveReadService {

    /**
     * 按办结时间游标分页查询已通过实例。
     *
     * <p>排序固定为 {@code update_time ASC, id ASC}，调用方以返回记录的最大
     * {@code updateTime} 作为下一轮游标，避免深分页与漏拉。</p>
     *
     * @param updatedAfter 游标下界（不含），可为 {@code null} 表示不限
     * @param updatedBefore 游标上界（含），可为 {@code null} 表示不限
     * @param current 页码（小于 1 时按 1 处理）
     * @param size 每页条数（1..200，越界自动收敛）
     * @return 已通过实例分页结果
     */
    PageResult<ApprovedInstanceVO> approved(LocalDateTime updatedAfter, LocalDateTime updatedBefore,
                                            long current, long size);

    /**
     * 查询实例归档详情：实例 + 定义快照 JSON + 任务轨迹。
     *
     * @param id 流程实例ID
     * @return 归档详情视图对象
     */
    ArchiveInstanceDetailVO detail(Long id);
}
