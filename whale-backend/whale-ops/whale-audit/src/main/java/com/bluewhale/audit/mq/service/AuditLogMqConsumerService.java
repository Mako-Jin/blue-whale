package com.bluewhale.audit.mq.service;

import com.bluewhale.audit.mq.entity.AuditMqMessage;
import com.bluewhale.audit.mq.mapper.AuditMqMessageMapper;
import com.bluewhale.audit.service.AuditLogService;
import com.bluewhale.common.audit.entity.AuditLogRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计 MQ 消息消费服务：幂等去重 + 审计落库（AUTH-10）。
 *
 * <p>去重表 {@code audit_mq_message} 与审计表 {@code audit_log} 同库同事务：
 * 先去重行写入、再落审计，任一步失败整体回滚，消息由监听容器重试；重试时去重行已回滚，
 * 不会误判为重复。主键冲突（{@code DuplicateKeyException}）说明该消息此前已成功落库，
 * 由调用方捕获并跳过，不重复追加审计。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogMqConsumerService {

    private final AuditMqMessageMapper auditMqMessageMapper;

    private final AuditLogService auditLogService;

    /**
     * 消费单条审计消息并落库。
     *
     * @param messageId 消息ID（幂等键，非空由调用方校验）
     * @param record 待落库的审计记录（主键由本库重新生成）
     * @throws org.springframework.dao.DuplicateKeyException 该 {@code messageId} 已被消费过
     */
    @Transactional(rollbackFor = Exception.class)
    public void consume(String messageId, AuditLogRecord record) {
        AuditMqMessage message = new AuditMqMessage();
        message.setMessageId(messageId);
        message.setCreateTime(LocalDateTime.now());
        auditMqMessageMapper.insert(message);
        auditLogService.ingest(List.of(record));
        log.debug("审计 MQ 消息已落库: messageId={}, traceId={}", messageId, record.getTraceId());
    }
}
