package com.bluewhale.erp.mfg.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.erp.mfg.domain.model.MesCallbackLog;
import com.bluewhale.erp.mfg.domain.model.MesProgressSnapshot;
import com.bluewhale.erp.mfg.domain.repository.MesSyncRepository;
import com.bluewhale.erp.mfg.infrastructure.persistence.entity.MesCallbackEntity;
import com.bluewhale.erp.mfg.infrastructure.persistence.entity.MesProgressEntity;
import com.bluewhale.erp.mfg.infrastructure.persistence.mapper.MesCallbackMapper;
import com.bluewhale.erp.mfg.infrastructure.persistence.mapper.MesProgressMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * MES 回调幂等与进度快照仓储实现。
 *
 * @author BlueWhale
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MesSyncRepositoryImpl implements MesSyncRepository {

    private final MesCallbackMapper mesCallbackMapper;

    private final MesProgressMapper mesProgressMapper;

    private final ObjectMapper objectMapper;

    @Override
    public boolean existsEvent(String sourceEventId) {
        return mesCallbackMapper.selectCount(new LambdaQueryWrapper<MesCallbackEntity>()
                .eq(MesCallbackEntity::getSourceEventId, sourceEventId)) > 0;
    }

    @Override
    public void insertCallback(MesCallbackLog log) {
        MesCallbackEntity entity = new MesCallbackEntity();
        entity.setOrderId(log.getOrderId());
        entity.setErpOrderNo(log.getErpOrderNo());
        entity.setCallbackType(log.getCallbackType());
        entity.setSourceEventId(log.getSourceEventId());
        entity.setStatus(log.getStatus());
        entity.setPayload(log.getPayload());
        entity.setError(log.getError());
        entity.setReceivedAt(log.getReceivedAt());
        mesCallbackMapper.insert(entity);
    }

    @Override
    public void upsertProgress(MesProgressSnapshot snapshot) {
        MesProgressEntity entity = findByOrderNo(snapshot.getErpOrderNo());
        if (entity == null) {
            entity = new MesProgressEntity();
            entity.setErpOrderNo(snapshot.getErpOrderNo());
        }
        entity.setOrderId(snapshot.getOrderId());
        entity.setMesStatus(snapshot.getMesStatus());
        entity.setPlannedQuantity(snapshot.getPlannedQuantity());
        entity.setReportedQuantity(snapshot.getReportedQuantity());
        entity.setQualifiedQuantity(snapshot.getQualifiedQuantity());
        entity.setCompletedQuantity(snapshot.getCompletedQuantity());
        entity.setTaskJson(writeTasks(snapshot.getTasks()));
        entity.setEventId(snapshot.getEventId());
        entity.setReceivedAt(snapshot.getReceivedAt());
        if (entity.getId() == null) {
            mesProgressMapper.insert(entity);
        } else {
            mesProgressMapper.updateById(entity);
        }
    }

    @Override
    public Optional<MesProgressSnapshot> findProgress(String erpOrderNo) {
        MesProgressEntity entity = findByOrderNo(erpOrderNo);
        return entity == null ? Optional.empty() : Optional.of(toSnapshot(entity));
    }

    @Override
    public List<MesCallbackLog> listCallbacks(String erpOrderNo) {
        return mesCallbackMapper.selectList(new LambdaQueryWrapper<MesCallbackEntity>()
                        .eq(MesCallbackEntity::getErpOrderNo, erpOrderNo)
                        .orderByDesc(MesCallbackEntity::getReceivedAt))
                .stream().map(this::toLog).toList();
    }

    /**
     * 按订单号查询进度快照。
     *
     * @param erpOrderNo ERP 生产订单号
     * @return 实体
     */
    private MesProgressEntity findByOrderNo(String erpOrderNo) {
        return mesProgressMapper.selectOne(new LambdaQueryWrapper<MesProgressEntity>()
                .eq(MesProgressEntity::getErpOrderNo, erpOrderNo)
                .last("limit 1"));
    }

    /**
     * 序列化任务明细。
     *
     * @param tasks 任务列表
     * @return JSON
     */
    private String writeTasks(List<MesProgressSnapshot.Task> tasks) {
        if (tasks == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(tasks);
        } catch (Exception e) {
            log.warn("MES 任务明细序列化失败", e);
            return null;
        }
    }

    /**
     * 实体转进度快照。
     *
     * @param entity 实体
     * @return 快照
     */
    private MesProgressSnapshot toSnapshot(MesProgressEntity entity) {
        MesProgressSnapshot snapshot = new MesProgressSnapshot();
        snapshot.setErpOrderNo(entity.getErpOrderNo());
        snapshot.setOrderId(entity.getOrderId());
        snapshot.setMesStatus(entity.getMesStatus());
        snapshot.setPlannedQuantity(entity.getPlannedQuantity());
        snapshot.setReportedQuantity(entity.getReportedQuantity());
        snapshot.setQualifiedQuantity(entity.getQualifiedQuantity());
        snapshot.setCompletedQuantity(entity.getCompletedQuantity());
        snapshot.setEventId(entity.getEventId());
        snapshot.setReceivedAt(entity.getReceivedAt());
        if (StringUtils.hasText(entity.getTaskJson())) {
            try {
                snapshot.setTasks(objectMapper.readValue(entity.getTaskJson(),
                        new TypeReference<List<MesProgressSnapshot.Task>>() {
                        }));
            } catch (Exception e) {
                log.warn("MES 任务明细反序列化失败", e);
            }
        }
        return snapshot;
    }

    /**
     * 实体转回调流水。
     *
     * @param entity 实体
     * @return 回调流水
     */
    private MesCallbackLog toLog(MesCallbackEntity entity) {
        MesCallbackLog log = new MesCallbackLog();
        log.setOrderId(entity.getOrderId());
        log.setErpOrderNo(entity.getErpOrderNo());
        log.setCallbackType(entity.getCallbackType());
        log.setSourceEventId(entity.getSourceEventId());
        log.setStatus(entity.getStatus());
        log.setPayload(entity.getPayload());
        log.setError(entity.getError());
        log.setReceivedAt(entity.getReceivedAt());
        return log;
    }
}
