package com.bluewhale.message.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.message.config.MessageArchiveProperties;
import com.bluewhale.message.mapper.MessageArchiveWatermarkMapper;
import com.bluewhale.message.mapper.MessageDeliveryMapper;
import com.bluewhale.message.mapper.MessageRecordMapper;
import com.bluewhale.message.po.MessageArchiveWatermarkPo;
import com.bluewhale.message.po.MessageDeliveryPo;
import com.bluewhale.message.po.MessageRecordPo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 消息数据归档任务。
 *
 * <p>按 {@code whale.message.archive.retention-days} 找到过期消息，逐租户增量导出为 NDJSON
 * （一条消息一行，内嵌投递明细）并上传 whale-file。归档只导出、不删除源表数据，
 * 通过 {@code msg_archive_watermark} 按租户记录 {@code create_time + id} 水位避免重复导出。</p>
 *
 * <p>单批失败只回退该批并终止当前租户本轮，不回退已成功批次的水位；多租户互不影响。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageArchiver {

    /** 上传结果中对象存储键字段名 */
    private static final String FIELD_OBJECT_KEY = "objectKey";

    /** 单批最小行数 */
    private static final int MIN_BATCH_ROWS = 1;

    /** 归档文件名时间戳格式 */
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final MessageRecordMapper recordMapper;

    private final MessageDeliveryMapper deliveryMapper;

    private final MessageArchiveWatermarkMapper watermarkMapper;

    private final MessageArchiveClient archiveClient;

    private final MessageArchiveProperties properties;

    private final ObjectMapper objectMapper;

    /**
     * 按配置 cron 触发归档；未启用时直接返回。
     */
    @Scheduled(cron = "${whale.message.archive.cron:0 30 3 * * ?}")
    public void archive() {
        if (!properties.isEnabled()) {
            return;
        }
        int retentionDays = Math.max(properties.getRetentionDays(), 0);
        LocalDateTime deadline = LocalDateTime.now().minusDays(retentionDays);
        for (Long tenantId : recordMapper.selectExpiredTenantIds(deadline)) {
            if (tenantId == null) {
                continue;
            }
            archiveTenant(tenantId, deadline);
        }
    }

    /**
     * 归档单个租户的过期消息。
     *
     * @param tenantId 租户ID
     * @param deadline 过期阈值（创建时间早于该值）
     */
    private void archiveTenant(Long tenantId, LocalDateTime deadline) {
        TenantContext.set(tenantId);
        try {
            MessageArchiveWatermarkPo watermark = loadWatermark(tenantId);
            int batchRows = Math.max(properties.getBatchRows(), MIN_BATCH_ROWS);
            int maxRows = Math.max(properties.getMaxRows(), batchRows);
            int archivedRows = 0;
            int batchNo = 0;
            while (archivedRows < maxRows) {
                int limit = Math.min(batchRows, maxRows - archivedRows);
                List<MessageRecordPo> records = queryExpired(deadline, watermark, limit);
                if (records.isEmpty()) {
                    break;
                }
                batchNo++;
                try {
                    Map<Long, List<MessageDeliveryPo>> deliveries = groupDeliveries(records);
                    byte[] content = toNdjson(tenantId, records, deliveries);
                    String fileName = "message-archive-" + tenantId + "-"
                            + LocalDateTime.now().format(FILE_TIME) + "-" + batchNo + ".ndjson";
                    Map<String, Object> uploaded = archiveClient.upload(
                            content, fileName, properties.getBucket());
                    watermark = advanceWatermark(tenantId, records.get(records.size() - 1));
                    archivedRows += records.size();
                    log.info("消息归档批次完成: tenantId={}, 批次={}, 本批行数={}, 累计行数={}, objectKey={}",
                            tenantId, batchNo, records.size(), archivedRows, uploaded.get(FIELD_OBJECT_KEY));
                } catch (Exception e) {
                    log.warn("消息归档失败（终止当前租户本轮，已成功批次水位不回退）: tenantId={}, 批次={}, reason={}",
                            tenantId, batchNo, e.getMessage(), e);
                    return;
                }
                if (records.size() < limit) {
                    break;
                }
            }
            if (archivedRows == 0) {
                log.info("消息归档跳过：过期区间无待归档消息, tenantId={}, 阈值={}", tenantId, deadline);
            }
        } catch (Exception e) {
            log.warn("消息归档异常（跳过当前租户）: tenantId={}, reason={}", tenantId, e.getMessage(), e);
        } finally {
            TenantContext.clear();
        }
    }

    /**
     * 读取租户归档水位。
     *
     * @param tenantId 租户ID
     * @return 水位；尚未归档过返回 {@code null}
     */
    private MessageArchiveWatermarkPo loadWatermark(Long tenantId) {
        try {
            return watermarkMapper.selectById(tenantId);
        } catch (Exception e) {
            log.warn("消息归档水位读取失败，本轮按全量过期区间归档: tenantId={}, reason={}",
                    tenantId, e.getMessage());
            return null;
        }
    }

    /**
     * 查询待归档消息：早于阈值且严格晚于水位。
     *
     * @param deadline  过期阈值
     * @param watermark 水位（可空）
     * @param limit     本批最大行数
     * @return 待归档消息
     */
    private List<MessageRecordPo> queryExpired(LocalDateTime deadline,
                                               MessageArchiveWatermarkPo watermark, int limit) {
        LambdaQueryWrapper<MessageRecordPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(MessageRecordPo::getCreateTime, deadline);
        if (watermark != null && watermark.getLastCreateTime() != null) {
            LocalDateTime lastTime = watermark.getLastCreateTime();
            Long lastId = watermark.getLastId() == null ? 0L : watermark.getLastId();
            wrapper.and(w -> w.gt(MessageRecordPo::getCreateTime, lastTime)
                    .or(q -> q.eq(MessageRecordPo::getCreateTime, lastTime)
                            .gt(MessageRecordPo::getId, lastId)));
        }
        wrapper.orderByAsc(MessageRecordPo::getCreateTime)
                .orderByAsc(MessageRecordPo::getId)
                .last("LIMIT " + limit);
        return recordMapper.selectList(wrapper);
    }

    /**
     * 查询本批消息的投递明细并按消息ID分组。
     *
     * @param records 消息记录
     * @return 消息ID到投递明细的映射
     */
    private Map<Long, List<MessageDeliveryPo>> groupDeliveries(List<MessageRecordPo> records) {
        List<Long> messageIds = records.stream().map(MessageRecordPo::getId).toList();
        LambdaQueryWrapper<MessageDeliveryPo> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(MessageDeliveryPo::getMessageId, messageIds)
                .orderByAsc(MessageDeliveryPo::getId);
        return deliveryMapper.selectList(wrapper).stream()
                .collect(Collectors.groupingBy(MessageDeliveryPo::getMessageId));
    }

    /**
     * 生成 NDJSON：一行一条消息，内嵌投递明细。
     *
     * @param tenantId   租户ID
     * @param records    消息记录
     * @param deliveries 投递明细分组
     * @return UTF-8 内容
     * @throws Exception 序列化失败
     */
    private byte[] toNdjson(Long tenantId, List<MessageRecordPo> records,
                            Map<Long, List<MessageDeliveryPo>> deliveries) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (MessageRecordPo record : records) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("id", record.getId());
            line.put("tenantId", tenantId);
            line.put("bizType", record.getBizType());
            line.put("bizId", record.getBizId());
            line.put("templateCode", record.getTemplateCode());
            line.put("idempotencyKey", record.getIdempotencyKey());
            line.put("title", record.getTitle());
            line.put("content", record.getContent());
            line.put("channels", record.getChannels());
            line.put("status", record.getStatus());
            line.put("callbackUrl", record.getCallbackUrl());
            line.put("expireAt", record.getExpireAt());
            line.put("traceId", record.getTraceId());
            line.put("createTime", record.getCreateTime());
            line.put("deliveries", deliveryLines(deliveries.get(record.getId())));
            builder.append(objectMapper.writeValueAsString(line)).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 生成单条消息的投递明细行。
     *
     * @param deliveries 投递明细（可空）
     * @return 投递明细列表
     */
    private static List<Map<String, Object>> deliveryLines(List<MessageDeliveryPo> deliveries) {
        List<Map<String, Object>> lines = new ArrayList<>();
        if (deliveries == null) {
            return lines;
        }
        for (MessageDeliveryPo delivery : deliveries) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("channel", delivery.getChannel());
            line.put("recipient", delivery.getRecipient());
            line.put("status", delivery.getStatus());
            line.put("providerMessageId", delivery.getProviderMessageId());
            line.put("errorMessage", delivery.getErrorMessage());
            line.put("attempts", delivery.getAttempts());
            line.put("sentAt", delivery.getSentAt());
            line.put("deliveredAt", delivery.getDeliveredAt());
            line.put("readAt", delivery.getReadAt());
            lines.add(line);
        }
        return lines;
    }

    /**
     * 上传成功后推进租户归档水位。
     *
     * @param tenantId 租户ID
     * @param last     本批最后一条已归档消息
     * @return 下一批续查水位
     */
    private MessageArchiveWatermarkPo advanceWatermark(Long tenantId, MessageRecordPo last) {
        MessageArchiveWatermarkPo next = new MessageArchiveWatermarkPo();
        next.setId(tenantId);
        next.setLastCreateTime(last.getCreateTime());
        next.setLastId(last.getId());
        next.setUpdateTime(LocalDateTime.now());
        try {
            if (watermarkMapper.selectById(tenantId) == null) {
                watermarkMapper.insert(next);
            } else {
                watermarkMapper.updateById(next);
            }
        } catch (Exception e) {
            log.warn("消息归档水位推进失败（下轮可能重复导出本批）: tenantId={}, reason={}",
                    tenantId, e.getMessage(), e);
        }
        return next;
    }
}
