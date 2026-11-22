package com.bluewhale.pdm.integration.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.pdm.config.PdmIntAlertProperties;
import com.bluewhale.pdm.integration.client.PdmIntAlertClient;
import com.bluewhale.pdm.integration.client.PdmIntErpClient;
import com.bluewhale.pdm.integration.client.PdmIntErpItemRequest;
import com.bluewhale.pdm.integration.client.PdmIntErpItemResult;
import com.bluewhale.pdm.integration.dto.PdmIntSyncQueryRequest;
import com.bluewhale.pdm.integration.entity.PdmIntSyncLog;
import com.bluewhale.pdm.integration.mapper.PdmIntSyncLogMapper;
import com.bluewhale.pdm.integration.service.PdmIntSyncService;
import com.bluewhale.pdm.integration.support.PdmIntAction;
import com.bluewhale.pdm.integration.support.PdmIntChannel;
import com.bluewhale.pdm.integration.support.PdmIntDirection;
import com.bluewhale.pdm.integration.support.PdmIntSyncStatus;
import com.bluewhale.pdm.integration.vo.PdmIntErpPartResultVO;
import com.bluewhale.pdm.integration.vo.PdmIntSyncLogVO;
import com.bluewhale.pdm.process.service.PdmPrcRouteService;
import com.bluewhale.pdm.service.PdmProductService;
import com.bluewhale.pdm.vo.PdmProductVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 集成同步日志服务实现
 *
 * <p>同步日志以 {@code channel + idempotencyKey} 覆盖写入，保证同一业务重复执行只留一条记录；
 * 失败原因保留在日志中，可按通道重试。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdmIntSyncServiceImpl implements PdmIntSyncService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final PdmIntSyncLogMapper syncLogMapper;

    private final PdmIntErpClient erpClient;

    private final PdmProductService productService;

    private final PdmPrcRouteService routeService;

    private final ObjectMapper objectMapper;

    private final PdmIntAlertClient alertClient;

    private final PdmIntAlertProperties alertProperties;

    @Override
    public PageResult<PdmIntSyncLogVO> page(PdmIntSyncQueryRequest request) {
        LambdaQueryWrapper<PdmIntSyncLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(request.getChannel()), PdmIntSyncLog::getChannel, request.getChannel());
        wrapper.eq(StringUtils.hasText(request.getAction()), PdmIntSyncLog::getAction, request.getAction());
        wrapper.eq(StringUtils.hasText(request.getStatus()), PdmIntSyncLog::getStatus, request.getStatus());
        wrapper.eq(StringUtils.hasText(request.getBizType()), PdmIntSyncLog::getBizType, request.getBizType());
        wrapper.like(StringUtils.hasText(request.getBizNo()), PdmIntSyncLog::getBizNo, request.getBizNo());
        wrapper.orderByDesc(PdmIntSyncLog::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<PdmIntSyncLog> page = syncLogMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<PdmIntSyncLogVO> records = new ArrayList<>(page.getRecords().size());
        for (PdmIntSyncLog entity : page.getRecords()) {
            records.add(toVo(entity));
        }
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PdmIntSyncLogVO findByKey(String channel, String idempotencyKey) {
        PdmIntSyncLog entity = findEntity(channel, idempotencyKey);
        return entity == null ? null : toVo(entity);
    }

    @Override
    public PdmIntSyncLogVO record(String channel, String action, String direction, String bizType, Long bizId,
                                  String bizNo, String versionNo, String idempotencyKey, String payload,
                                  String checksum, String externalRef, String error, Long operatorId) {
        PdmIntSyncLog entity = findEntity(channel, idempotencyKey);
        boolean created = entity == null;
        String previousError = created ? null : entity.getLastError();
        if (created) {
            entity = new PdmIntSyncLog();
            entity.setChannel(channel);
            entity.setIdempotencyKey(idempotencyKey);
            entity.setRetryCount(0);
            entity.setRequestedAt(LocalDateTime.now());
        }
        entity.setAction(action);
        entity.setDirection(direction);
        entity.setBizType(bizType);
        entity.setBizId(bizId);
        entity.setBizNo(bizNo);
        entity.setVersionNo(versionNo);
        entity.setPayload(payload);
        entity.setChecksum(checksum);
        entity.setExternalRef(externalRef);
        entity.setOperatorId(operatorId);
        entity.setStatus(error == null ? PdmIntSyncStatus.SUCCESS : PdmIntSyncStatus.FAILED);
        String newError = truncate(error);
        entity.setLastError(newError);
        entity.setFinishedAt(LocalDateTime.now());
        if (created) {
            syncLogMapper.insert(entity);
        } else {
            syncLogMapper.updateById(entity);
        }
        if (error != null && (created || !newError.equals(previousError))) {
            alert(channel, action, bizNo, idempotencyKey, error);
        }
        return toVo(entity);
    }

    /**
     * 投递同步失败告警（接收人为空或投递异常均不影响同步结果）
     *
     * @param channel        通道
     * @param action         动作
     * @param bizNo          业务编码
     * @param idempotencyKey 幂等键
     * @param error          失败原因
     */
    private void alert(String channel, String action, String bizNo, String idempotencyKey, String error) {
        if (alertProperties.getUserIds() == null || alertProperties.getUserIds().isEmpty()) {
            log.debug("PDM 同步失败告警跳过（未配置接收人）：key={}", idempotencyKey);
            return;
        }
        try {
            Long tenantId = TenantContext.get() == null ? 0L : TenantContext.get();
            String title = "PDM 集成同步失败：" + channel + " " + action;
            String content = "业务 " + (bizNo == null ? "-" : bizNo) + " 同步失败：" + error;
            alertClient.send(tenantId, "PDM_INT_SYNC", idempotencyKey,
                    "PDM:ALERT:" + channel + ":" + idempotencyKey, title, content,
                    List.of(alertProperties.getChannel()), alertProperties.getUserIds());
        } catch (RuntimeException ex) {
            log.warn("PDM 同步失败告警投递失败：key={}，原因={}", idempotencyKey, ex.getMessage());
        }
    }

    @Override
    public void recordBomPull(String bomNo, String versionNo, String checksum) {
        String key = "ERP:BOM_PULL:" + bomNo + ":" + versionNo;
        record(PdmIntChannel.ERP, PdmIntAction.BOM_PULL, PdmIntDirection.OUTBOUND, "BOM", null,
                bomNo, versionNo, key, null, checksum, null, null, null);
    }

    @Override
    public PdmIntSyncLogVO retry(Long id, Long operatorId) {
        PdmIntSyncLog entity = id == null ? null : syncLogMapper.selectById(id);
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.pdm.integration.sync-record-found", "同步记录不存在");
        }
        entity.setRetryCount((entity.getRetryCount() == null ? 0 : entity.getRetryCount()) + 1);
        entity.setOperatorId(operatorId);
        if (PdmIntAction.PART_UPSERT.equals(entity.getAction())) {
            syncPartToErp(entity.getBizId(), operatorId);
            return findEntity(entity.getChannel(), entity.getIdempotencyKey()) == null
                    ? toVo(entity) : findByKey(entity.getChannel(), entity.getIdempotencyKey());
        }
        if (PdmIntAction.ROUTE_PUSH.equals(entity.getAction())) {
            try {
                routeService.retryPublish(entity.getBizId());
                entity.setStatus(PdmIntSyncStatus.SUCCESS);
                entity.setLastError(null);
            } catch (BizException ex) {
                entity.setStatus(PdmIntSyncStatus.FAILED);
                entity.setLastError(truncate(ex.getMessage()));
            }
            entity.setFinishedAt(LocalDateTime.now());
            syncLogMapper.updateById(entity);
            return toVo(entity);
        }
        if (PdmIntAction.BOM_NOTIFY.equals(entity.getAction())) {
            entity.setStatus(PdmIntSyncStatus.SUCCESS);
            entity.setLastError(null);
            entity.setFinishedAt(LocalDateTime.now());
            syncLogMapper.updateById(entity);
            return toVo(entity);
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.integration.sync-action-support-manual-retry", "该同步动作不支持手动重试");
    }

    @Override
    public PdmIntErpPartResultVO syncPartToErp(Long partId, Long operatorId) {
        PdmProductVO part = productService.getDetail(partId);
        String key = "ERP:PART:" + part.getCode();
        PdmIntErpItemRequest request = new PdmIntErpItemRequest(part.getCode(), part.getName(),
                StringUtils.hasText(part.getStatus()) ? part.getStatus() : "ENABLED", buildRemark(part));
        String payload = toJson(request);
        PdmIntErpPartResultVO result = new PdmIntErpPartResultVO();
        result.setCode(part.getCode());
        try {
            PdmIntErpItemResult upserted = erpClient.upsertItem(TenantContext.requireId(), request);
            result.setErpItemId(upserted.id());
            result.setCreated(upserted.created());
            result.setStatus(PdmIntSyncStatus.SUCCESS);
            PdmIntSyncLogVO logVo = record(PdmIntChannel.ERP, PdmIntAction.PART_UPSERT,
                    PdmIntDirection.OUTBOUND, "PART", partId, part.getCode(), null, key,
                    payload, null, String.valueOf(upserted.id()), null, operatorId);
            result.setSyncLogId(logVo.getId());
        } catch (BizException ex) {
            PdmIntSyncLogVO logVo = record(PdmIntChannel.ERP, PdmIntAction.PART_UPSERT,
                    PdmIntDirection.OUTBOUND, "PART", partId, part.getCode(), null, key,
                    payload, null, null, ex.getMessage(), operatorId);
            result.setStatus(PdmIntSyncStatus.FAILED);
            result.setLastError(ex.getMessage());
            result.setSyncLogId(logVo.getId());
            log.warn("ERP 物料同步失败: code={}, reason={}", part.getCode(), ex.getMessage());
        }
        return result;
    }

    /**
     * 按通道与幂等键查询日志实体。
     *
     * @param channel        通道
     * @param idempotencyKey 幂等键
     * @return 日志实体，未找到返回 {@code null}
     */
    private PdmIntSyncLog findEntity(String channel, String idempotencyKey) {
        LambdaQueryWrapper<PdmIntSyncLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PdmIntSyncLog::getChannel, channel);
        wrapper.eq(PdmIntSyncLog::getIdempotencyKey, idempotencyKey);
        wrapper.last("LIMIT 1");
        return syncLogMapper.selectOne(wrapper);
    }

    /**
     * 组装 ERP 物料备注快照。
     *
     * @param part 零件视图
     * @return 备注
     */
    private String buildRemark(PdmProductVO part) {
        List<String> segments = new ArrayList<>();
        segments.add("材质:" + nullToEmpty(part.getMaterial()));
        segments.add("规格:" + nullToEmpty(part.getSpec()));
        segments.add("单位:" + nullToEmpty(part.getUom()));
        segments.add("PDM零件类型:" + nullToEmpty(part.getPartType()));
        return String.join("; ", segments);
    }

    /**
     * 对象序列化为 JSON。
     *
     * @param value 对象
     * @return JSON 字符串
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("同步内容序列化失败: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * 空值转空串。
     *
     * @param value 原值
     * @return 非空字符串
     */
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 截断失败原因到列宽。
     *
     * @param message 原始信息
     * @return 截断结果
     */
    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    /**
     * 实体转视图对象。
     *
     * @param entity 日志实体
     * @return 视图对象
     */
    private static PdmIntSyncLogVO toVo(PdmIntSyncLog entity) {
        PdmIntSyncLogVO vo = new PdmIntSyncLogVO();
        vo.setId(entity.getId());
        vo.setChannel(entity.getChannel());
        vo.setAction(entity.getAction());
        vo.setDirection(entity.getDirection());
        vo.setBizType(entity.getBizType());
        vo.setBizId(entity.getBizId());
        vo.setBizNo(entity.getBizNo());
        vo.setVersionNo(entity.getVersionNo());
        vo.setIdempotencyKey(entity.getIdempotencyKey());
        vo.setStatus(entity.getStatus());
        vo.setRetryCount(entity.getRetryCount());
        vo.setLastError(entity.getLastError());
        vo.setExternalRef(entity.getExternalRef());
        vo.setChecksum(entity.getChecksum());
        vo.setOperatorId(entity.getOperatorId());
        vo.setRequestedAt(entity.getRequestedAt());
        vo.setFinishedAt(entity.getFinishedAt());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
