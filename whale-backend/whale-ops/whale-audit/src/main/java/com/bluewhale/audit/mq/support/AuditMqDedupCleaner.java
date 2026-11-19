package com.bluewhale.audit.mq.support;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.audit.mq.entity.AuditMqMessage;
import com.bluewhale.audit.mq.mapper.AuditMqMessageMapper;
import com.bluewhale.common.jobschedule.ScheduledJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 审计 MQ 去重表保留期清理（AUTH-10）。
 *
 * <p>{@code audit_mq_message} 每条消息一行，只增不减会长期膨胀（索引变大、insert 变慢）。
 * 本任务按 {@code create_time < 当前时间 - 保留天数} 删除旧去重行；保留期须不短于
 * RabbitMQ 消息最大存活时间，避免早于消息重投窗口删除去重键导致重复落库。</p>
 *
 * <p>仅在 {@code whale.audit.mq.consume-enabled=true} 时装配：未启用消费的环境不会产生去重行，
 * 也不会因表未创建（手工迁移脚本未执行）产生无意义告警。清理失败仅告警，不阻断消费主流程。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "whale.audit.mq", name = "consume-enabled", havingValue = "true")
public class AuditMqDedupCleaner {

    private final AuditMqMessageMapper auditMqMessageMapper;

    /** 去重行保留天数；配置非法（≤0）时按 1 天兜底，避免误删仍在重投窗口内的去重键 */
    private final int retentionDays;

    /**
     * 构造去重表清理任务
     *
     * @param auditMqMessageMapper 去重表 Mapper
     * @param retentionDays 去重行保留天数（{@code whale.audit.mq.dedup-retention-days}）
     */
    public AuditMqDedupCleaner(AuditMqMessageMapper auditMqMessageMapper,
                               @Value("${whale.audit.mq.dedup-retention-days:7}") int retentionDays) {
        this.auditMqMessageMapper = auditMqMessageMapper;
        this.retentionDays = retentionDays;
    }

    /**
     * 按配置 cron 清理过期去重行
     *
     * @return 执行摘要（保留天数与删除行数）
     */
    @ScheduledJob(code = "audit.mq.dedup.cleanup",
            cron = "${whale.audit.mq.dedup-clean-cron:0 30 3 * * ?}",
            timeoutSeconds = 600)
    public String cleanExpired() {
        int days = Math.max(retentionDays, 1);
        LocalDateTime threshold = LocalDateTime.now().minusDays(days);
        try {
            int deleted = auditMqMessageMapper.delete(
                    Wrappers.<AuditMqMessage>lambdaQuery().lt(AuditMqMessage::getCreateTime, threshold));
            log.info("审计 MQ 去重表清理完成: 保留天数={}, 阈值={}, 删除行数={}", days, threshold, deleted);
            return "days=" + days + ", deleted=" + deleted;
        } catch (Exception e) {
            log.warn("审计 MQ 去重表清理失败（不影响消费主流程）: reason={}", e.getMessage(), e);
            return "failed";
        }
    }
}
