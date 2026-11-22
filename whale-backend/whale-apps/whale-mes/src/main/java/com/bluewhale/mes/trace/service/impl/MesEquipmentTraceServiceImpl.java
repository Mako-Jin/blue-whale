package com.bluewhale.mes.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.mes.equipment.entity.MesEquipment;
import com.bluewhale.mes.equipment.mapper.MesEquipmentMapper;
import com.bluewhale.mes.execution.entity.MesProductionReport;
import com.bluewhale.mes.execution.mapper.MesProductionReportMapper;
import com.bluewhale.mes.trace.entity.MesMaterialBatch;
import com.bluewhale.mes.trace.mapper.MesMaterialBatchMapper;
import com.bluewhale.mes.trace.service.MesEquipmentTraceService;
import com.bluewhale.mes.trace.vo.MesEquipmentTraceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MES 设备维度追溯服务实现。
 *
 * <p>设备与报工通过工位编码关联；设备未配置工位时只返回台账信息，不臆造关联关系。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MesEquipmentTraceServiceImpl implements MesEquipmentTraceService {

    /** 默认报工条数 */
    private static final int DEFAULT_LIMIT = 50;

    /** 最大报工条数 */
    private static final int MAX_LIMIT = 200;

    private final MesEquipmentMapper equipmentMapper;

    private final MesProductionReportMapper reportMapper;

    private final MesMaterialBatchMapper batchMapper;

    /** {@inheritDoc} */
    @Override
    public MesEquipmentTraceVO trace(String equipmentCode, Integer limit) {
        if (!StringUtils.hasText(equipmentCode)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.trace.equipment-code-empty", "设备编码不能为空");
        }
        MesEquipment equipment = equipmentMapper.selectOne(new LambdaQueryWrapper<MesEquipment>()
                .eq(MesEquipment::getEquipmentCode, equipmentCode)
                .last("LIMIT 1"));
        if (equipment == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.trace.equipment-found", "设备不存在：{0}", equipmentCode);
        }
        MesEquipmentTraceVO vo = new MesEquipmentTraceVO();
        vo.setEquipmentCode(equipment.getEquipmentCode());
        vo.setEquipmentName(equipment.getEquipmentName());
        vo.setWorkCenterCode(equipment.getWorkCenterCode());
        vo.setStationCode(equipment.getStationCode());
        vo.setStatus(equipment.getStatus());
        vo.setReports(new ArrayList<>());
        vo.setBatches(new ArrayList<>());
        if (!StringUtils.hasText(equipment.getStationCode())) {
            return vo;
        }
        int size = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        List<MesProductionReport> reports = reportMapper.selectList(new LambdaQueryWrapper<MesProductionReport>()
                .eq(MesProductionReport::getStationCode, equipment.getStationCode())
                .orderByDesc(MesProductionReport::getReportedAt)
                .last("LIMIT " + size));
        vo.setReports(reports.stream().map(this::toReportVo).toList());
        Set<Long> workOrderIds = reports.stream()
                .map(MesProductionReport::getWorkOrderId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (!workOrderIds.isEmpty()) {
            List<MesMaterialBatch> batches = batchMapper.selectList(new LambdaQueryWrapper<MesMaterialBatch>()
                    .in(MesMaterialBatch::getWorkOrderId, workOrderIds)
                    .orderByDesc(MesMaterialBatch::getProductionDate));
            vo.setBatches(batches.stream().map(this::toBatchVo).toList());
        }
        return vo;
    }

    /**
     * 报工实体转视图
     *
     * @param report 报工实体
     * @return 报工视图
     */
    private MesEquipmentTraceVO.Report toReportVo(MesProductionReport report) {
        MesEquipmentTraceVO.Report vo = new MesEquipmentTraceVO.Report();
        vo.setReportNo(report.getReportNo());
        vo.setTaskNo(report.getTaskNo());
        vo.setWorkOrderCode(report.getWorkOrderCode());
        vo.setOperationCode(report.getOperationCode());
        vo.setReportQuantity(report.getReportQuantity());
        vo.setQualifiedQuantity(report.getQualifiedQuantity());
        vo.setDefectQuantity(report.getDefectQuantity());
        vo.setReportedAt(report.getReportedAt());
        return vo;
    }

    /**
     * 批次实体转视图
     *
     * @param batch 批次实体
     * @return 批次视图
     */
    private MesEquipmentTraceVO.Batch toBatchVo(MesMaterialBatch batch) {
        MesEquipmentTraceVO.Batch vo = new MesEquipmentTraceVO.Batch();
        vo.setBatchNo(batch.getBatchNo());
        vo.setItemCode(batch.getItemCode());
        vo.setItemName(batch.getItemName());
        vo.setWorkOrderCode(batch.getWorkOrderCode());
        vo.setQuantity(batch.getQuantity());
        vo.setProductionDate(batch.getProductionDate());
        vo.setStatus(batch.getStatus());
        return vo;
    }
}
