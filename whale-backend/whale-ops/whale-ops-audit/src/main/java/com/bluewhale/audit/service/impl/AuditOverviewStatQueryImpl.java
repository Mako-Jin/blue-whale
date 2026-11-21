package com.bluewhale.audit.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.audit.mapper.stat.AuditDayCountPo;
import com.bluewhale.audit.mapper.stat.AuditOperateTypePo;
import com.bluewhale.audit.mapper.stat.AuditOverviewStatMapper;
import com.bluewhale.audit.retention.mapper.AuditJobHeartbeatMapper;
import com.bluewhale.audit.retention.po.AuditJobHeartbeatPo;
import com.bluewhale.audit.service.AuditOverviewStatQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link AuditOverviewStatQuery} 的 MyBatis 实现（S5 / T5.9，只读）。
 *
 * <p>归档作业心跳表 {@code audit_job_heartbeat} 是平台级（主键 job_code、无 tenant_id），
 * 由租户插件白名单排除；这里按固定 job_code 查单行，与指标 {@code audit.job.stale.days} 同口径。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class AuditOverviewStatQueryImpl implements AuditOverviewStatQuery {

    /** 归档作业心跳的 job_code，与 AuditJobStaleGauge / application.yml 口径一致 */
    private static final String ARCHIVE_JOB_CODE = "audit.log.retention";

    /** 操作类型为空时的兜底展示名 */
    private static final String OTHER = "OTHER";

    private final AuditOverviewStatMapper statMapper;

    private final AuditJobHeartbeatMapper heartbeatMapper;

    @Override
    public long todayCount(Long tenantId, LocalDateTime from, LocalDateTime to) {
        if (invalidWindow(tenantId, from, to)) {
            return 0L;
        }
        return statMapper.countBetween(tenantId, from, to);
    }

    @Override
    public long failedCount(Long tenantId, LocalDateTime from, LocalDateTime to) {
        if (invalidWindow(tenantId, from, to)) {
            return 0L;
        }
        return statMapper.countFailedBetween(tenantId, from, to);
    }

    @Override
    public LocalDateTime lastAuditTime(Long tenantId) {
        if (tenantId == null) {
            return null;
        }
        return statMapper.lastAuditTime(tenantId);
    }

    @Override
    public List<DayAudit> dailyAudits(Long tenantId, LocalDateTime from, LocalDateTime to) {
        if (invalidWindow(tenantId, from, to)) {
            return List.of();
        }
        List<AuditDayCountPo> rows = statMapper.countByDay(tenantId, from, to);
        List<DayAudit> result = new ArrayList<>(rows.size());
        for (AuditDayCountPo row : rows) {
            if (row.getDay() != null) {
                result.add(new DayAudit(row.getDay(), valueOf(row.getAudits())));
            }
        }
        return List.copyOf(result);
    }

    @Override
    public List<OperateTypeCount> topOperateTypes(Long tenantId, LocalDateTime from, LocalDateTime to, int limit) {
        if (invalidWindow(tenantId, from, to) || limit <= 0) {
            return List.of();
        }
        List<AuditOperateTypePo> rows = statMapper.topOperateTypes(tenantId, from, to, limit);
        List<OperateTypeCount> result = new ArrayList<>(rows.size());
        for (AuditOperateTypePo row : rows) {
            String type = row.getOperateType();
            result.add(new OperateTypeCount(type == null || type.isBlank() ? OTHER : type,
                    valueOf(row.getAudits())));
        }
        return List.copyOf(result);
    }

    @Override
    public LocalDateTime archiveLastSuccessAt() {
        AuditJobHeartbeatPo row = heartbeatMapper.selectOne(new LambdaQueryWrapper<AuditJobHeartbeatPo>()
                .eq(AuditJobHeartbeatPo::getJobCode, ARCHIVE_JOB_CODE));
        return row == null ? null : row.getLastSuccessAt();
    }

    /**
     * 时间窗参数是否不可用
     *
     * @param tenantId 租户 ID
     * @param from     起始时刻
     * @param to       截止时刻
     * @return 任一缺失或窗口为空时返回 true
     */
    private boolean invalidWindow(Long tenantId, LocalDateTime from, LocalDateTime to) {
        return tenantId == null || from == null || to == null || !from.isBefore(to);
    }

    /**
     * Long 拆箱兜底
     *
     * @param value 可能为 null 的计数
     * @return 非空计数
     */
    private long valueOf(Long value) {
        return value == null ? 0L : value;
    }
}
