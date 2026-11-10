package com.bluewhale.mes.collect.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.collect.dto.MesArchivePolicyQueryRequest;
import com.bluewhale.mes.collect.dto.MesArchivePolicySaveRequest;
import com.bluewhale.mes.collect.dto.MesCollectBoardQueryRequest;
import com.bluewhale.mes.collect.dto.MesCollectBoardSaveRequest;
import com.bluewhale.mes.collect.dto.MesCollectDeviceQueryRequest;
import com.bluewhale.mes.collect.dto.MesCollectDeviceSaveRequest;
import com.bluewhale.mes.collect.dto.MesCollectPointQueryRequest;
import com.bluewhale.mes.collect.dto.MesCollectPointSaveRequest;
import com.bluewhale.mes.collect.dto.MesMetricIngestRequest;
import com.bluewhale.mes.collect.dto.MesMetricSampleQueryRequest;
import com.bluewhale.mes.collect.entity.MesArchivePolicy;
import com.bluewhale.mes.collect.entity.MesCollectBoardConfig;
import com.bluewhale.mes.collect.entity.MesCollectDevice;
import com.bluewhale.mes.collect.entity.MesCollectionPoint;
import com.bluewhale.mes.collect.entity.MesMetricSample;
import com.bluewhale.mes.collect.mapper.MesArchivePolicyMapper;
import com.bluewhale.mes.collect.mapper.MesCollectBoardConfigMapper;
import com.bluewhale.mes.collect.mapper.MesCollectDeviceMapper;
import com.bluewhale.mes.collect.mapper.MesCollectionPointMapper;
import com.bluewhale.mes.collect.mapper.MesMetricSampleMapper;
import com.bluewhale.mes.collect.service.MesCollectService;
import com.bluewhale.mes.collect.vo.MesArchivePolicyVO;
import com.bluewhale.mes.collect.vo.MesCollectBoardDataVO;
import com.bluewhale.mes.collect.vo.MesCollectBoardVO;
import com.bluewhale.mes.collect.vo.MesCollectDeviceVO;
import com.bluewhale.mes.collect.vo.MesCollectPointVO;
import com.bluewhale.mes.collect.vo.MesMetricIngestResultVO;
import com.bluewhale.mes.collect.vo.MesMetricSampleVO;
import com.bluewhale.mes.common.support.MesCodeGenerator;
import com.bluewhale.mes.equipment.entity.MesEquipment;
import com.bluewhale.mes.equipment.mapper.MesEquipmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MES 数据采集应用服务实现
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MesCollectServiceImpl implements MesCollectService {

    private static final Set<String> ARCHIVE_TABLES = Set.of("mes_cl_metric_sample");

    private final MesCollectDeviceMapper deviceMapper;

    private final MesCollectionPointMapper pointMapper;

    private final MesMetricSampleMapper sampleMapper;

    private final MesCollectBoardConfigMapper boardMapper;

    private final MesArchivePolicyMapper archivePolicyMapper;

    private final MesEquipmentMapper equipmentMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesCollectDeviceVO saveDevice(MesCollectDeviceSaveRequest request) {
        MesCollectDevice entity;
        String code = trimToNull(request.getDeviceCode());
        if (request.getId() == null) {
            entity = new MesCollectDevice();
            if (code == null) {
                code = MesCodeGenerator.nextNo("EQG");
            }
            assertDeviceCodeUnique(null, code);
            entity.setDeviceCode(code);
            entity.setDeviceType(defaultIfBlank(request.getDeviceType(), "GATEWAY"));
            entity.setProtocol(defaultIfBlank(request.getProtocol(), "REST"));
            entity.setStatus(defaultIfBlank(request.getStatus(), "OFFLINE"));
        } else {
            entity = deviceMapper.selectById(request.getId());
            if (entity == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "采集设备不存在：" + request.getId());
            }
            if (code != null && !code.equals(entity.getDeviceCode())) {
                assertDeviceCodeUnique(entity.getId(), code);
                entity.setDeviceCode(code);
            }
            if (request.getDeviceType() != null) {
                entity.setDeviceType(request.getDeviceType());
            }
            if (request.getProtocol() != null) {
                entity.setProtocol(request.getProtocol());
            }
            if (request.getStatus() != null) {
                entity.setStatus(request.getStatus());
            }
        }
        entity.setDeviceName(request.getDeviceName());
        entity.setIpAddress(request.getIpAddress());
        entity.setLineId(request.getLineId());
        entity.setLineCode(request.getLineCode());
        entity.setRemark(request.getRemark());
        if (entity.getId() == null) {
            deviceMapper.insert(entity);
        } else if (deviceMapper.updateById(entity) < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "采集设备已被其他用户修改，请刷新后重试");
        }
        return toDeviceVO(entity);
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesCollectDeviceVO> pageDevices(MesCollectDeviceQueryRequest request) {
        LambdaQueryWrapper<MesCollectDevice> wrapper = new LambdaQueryWrapper<MesCollectDevice>()
                .like(StringUtils.hasText(request.getDeviceCode()), MesCollectDevice::getDeviceCode,
                        request.getDeviceCode())
                .like(StringUtils.hasText(request.getDeviceName()), MesCollectDevice::getDeviceName,
                        request.getDeviceName())
                .eq(StringUtils.hasText(request.getDeviceType()), MesCollectDevice::getDeviceType,
                        request.getDeviceType())
                .eq(StringUtils.hasText(request.getStatus()), MesCollectDevice::getStatus, request.getStatus())
                .orderByDesc(MesCollectDevice::getCreateTime);
        Page<MesCollectDevice> page = deviceMapper.selectPage(newPage(request.getCurrent(), request.getSize()),
                wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toDeviceVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesCollectDeviceVO heartbeat(String deviceCode) {
        MesCollectDevice device = deviceMapper.selectOne(new LambdaQueryWrapper<MesCollectDevice>()
                .eq(MesCollectDevice::getDeviceCode, deviceCode));
        if (device == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "采集设备不存在：" + deviceCode);
        }
        if ("DISABLED".equals(device.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "采集设备已停用：" + deviceCode);
        }
        device.setStatus("ONLINE");
        device.setLastHeartbeatAt(LocalDateTime.now());
        if (deviceMapper.updateById(device) < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "采集设备已被其他用户修改，请刷新后重试");
        }
        return toDeviceVO(device);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesCollectPointVO savePoint(MesCollectPointSaveRequest request) {
        String code = trimToNull(request.getPointCode());
        MesCollectionPoint entity = request.getId() == null
                ? createPoint(request, code)
                : updatePoint(request, code);
        entity.setPointName(request.getPointName());
        entity.setDeviceId(request.getDeviceId());
        entity.setDeviceCode(request.getDeviceCode());
        applyPointEquipment(entity, request);
        entity.setOperationCode(request.getOperationCode());
        entity.setUnit(request.getUnit());
        entity.setRemark(request.getRemark());
        if (entity.getId() == null) {
            pointMapper.insert(entity);
        } else if (pointMapper.updateById(entity) < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "采集点已被其他用户修改，请刷新后重试");
        }
        return toPointVO(entity);
    }

    /**
     * 构造新采集点
     *
     * @param request 保存请求
     * @param code    采集点编码
     * @return 采集点实体
     */
    private MesCollectionPoint createPoint(MesCollectPointSaveRequest request, String code) {
        MesCollectionPoint entity = new MesCollectionPoint();
        String pointCode = code == null ? MesCodeGenerator.nextNo("EQPT") : code;
        assertPointCodeUnique(null, pointCode);
        entity.setPointCode(pointCode);
        entity.setMetricType(defaultIfBlank(request.getMetricType(), "METRIC"));
        entity.setCollectIntervalSeconds(request.getCollectIntervalSeconds() == null
                ? 60 : request.getCollectIntervalSeconds());
        entity.setEnabled(request.getEnabled() == null ? 1 : request.getEnabled());
        return entity;
    }

    /**
     * 加载并应用采集点编辑字段
     *
     * @param request 保存请求
     * @param code    采集点编码
     * @return 采集点实体
     */
    private MesCollectionPoint updatePoint(MesCollectPointSaveRequest request, String code) {
        MesCollectionPoint entity = pointMapper.selectById(request.getId());
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "采集点不存在：" + request.getId());
        }
        if (code != null && !code.equals(entity.getPointCode())) {
            assertPointCodeUnique(entity.getId(), code);
            entity.setPointCode(code);
        }
        if (request.getMetricType() != null) {
            entity.setMetricType(request.getMetricType());
        }
        if (request.getCollectIntervalSeconds() != null) {
            entity.setCollectIntervalSeconds(request.getCollectIntervalSeconds());
        }
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled());
        }
        return entity;
    }

    /**
     * 解析并绑定采集点关联设备
     *
     * @param entity  采集点实体
     * @param request 保存请求
     */
    private void applyPointEquipment(MesCollectionPoint entity, MesCollectPointSaveRequest request) {
        MesEquipment equipment = null;
        if (request.getEquipmentId() != null) {
            equipment = equipmentMapper.selectById(request.getEquipmentId());
            if (equipment == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "设备不存在：" + request.getEquipmentId());
            }
        }
        entity.setEquipmentId(equipment == null ? request.getEquipmentId() : equipment.getId());
        entity.setEquipmentCode(equipment == null ? request.getEquipmentCode() : equipment.getEquipmentCode());
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesCollectPointVO> pagePoints(MesCollectPointQueryRequest request) {
        LambdaQueryWrapper<MesCollectionPoint> wrapper = new LambdaQueryWrapper<MesCollectionPoint>()
                .like(StringUtils.hasText(request.getPointCode()), MesCollectionPoint::getPointCode,
                        request.getPointCode())
                .like(StringUtils.hasText(request.getPointName()), MesCollectionPoint::getPointName,
                        request.getPointName())
                .eq(request.getEquipmentId() != null, MesCollectionPoint::getEquipmentId, request.getEquipmentId())
                .eq(request.getDeviceId() != null, MesCollectionPoint::getDeviceId, request.getDeviceId())
                .eq(request.getEnabled() != null, MesCollectionPoint::getEnabled, request.getEnabled())
                .orderByAsc(MesCollectionPoint::getPointCode);
        Page<MesCollectionPoint> page = pointMapper.selectPage(newPage(request.getCurrent(), request.getSize()),
                wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toPointVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesMetricIngestResultVO ingest(String pointCode, MesMetricIngestRequest request) {
        MesCollectionPoint point = pointMapper.selectOne(new LambdaQueryWrapper<MesCollectionPoint>()
                .eq(MesCollectionPoint::getPointCode, pointCode));
        if (point == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "采集点不存在：" + pointCode);
        }
        if (point.getEnabled() != null && point.getEnabled() == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "采集点已停用：" + pointCode);
        }
        int accepted = 0;
        int skipped = 0;
        for (MesMetricIngestRequest.SampleItem item : request.getSamples()) {
            int seq = item.getSeq() == null ? 1 : item.getSeq();
            long exists = sampleMapper.selectCount(new LambdaQueryWrapper<MesMetricSample>()
                    .eq(MesMetricSample::getPointId, point.getId())
                    .eq(MesMetricSample::getSampledAt, item.getSampledAt())
                    .eq(MesMetricSample::getSeq, seq));
            if (exists > 0) {
                skipped++;
                continue;
            }
            MesMetricSample sample = new MesMetricSample();
            sample.setPointId(point.getId());
            sample.setPointCode(point.getPointCode());
            sample.setEquipmentId(point.getEquipmentId());
            sample.setEquipmentCode(point.getEquipmentCode());
            sample.setMetricValue(item.getMetricValue());
            sample.setUnit(point.getUnit());
            sample.setSampledAt(item.getSampledAt());
            sample.setSeq(seq);
            sample.setSourceEventId(item.getSourceEventId());
            sample.setPayload(item.getPayload());
            sampleMapper.insert(sample);
            accepted++;
        }
        MesMetricIngestResultVO vo = new MesMetricIngestResultVO();
        vo.setPointCode(point.getPointCode());
        vo.setAccepted(accepted);
        vo.setSkipped(skipped);
        vo.setMessage(accepted == 0 ? "全部样本已存在，幂等跳过" : "样本接收成功");
        return vo;
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesMetricSampleVO> pageSamples(MesMetricSampleQueryRequest request) {
        LambdaQueryWrapper<MesMetricSample> wrapper = new LambdaQueryWrapper<MesMetricSample>()
                .eq(request.getPointId() != null, MesMetricSample::getPointId, request.getPointId())
                .like(StringUtils.hasText(request.getPointCode()), MesMetricSample::getPointCode,
                        request.getPointCode())
                .like(StringUtils.hasText(request.getEquipmentCode()), MesMetricSample::getEquipmentCode,
                        request.getEquipmentCode())
                .ge(StringUtils.hasText(request.getStartTime()), MesMetricSample::getSampledAt,
                        parseDateTime(request.getStartTime()))
                .le(StringUtils.hasText(request.getEndTime()), MesMetricSample::getSampledAt,
                        parseDateTime(request.getEndTime()))
                .orderByDesc(MesMetricSample::getSampledAt)
                .orderByAsc(MesMetricSample::getSeq);
        Page<MesMetricSample> page = sampleMapper.selectPage(newPage(request.getCurrent(), request.getSize()),
                wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toSampleVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesCollectBoardVO saveBoard(MesCollectBoardSaveRequest request) {
        MesCollectBoardConfig entity;
        String code = trimToNull(request.getBoardCode());
        if (request.getId() == null) {
            entity = new MesCollectBoardConfig();
            if (code == null) {
                code = MesCodeGenerator.nextNo("EQBD");
            }
            assertBoardCodeUnique(null, code);
            entity.setBoardCode(code);
            entity.setRefreshSeconds(request.getRefreshSeconds() == null ? 10 : request.getRefreshSeconds());
            entity.setStatus(defaultIfBlank(request.getStatus(), "ENABLED"));
        } else {
            entity = boardMapper.selectById(request.getId());
            if (entity == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "看板配置不存在：" + request.getId());
            }
            if (code != null && !code.equals(entity.getBoardCode())) {
                assertBoardCodeUnique(entity.getId(), code);
                entity.setBoardCode(code);
            }
            if (request.getRefreshSeconds() != null) {
                entity.setRefreshSeconds(request.getRefreshSeconds());
            }
            if (request.getStatus() != null) {
                entity.setStatus(request.getStatus());
            }
        }
        entity.setBoardName(request.getBoardName());
        entity.setLineId(request.getLineId());
        entity.setLineCode(request.getLineCode());
        entity.setMetricCodes(request.getMetricCodes());
        entity.setLayout(request.getLayout());
        entity.setRemark(request.getRemark());
        if (entity.getId() == null) {
            boardMapper.insert(entity);
        } else if (boardMapper.updateById(entity) < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "看板配置已被其他用户修改，请刷新后重试");
        }
        return toBoardVO(entity);
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesCollectBoardVO> pageBoards(MesCollectBoardQueryRequest request) {
        LambdaQueryWrapper<MesCollectBoardConfig> wrapper = new LambdaQueryWrapper<MesCollectBoardConfig>()
                .like(StringUtils.hasText(request.getBoardCode()), MesCollectBoardConfig::getBoardCode,
                        request.getBoardCode())
                .like(StringUtils.hasText(request.getBoardName()), MesCollectBoardConfig::getBoardName,
                        request.getBoardName())
                .eq(request.getLineId() != null, MesCollectBoardConfig::getLineId, request.getLineId())
                .eq(StringUtils.hasText(request.getStatus()), MesCollectBoardConfig::getStatus, request.getStatus())
                .orderByAsc(MesCollectBoardConfig::getBoardCode);
        Page<MesCollectBoardConfig> page = boardMapper.selectPage(newPage(request.getCurrent(),
                request.getSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toBoardVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** {@inheritDoc} */
    @Override
    public MesCollectBoardDataVO boardData(String boardCode) {
        MesCollectBoardConfig board = boardMapper.selectOne(new LambdaQueryWrapper<MesCollectBoardConfig>()
                .eq(MesCollectBoardConfig::getBoardCode, boardCode));
        if (board == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "看板不存在：" + boardCode);
        }
        if (!"ENABLED".equals(board.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "看板已停用：" + boardCode);
        }
        List<String> pointCodes = splitCodes(board.getMetricCodes());
        LambdaQueryWrapper<MesCollectionPoint> pointWrapper = new LambdaQueryWrapper<MesCollectionPoint>()
                .eq(MesCollectionPoint::getEnabled, 1)
                .in(!pointCodes.isEmpty(), MesCollectionPoint::getPointCode, pointCodes);
        List<MesCollectionPoint> points = pointMapper.selectList(pointWrapper);
        List<MesMetricSampleVO> latest = new ArrayList<>();
        for (MesCollectionPoint point : points) {
            MesMetricSample sample = sampleMapper.selectOne(new LambdaQueryWrapper<MesMetricSample>()
                    .eq(MesMetricSample::getPointId, point.getId())
                    .orderByDesc(MesMetricSample::getSampledAt)
                    .orderByDesc(MesMetricSample::getSeq)
                    .last("LIMIT 1"));
            if (sample != null) {
                latest.add(toSampleVO(sample));
            }
        }
        MesCollectBoardDataVO data = new MesCollectBoardDataVO();
        data.setBoard(toBoardVO(board));
        data.setLatestSamples(latest);
        data.setGeneratedAt(LocalDateTime.now().toString());
        return data;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesArchivePolicyVO saveArchivePolicy(MesArchivePolicySaveRequest request) {
        String code = trimToNull(request.getPolicyCode());
        String tableName = trimToNull(request.getTableName());
        if (tableName != null) {
            assertArchiveTable(tableName);
        }
        MesArchivePolicy entity = request.getId() == null
                ? createArchivePolicy(request, code)
                : updateArchivePolicy(request, code);
        entity.setTableName(tableName == null ? entity.getTableName() : tableName);
        entity.setRemark(request.getRemark());
        if (entity.getId() == null) {
            archivePolicyMapper.insert(entity);
        } else if (archivePolicyMapper.updateById(entity) < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "归档策略已被其他用户修改，请刷新后重试");
        }
        return toArchivePolicyVO(entity);
    }

    /**
     * 构造新归档策略
     *
     * @param request 保存请求
     * @param code    策略编码
     * @return 归档策略实体
     */
    private MesArchivePolicy createArchivePolicy(MesArchivePolicySaveRequest request, String code) {
        MesArchivePolicy entity = new MesArchivePolicy();
        String policyCode = code == null ? MesCodeGenerator.nextNo("EQAP") : code;
        assertPolicyCodeUnique(null, policyCode);
        entity.setPolicyCode(policyCode);
        entity.setStatus(defaultIfBlank(request.getStatus(), "ENABLED"));
        entity.setRetentionDays(request.getRetentionDays() == null ? 365 : request.getRetentionDays());
        entity.setArchiveBatchSize(request.getArchiveBatchSize() == null
                ? 1000 : request.getArchiveBatchSize());
        entity.setCronExpr(defaultIfBlank(request.getCronExpr(), "0 2 * * *"));
        entity.setLastCandidateCount(0L);
        return entity;
    }

    /**
     * 加载并应用归档策略编辑字段
     *
     * @param request 保存请求
     * @param code    策略编码
     * @return 归档策略实体
     */
    private MesArchivePolicy updateArchivePolicy(MesArchivePolicySaveRequest request, String code) {
        MesArchivePolicy entity = archivePolicyMapper.selectById(request.getId());
        if (entity == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "归档策略不存在：" + request.getId());
        }
        if (code != null && !code.equals(entity.getPolicyCode())) {
            assertPolicyCodeUnique(entity.getId(), code);
            entity.setPolicyCode(code);
        }
        if (request.getRetentionDays() != null) {
            entity.setRetentionDays(request.getRetentionDays());
        }
        if (request.getArchiveBatchSize() != null) {
            entity.setArchiveBatchSize(request.getArchiveBatchSize());
        }
        if (request.getCronExpr() != null) {
            entity.setCronExpr(request.getCronExpr());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        return entity;
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesArchivePolicyVO> pageArchivePolicies(MesArchivePolicyQueryRequest request) {
        LambdaQueryWrapper<MesArchivePolicy> wrapper = new LambdaQueryWrapper<MesArchivePolicy>()
                .like(StringUtils.hasText(request.getPolicyCode()), MesArchivePolicy::getPolicyCode,
                        request.getPolicyCode())
                .eq(StringUtils.hasText(request.getTableName()), MesArchivePolicy::getTableName,
                        request.getTableName())
                .eq(StringUtils.hasText(request.getStatus()), MesArchivePolicy::getStatus, request.getStatus())
                .orderByAsc(MesArchivePolicy::getPolicyCode);
        Page<MesArchivePolicy> page = archivePolicyMapper.selectPage(newPage(request.getCurrent(),
                request.getSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toArchivePolicyVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesArchivePolicyVO runArchive(Long id) {
        MesArchivePolicy policy = archivePolicyMapper.selectById(id);
        if (policy == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "归档策略不存在：" + id);
        }
        if (!"ENABLED".equals(policy.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "归档策略已停用：" + policy.getPolicyCode());
        }
        assertArchiveTable(policy.getTableName());
        LocalDateTime cutoff = LocalDate.now()
                .minusDays(policy.getRetentionDays() == null ? 365 : policy.getRetentionDays())
                .atStartOfDay();
        long candidates = sampleMapper.selectCount(new LambdaQueryWrapper<MesMetricSample>()
                .lt(MesMetricSample::getSampledAt, cutoff));
        policy.setLastRunAt(LocalDateTime.now());
        policy.setLastCandidateCount(candidates);
        if (archivePolicyMapper.updateById(policy) < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "归档策略已被其他用户修改，请刷新后重试");
        }
        return toArchivePolicyVO(policy);
    }

    /**
     * 校验采集设备编码唯一
     *
     * @param excludeId 排除的 ID
     * @param code      采集设备编码
     */
    private void assertDeviceCodeUnique(Long excludeId, String code) {
        long count = deviceMapper.selectCount(new LambdaQueryWrapper<MesCollectDevice>()
                .eq(MesCollectDevice::getDeviceCode, code)
                .ne(excludeId != null, MesCollectDevice::getId, excludeId));
        if (count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "采集设备编码已存在：" + code);
        }
    }

    /**
     * 校验采集点编码唯一
     *
     * @param excludeId 排除的 ID
     * @param code      采集点编码
     */
    private void assertPointCodeUnique(Long excludeId, String code) {
        long count = pointMapper.selectCount(new LambdaQueryWrapper<MesCollectionPoint>()
                .eq(MesCollectionPoint::getPointCode, code)
                .ne(excludeId != null, MesCollectionPoint::getId, excludeId));
        if (count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "采集点编码已存在：" + code);
        }
    }

    /**
     * 校验看板编码唯一
     *
     * @param excludeId 排除的 ID
     * @param code      看板编码
     */
    private void assertBoardCodeUnique(Long excludeId, String code) {
        long count = boardMapper.selectCount(new LambdaQueryWrapper<MesCollectBoardConfig>()
                .eq(MesCollectBoardConfig::getBoardCode, code)
                .ne(excludeId != null, MesCollectBoardConfig::getId, excludeId));
        if (count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "看板编码已存在：" + code);
        }
    }

    /**
     * 校验归档策略编码唯一
     *
     * @param excludeId 排除的 ID
     * @param code      策略编码
     */
    private void assertPolicyCodeUnique(Long excludeId, String code) {
        long count = archivePolicyMapper.selectCount(new LambdaQueryWrapper<MesArchivePolicy>()
                .eq(MesArchivePolicy::getPolicyCode, code)
                .ne(excludeId != null, MesArchivePolicy::getId, excludeId));
        if (count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "归档策略编码已存在：" + code);
        }
    }

    /**
     * 校验归档目标表在允许清单内
     *
     * @param tableName 目标表
     */
    private void assertArchiveTable(String tableName) {
        if (!ARCHIVE_TABLES.contains(tableName)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "归档目标表不受支持：" + tableName);
        }
    }

    /**
     * 构造分页参数（默认第 1 页、每页 10 条，上限 1000）
     *
     * @param current 当前页
     * @param size    每页条数
     * @param <T>     实体类型
     * @return 分页对象
     */
    private <T> Page<T> newPage(Long current, Long size) {
        long pageNo = current == null || current < 1 ? 1L : current;
        long pageSize = size == null || size < 1 ? 10L : Math.min(size, 1000L);
        return new Page<>(pageNo, pageSize);
    }

    /**
     * 拆分逗号分隔的编码列表
     *
     * @param codes 编码串
     * @return 编码列表
     */
    private List<String> splitCodes(String codes) {
        if (!StringUtils.hasText(codes)) {
            return new ArrayList<>();
        }
        return Arrays.stream(codes.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    /**
     * 解析 ISO 时间字符串
     *
     * @param value 时间串
     * @return 时间，空则 null
     */
    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return LocalDateTime.parse(value.trim());
    }

    /**
     * 去空白并转为 null
     *
     * @param value 原始值
     * @return 处理后值
     */
    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    /**
     * 空字符串取默认值
     *
     * @param value    原始值
     * @param fallback 默认值
     * @return 处理后值
     */
    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    /**
     * 采集设备实体转视图
     *
     * @param entity 采集设备实体
     * @return 采集设备视图
     */
    private MesCollectDeviceVO toDeviceVO(MesCollectDevice entity) {
        MesCollectDeviceVO vo = new MesCollectDeviceVO();
        vo.setId(entity.getId());
        vo.setDeviceCode(entity.getDeviceCode());
        vo.setDeviceName(entity.getDeviceName());
        vo.setDeviceType(entity.getDeviceType());
        vo.setProtocol(entity.getProtocol());
        vo.setIpAddress(entity.getIpAddress());
        vo.setLineId(entity.getLineId());
        vo.setLineCode(entity.getLineCode());
        vo.setStatus(entity.getStatus());
        vo.setLastHeartbeatAt(entity.getLastHeartbeatAt());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /**
     * 采集点实体转视图
     *
     * @param entity 采集点实体
     * @return 采集点视图
     */
    private MesCollectPointVO toPointVO(MesCollectionPoint entity) {
        MesCollectPointVO vo = new MesCollectPointVO();
        vo.setId(entity.getId());
        vo.setPointCode(entity.getPointCode());
        vo.setPointName(entity.getPointName());
        vo.setDeviceId(entity.getDeviceId());
        vo.setDeviceCode(entity.getDeviceCode());
        vo.setEquipmentId(entity.getEquipmentId());
        vo.setEquipmentCode(entity.getEquipmentCode());
        vo.setOperationCode(entity.getOperationCode());
        vo.setMetricType(entity.getMetricType());
        vo.setUnit(entity.getUnit());
        vo.setCollectIntervalSeconds(entity.getCollectIntervalSeconds());
        vo.setEnabled(entity.getEnabled());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /**
     * 样本实体转视图
     *
     * @param entity 样本实体
     * @return 样本视图
     */
    private MesMetricSampleVO toSampleVO(MesMetricSample entity) {
        MesMetricSampleVO vo = new MesMetricSampleVO();
        vo.setId(entity.getId());
        vo.setPointId(entity.getPointId());
        vo.setPointCode(entity.getPointCode());
        vo.setEquipmentId(entity.getEquipmentId());
        vo.setEquipmentCode(entity.getEquipmentCode());
        vo.setMetricValue(entity.getMetricValue());
        vo.setUnit(entity.getUnit());
        vo.setSampledAt(entity.getSampledAt());
        vo.setSeq(entity.getSeq());
        vo.setPayload(entity.getPayload());
        return vo;
    }

    /**
     * 看板配置实体转视图
     *
     * @param entity 看板配置实体
     * @return 看板视图
     */
    private MesCollectBoardVO toBoardVO(MesCollectBoardConfig entity) {
        MesCollectBoardVO vo = new MesCollectBoardVO();
        vo.setId(entity.getId());
        vo.setBoardCode(entity.getBoardCode());
        vo.setBoardName(entity.getBoardName());
        vo.setLineId(entity.getLineId());
        vo.setLineCode(entity.getLineCode());
        vo.setRefreshSeconds(entity.getRefreshSeconds());
        vo.setMetricCodes(entity.getMetricCodes());
        vo.setLayout(entity.getLayout());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /**
     * 归档策略实体转视图
     *
     * @param entity 归档策略实体
     * @return 归档策略视图
     */
    private MesArchivePolicyVO toArchivePolicyVO(MesArchivePolicy entity) {
        MesArchivePolicyVO vo = new MesArchivePolicyVO();
        vo.setId(entity.getId());
        vo.setPolicyCode(entity.getPolicyCode());
        vo.setTableName(entity.getTableName());
        vo.setRetentionDays(entity.getRetentionDays());
        vo.setArchiveBatchSize(entity.getArchiveBatchSize());
        vo.setCronExpr(entity.getCronExpr());
        vo.setStatus(entity.getStatus());
        vo.setLastRunAt(entity.getLastRunAt());
        vo.setLastCandidateCount(entity.getLastCandidateCount());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
