package com.bluewhale.audit.retention;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.common.log.entity.ApiLogRecord;
import com.bluewhale.common.log.entity.BehaviorLogRecord;
import com.bluewhale.common.log.entity.FrontendLogRecord;
import com.bluewhale.common.log.entity.OutboundLogRecord;
import com.bluewhale.common.log.mapper.ApiLogMapper;
import com.bluewhale.common.log.mapper.BehaviorLogMapper;
import com.bluewhale.common.log.mapper.FrontendLogMapper;
import com.bluewhale.common.log.mapper.OutboundLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.function.IntSupplier;

/**
 * 多通道日志表保留期清理执行器（REQ-SEC-006）。
 *
 * <p>按 {@code create_time < 当前时间 - 保留天数} 删除 4 张日志表的过期行：
 * {@code api_log} / {@code outbound_log} / {@code frontend_log} / {@code behavior_log}；
 * <b>不触碰 {@code audit_log}</b>（其保留/归档策略属 AUTH-09）。</p>
 *
 * <p>单表删除异常仅告警，不中断其余表的清理，也不冒泡影响服务。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogRetentionCleaner {

    /** 接口日志表名（用于告警文案） */
    private static final String API_TABLE = "api_log";

    /** 三方调用日志表名（用于告警文案） */
    private static final String OUTBOUND_TABLE = "outbound_log";

    /** 前端开发者日志表名（用于告警文案） */
    private static final String FRONTEND_TABLE = "frontend_log";

    /** 前端行为埋点表名（用于告警文案） */
    private static final String BEHAVIOR_TABLE = "behavior_log";

    private final ApiLogMapper apiLogMapper;

    private final OutboundLogMapper outboundLogMapper;

    private final FrontendLogMapper frontendLogMapper;

    private final BehaviorLogMapper behaviorLogMapper;

    /**
     * 清理 4 张日志表中的过期记录（单表失败仅告警，不影响其余表）
     *
     * @param retentionDays 保留天数；小于 0 时按 0 处理（仅清理未来时间戳的异常数据）
     */
    public void cleanExpired(int retentionDays) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(Math.max(retentionDays, 0));
        log.info("日志保留期清理开始: 阈值={}", threshold);
        delete(API_TABLE, () -> apiLogMapper.delete(
                Wrappers.<ApiLogRecord>lambdaQuery().lt(ApiLogRecord::getCreateTime, threshold)));
        delete(OUTBOUND_TABLE, () -> outboundLogMapper.delete(
                Wrappers.<OutboundLogRecord>lambdaQuery().lt(OutboundLogRecord::getCreateTime, threshold)));
        delete(FRONTEND_TABLE, () -> frontendLogMapper.delete(
                Wrappers.<FrontendLogRecord>lambdaQuery().lt(FrontendLogRecord::getCreateTime, threshold)));
        delete(BEHAVIOR_TABLE, () -> behaviorLogMapper.delete(
                Wrappers.<BehaviorLogRecord>lambdaQuery().lt(BehaviorLogRecord::getCreateTime, threshold)));
        log.info("日志保留期清理结束: 阈值={}", threshold);
    }

    /**
     * 执行单表删除；异常仅告警不冒泡
     *
     * @param table 表名（告警文案用）
     * @param action 返回删除行数的删除动作
     */
    private void delete(String table, IntSupplier action) {
        try {
            int deleted = action.getAsInt();
            log.info("日志保留期清理完成: 表={}, 删除行数={}", table, deleted);
        } catch (Exception e) {
            log.warn("日志保留期清理失败（不阻断其余表与服务）: 表={}, reason={}", table, e.getMessage(), e);
        }
    }
}
