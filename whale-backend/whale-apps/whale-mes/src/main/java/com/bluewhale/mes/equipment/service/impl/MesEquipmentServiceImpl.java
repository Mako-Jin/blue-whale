package com.bluewhale.mes.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.common.support.MesCodeGenerator;
import com.bluewhale.mes.equipment.dto.MesEquipmentQueryRequest;
import com.bluewhale.mes.equipment.dto.MesEquipmentSaveRequest;
import com.bluewhale.mes.equipment.dto.MesEquipmentStatusChangeRequest;
import com.bluewhale.mes.equipment.dto.MesMaintenanceOrderAcceptRequest;
import com.bluewhale.mes.equipment.dto.MesMaintenanceOrderCompleteRequest;
import com.bluewhale.mes.equipment.dto.MesMaintenanceOrderCreateRequest;
import com.bluewhale.mes.equipment.dto.MesMaintenancePlanSaveRequest;
import com.bluewhale.mes.equipment.dto.MesMaintenanceQueryRequest;
import com.bluewhale.mes.equipment.dto.MesOeeQueryRequest;
import com.bluewhale.mes.equipment.dto.MesOeeSnapshotRequest;
import com.bluewhale.mes.equipment.dto.MesRepairActionRequest;
import com.bluewhale.mes.equipment.dto.MesRepairCreateRequest;
import com.bluewhale.mes.equipment.dto.MesRepairQueryRequest;
import com.bluewhale.mes.equipment.dto.MesSparePartQueryRequest;
import com.bluewhale.mes.equipment.dto.MesSparePartSaveRequest;
import com.bluewhale.mes.equipment.dto.MesSparePartStockRequest;
import com.bluewhale.mes.equipment.entity.MesEquipment;
import com.bluewhale.mes.equipment.entity.MesEquipmentDowntime;
import com.bluewhale.mes.equipment.entity.MesEquipmentStatusEvent;
import com.bluewhale.mes.equipment.entity.MesMaintenanceOrder;
import com.bluewhale.mes.equipment.entity.MesMaintenancePlan;
import com.bluewhale.mes.equipment.entity.MesOeeSnapshot;
import com.bluewhale.mes.equipment.entity.MesRepairOrder;
import com.bluewhale.mes.equipment.entity.MesSparePart;
import com.bluewhale.mes.equipment.entity.MesSparePartMovement;
import com.bluewhale.mes.equipment.entity.MesSparePartStock;
import com.bluewhale.mes.equipment.mapper.MesEquipmentDowntimeMapper;
import com.bluewhale.mes.equipment.mapper.MesEquipmentMapper;
import com.bluewhale.mes.equipment.mapper.MesEquipmentStatusEventMapper;
import com.bluewhale.mes.equipment.mapper.MesMaintenanceOrderMapper;
import com.bluewhale.mes.equipment.mapper.MesMaintenancePlanMapper;
import com.bluewhale.mes.equipment.mapper.MesOeeSnapshotMapper;
import com.bluewhale.mes.equipment.mapper.MesRepairOrderMapper;
import com.bluewhale.mes.equipment.mapper.MesSparePartMapper;
import com.bluewhale.mes.equipment.mapper.MesSparePartMovementMapper;
import com.bluewhale.mes.equipment.mapper.MesSparePartStockMapper;
import com.bluewhale.mes.equipment.service.MesEquipmentService;
import com.bluewhale.mes.equipment.vo.MesEquipmentDowntimeVO;
import com.bluewhale.mes.equipment.vo.MesEquipmentGateVO;
import com.bluewhale.mes.equipment.vo.MesEquipmentStatusEventVO;
import com.bluewhale.mes.equipment.vo.MesEquipmentVO;
import com.bluewhale.mes.equipment.vo.MesMaintenanceOrderVO;
import com.bluewhale.mes.equipment.vo.MesMaintenancePlanVO;
import com.bluewhale.mes.equipment.vo.MesOeeSnapshotVO;
import com.bluewhale.mes.equipment.vo.MesRepairOrderVO;
import com.bluewhale.mes.equipment.vo.MesSparePartMovementVO;
import com.bluewhale.mes.equipment.vo.MesSparePartStockVO;
import com.bluewhale.mes.equipment.vo.MesSparePartVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * MES 设备应用服务实现
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MesEquipmentServiceImpl implements MesEquipmentService {

    private static final BigDecimal SIXTY = new BigDecimal("60");

    private static final BigDecimal ONE = BigDecimal.ONE;

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private static final String STATUS_FAULT = "FAULT";

    private static final String STATUS_OFFLINE = "OFFLINE";

    private static final String DEFAULT_FORMULA =
            "AVAILABILITY=RUN/(PLANNED-PLANNED_DT);PERFORMANCE=OUTPUT/RUN;QUALITY=GOOD/TOTAL;OEE=A*P*Q";

    private final MesEquipmentMapper equipmentMapper;

    private final MesEquipmentStatusEventMapper statusEventMapper;

    private final MesEquipmentDowntimeMapper downtimeMapper;

    private final MesMaintenancePlanMapper maintenancePlanMapper;

    private final MesMaintenanceOrderMapper maintenanceOrderMapper;

    private final MesRepairOrderMapper repairOrderMapper;

    private final MesSparePartMapper sparePartMapper;

    private final MesSparePartStockMapper sparePartStockMapper;

    private final MesSparePartMovementMapper sparePartMovementMapper;

    private final MesOeeSnapshotMapper oeeSnapshotMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesEquipmentVO saveEquipment(MesEquipmentSaveRequest request) {
        MesEquipment entity;
        String code = trimToNull(request.getEquipmentCode());
        if (request.getId() == null) {
            entity = new MesEquipment();
            if (code == null) {
                code = MesCodeGenerator.nextNo("EQ");
            }
            assertEquipmentCodeUnique(null, code);
            entity.setEquipmentCode(code);
            entity.setEquipmentType(defaultIfBlank(request.getEquipmentType(), "MACHINE"));
            entity.setStatusBaseline(defaultIfBlank(request.getStatusBaseline(), "RUNNING"));
            entity.setStatus(defaultIfBlank(request.getStatus(), entity.getStatusBaseline()));
            entity.setMaintenanceCycleDays(defaultIfNull(request.getMaintenanceCycleDays(), 30));
            entity.setEnabled(defaultIfNull(request.getEnabled(), 1));
        } else {
            entity = requireEquipment(request.getId());
            if (code != null && !code.equals(entity.getEquipmentCode())) {
                assertEquipmentCodeUnique(entity.getId(), code);
                entity.setEquipmentCode(code);
            }
            if (request.getStatusBaseline() != null) {
                entity.setStatusBaseline(request.getStatusBaseline());
            }
            if (request.getMaintenanceCycleDays() != null) {
                entity.setMaintenanceCycleDays(request.getMaintenanceCycleDays());
            }
            if (request.getEnabled() != null) {
                entity.setEnabled(request.getEnabled());
            }
            if (request.getEquipmentType() != null) {
                entity.setEquipmentType(request.getEquipmentType());
            }
        }
        entity.setEquipmentName(request.getEquipmentName());
        entity.setWorkshopId(request.getWorkshopId());
        entity.setWorkshopCode(request.getWorkshopCode());
        entity.setLineId(request.getLineId());
        entity.setLineCode(request.getLineCode());
        entity.setWorkCenterId(request.getWorkCenterId());
        entity.setWorkCenterCode(request.getWorkCenterCode());
        entity.setStationCode(request.getStationCode());
        entity.setModelNo(request.getModelNo());
        entity.setManufacturer(request.getManufacturer());
        entity.setPurchaseDate(request.getPurchaseDate());
        entity.setRemark(request.getRemark());

        if (entity.getId() == null) {
            equipmentMapper.insert(entity);
            LocalDateTime now = LocalDateTime.now();
            MesEquipmentStatusEvent event = new MesEquipmentStatusEvent();
            event.setEquipmentId(entity.getId());
            event.setEquipmentCode(entity.getEquipmentCode());
            event.setToStatus(entity.getStatus());
            event.setEventType("MANUAL");
            event.setOccurredAt(now);
            event.setOperator("SYSTEM");
            event.setRemark("设备建档初始化状态");
            statusEventMapper.insert(event);
            if (STATUS_FAULT.equals(entity.getStatus())) {
                openDowntime(entity, event, null);
            }
        } else if (equipmentMapper.updateById(entity) < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.equipment-was-modified-refresh-retry", "设备已被其他用户修改，请刷新后重试");
        }
        return toEquipmentVO(entity);
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesEquipmentVO> pageEquipments(MesEquipmentQueryRequest request) {
        LambdaQueryWrapper<MesEquipment> wrapper = new LambdaQueryWrapper<MesEquipment>()
                .like(StringUtils.hasText(request.getEquipmentCode()), MesEquipment::getEquipmentCode,
                        trimToNull(request.getEquipmentCode()))
                .like(StringUtils.hasText(request.getEquipmentName()), MesEquipment::getEquipmentName,
                        trimToNull(request.getEquipmentName()))
                .eq(StringUtils.hasText(request.getEquipmentType()), MesEquipment::getEquipmentType,
                        request.getEquipmentType())
                .eq(request.getLineId() != null, MesEquipment::getLineId, request.getLineId())
                .eq(StringUtils.hasText(request.getStatus()), MesEquipment::getStatus, request.getStatus())
                .eq(request.getEnabled() != null, MesEquipment::getEnabled, request.getEnabled())
                .orderByDesc(MesEquipment::getCreateTime);
        Page<MesEquipment> page = equipmentMapper.selectPage(newPage(request.getCurrent(), request.getSize()),
                wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toEquipmentVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** {@inheritDoc} */
    @Override
    public MesEquipmentVO getEquipment(Long id) {
        return toEquipmentVO(requireEquipment(id));
    }

    /** {@inheritDoc} */
    @Override
    public MesEquipmentGateVO gate(String equipmentCode) {
        MesEquipmentGateVO vo = new MesEquipmentGateVO();
        vo.setEquipmentCode(equipmentCode);
        MesEquipment equipment = equipmentMapper.selectOne(new LambdaQueryWrapper<MesEquipment>()
                .eq(MesEquipment::getEquipmentCode, equipmentCode));
        if (equipment == null) {
            vo.setPassed(Boolean.FALSE);
            vo.setMessage("设备不存在");
            return vo;
        }
        vo.setStatus(equipment.getStatus());
        if (Objects.equals(equipment.getEnabled(), 0)) {
            vo.setPassed(Boolean.FALSE);
            vo.setMessage("设备已停用");
            return vo;
        }
        if (STATUS_FAULT.equals(equipment.getStatus())) {
            vo.setPassed(Boolean.FALSE);
            vo.setMessage("设备处于故障状态，禁止开工");
            return vo;
        }
        if (STATUS_OFFLINE.equals(equipment.getStatus())) {
            vo.setPassed(Boolean.FALSE);
            vo.setMessage("设备离线，禁止开工");
            return vo;
        }
        vo.setPassed(Boolean.TRUE);
        vo.setMessage("设备可开工");
        return vo;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesEquipmentStatusEventVO changeStatus(Long id, MesEquipmentStatusChangeRequest request) {
        MesEquipment equipment = requireEquipment(id);
        String sourceEventId = trimToNull(request.getSourceEventId());
        if (sourceEventId != null) {
            MesEquipmentStatusEvent existing = statusEventMapper.selectOne(new LambdaQueryWrapper<MesEquipmentStatusEvent>()
                    .eq(MesEquipmentStatusEvent::getEquipmentId, id)
                    .eq(MesEquipmentStatusEvent::getSourceEventId, sourceEventId)
                    .last("LIMIT 1"));
            if (existing != null) {
                return toStatusEventVO(existing);
            }
        }
        LocalDateTime occurredAt = request.getOccurredAt() == null ? LocalDateTime.now() : request.getOccurredAt();
        MesEquipmentStatusEvent last = statusEventMapper.selectOne(new LambdaQueryWrapper<MesEquipmentStatusEvent>()
                .eq(MesEquipmentStatusEvent::getEquipmentId, id)
                .orderByDesc(MesEquipmentStatusEvent::getOccurredAt)
                .last("LIMIT 1"));
        long durationSeconds = 0L;
        if (last != null && last.getOccurredAt() != null) {
            durationSeconds = Math.max(0L, Duration.between(last.getOccurredAt(), occurredAt).getSeconds());
        }
        String fromStatus = equipment.getStatus();
        MesEquipmentStatusEvent event = new MesEquipmentStatusEvent();
        event.setEquipmentId(equipment.getId());
        event.setEquipmentCode(equipment.getEquipmentCode());
        event.setFromStatus(fromStatus);
        event.setToStatus(request.getToStatus());
        event.setEventType(defaultIfBlank(request.getEventType(), "MANUAL"));
        event.setOccurredAt(occurredAt);
        event.setDurationSeconds(durationSeconds);
        event.setOperator(request.getOperator());
        event.setSourceEventId(sourceEventId);
        event.setRemark(request.getRemark());
        statusEventMapper.insert(event);

        equipment.setStatus(request.getToStatus());
        if (equipmentMapper.updateById(equipment) < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.equipment-was-modified-refresh-retry-2", "设备已被其他用户修改，请刷新后重试");
        }
        if (STATUS_FAULT.equals(request.getToStatus())) {
            openDowntime(equipment, event, request);
        } else if (STATUS_FAULT.equals(fromStatus)) {
            closeOpenDowntime(equipment, occurredAt);
        }
        return toStatusEventVO(event);
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesEquipmentStatusEventVO> pageStatusEvents(Long equipmentId, Long current, Long size) {
        LambdaQueryWrapper<MesEquipmentStatusEvent> wrapper = new LambdaQueryWrapper<MesEquipmentStatusEvent>()
                .eq(equipmentId != null, MesEquipmentStatusEvent::getEquipmentId, equipmentId)
                .orderByDesc(MesEquipmentStatusEvent::getOccurredAt);
        Page<MesEquipmentStatusEvent> page = statusEventMapper.selectPage(newPage(current, size), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toStatusEventVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesEquipmentDowntimeVO> pageDowntimes(Long equipmentId, Long current, Long size) {
        LambdaQueryWrapper<MesEquipmentDowntime> wrapper = new LambdaQueryWrapper<MesEquipmentDowntime>()
                .eq(equipmentId != null, MesEquipmentDowntime::getEquipmentId, equipmentId)
                .orderByDesc(MesEquipmentDowntime::getStartAt);
        Page<MesEquipmentDowntime> page = downtimeMapper.selectPage(newPage(current, size), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toDowntimeVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesMaintenancePlanVO saveMaintenancePlan(MesMaintenancePlanSaveRequest request) {
        MesEquipment equipment = requireEquipment(request.getEquipmentId());
        MesMaintenancePlan entity;
        if (request.getId() == null) {
            entity = new MesMaintenancePlan();
            String planNo = trimToNull(request.getPlanNo());
            entity.setPlanNo(planNo == null ? MesCodeGenerator.nextNo("EQMP") : planNo);
            assertPlanNoUnique(null, entity.getPlanNo());
            entity.setStatus(defaultIfBlank(request.getStatus(), "ENABLED"));
        } else {
            entity = maintenancePlanMapper.selectById(request.getId());
            if (entity == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.equipment.maintenance-plan-found", "保养计划不存在：{0}", request.getId());
            }
            String planNo = trimToNull(request.getPlanNo());
            if (planNo != null && !planNo.equals(entity.getPlanNo())) {
                assertPlanNoUnique(entity.getId(), planNo);
                entity.setPlanNo(planNo);
            }
            if (request.getStatus() != null) {
                entity.setStatus(request.getStatus());
            }
        }
        Integer cycleDays = defaultIfNull(request.getCycleDays(),
                defaultIfNull(equipment.getMaintenanceCycleDays(), 30));
        entity.setEquipmentId(equipment.getId());
        entity.setEquipmentCode(equipment.getEquipmentCode());
        entity.setMaintenanceType(defaultIfBlank(request.getMaintenanceType(), "ROUTINE"));
        entity.setCycleDays(cycleDays);
        entity.setChecklist(request.getChecklist());
        entity.setRemark(request.getRemark());
        LocalDateTime base = entity.getLastDoneAt() == null ? LocalDateTime.now() : entity.getLastDoneAt();
        entity.setNextDueAt(base.plusDays(cycleDays));
        if (entity.getId() == null) {
            maintenancePlanMapper.insert(entity);
        } else if (maintenancePlanMapper.updateById(entity) < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.maintenance-plan-was-modified-refresh-retry", "保养计划已被其他用户修改，请刷新后重试");
        }
        return toMaintenancePlanVO(entity);
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesMaintenancePlanVO> pageMaintenancePlans(MesMaintenanceQueryRequest request) {
        LambdaQueryWrapper<MesMaintenancePlan> wrapper = new LambdaQueryWrapper<MesMaintenancePlan>()
                .like(StringUtils.hasText(request.getDocNo()), MesMaintenancePlan::getPlanNo, request.getDocNo())
                .eq(request.getEquipmentId() != null, MesMaintenancePlan::getEquipmentId, request.getEquipmentId())
                .like(StringUtils.hasText(request.getEquipmentCode()), MesMaintenancePlan::getEquipmentCode,
                        request.getEquipmentCode())
                .eq(StringUtils.hasText(request.getStatus()), MesMaintenancePlan::getStatus, request.getStatus())
                .orderByAsc(MesMaintenancePlan::getNextDueAt);
        Page<MesMaintenancePlan> page = maintenancePlanMapper.selectPage(newPage(request.getCurrent(),
                request.getSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toMaintenancePlanVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesMaintenanceOrderVO createMaintenanceOrder(MesMaintenanceOrderCreateRequest request) {
        MesMaintenancePlan plan = maintenancePlanMapper.selectById(request.getPlanId());
        if (plan == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.equipment.maintenance-plan-found-2", "保养计划不存在：{0}", request.getPlanId());
        }
        if (!"ENABLED".equals(plan.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.maintenance-plan-disabled-cannot-generate-work", "保养计划已停用，不可生成工单");
        }
        MesEquipment equipment = requireEquipment(plan.getEquipmentId());
        long opened = maintenanceOrderMapper.selectCount(new LambdaQueryWrapper<MesMaintenanceOrder>()
                .eq(MesMaintenanceOrder::getPlanId, plan.getId())
                .in(MesMaintenanceOrder::getStatus, Arrays.asList("PLANNED", "DUE", "OVERDUE", "IN_PROGRESS")));
        if (opened > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.plan-unfinished-maintenance-work-orders", "该计划存在未完成的保养工单");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dueAt = request.getDueAt() == null
                ? (plan.getNextDueAt() == null ? now.plusDays(defaultIfNull(plan.getCycleDays(), 30)) : plan.getNextDueAt())
                : request.getDueAt();
        MesMaintenanceOrder order = new MesMaintenanceOrder();
        order.setOrderNo(MesCodeGenerator.nextNo("EQMO"));
        order.setPlanId(plan.getId());
        order.setPlanNo(plan.getPlanNo());
        order.setEquipmentId(equipment.getId());
        order.setEquipmentCode(equipment.getEquipmentCode());
        order.setMaintenanceType(plan.getMaintenanceType());
        order.setChecklist(plan.getChecklist());
        order.setDueAt(dueAt);
        order.setStatus(dueAt.isAfter(now) ? "PLANNED" : "DUE");
        order.setAssignee(request.getAssignee());
        order.setRemark(request.getRemark());
        maintenanceOrderMapper.insert(order);
        return toMaintenanceOrderVO(order);
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesMaintenanceOrderVO> pageMaintenanceOrders(MesMaintenanceQueryRequest request) {
        LambdaQueryWrapper<MesMaintenanceOrder> wrapper = new LambdaQueryWrapper<MesMaintenanceOrder>()
                .like(StringUtils.hasText(request.getDocNo()), MesMaintenanceOrder::getOrderNo, request.getDocNo())
                .eq(request.getEquipmentId() != null, MesMaintenanceOrder::getEquipmentId, request.getEquipmentId())
                .like(StringUtils.hasText(request.getEquipmentCode()), MesMaintenanceOrder::getEquipmentCode,
                        request.getEquipmentCode())
                .eq(StringUtils.hasText(request.getStatus()), MesMaintenanceOrder::getStatus, request.getStatus())
                .orderByAsc(MesMaintenanceOrder::getDueAt);
        Page<MesMaintenanceOrder> page = maintenanceOrderMapper.selectPage(newPage(request.getCurrent(),
                request.getSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toMaintenanceOrderVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesMaintenanceOrderVO startMaintenanceOrder(Long id, String assignee) {
        MesMaintenanceOrder order = requireMaintenanceOrder(id);
        if (!Arrays.asList("PLANNED", "DUE", "OVERDUE").contains(order.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.maintenance-work-order-status-cannot-start", "当前保养工单状态不可开始：{0}",
                          order.getStatus());
        }
        order.setStatus("IN_PROGRESS");
        order.setStartedAt(LocalDateTime.now());
        if (StringUtils.hasText(assignee)) {
            order.setAssignee(assignee);
        }
        updateMaintenanceOrder(order);
        return toMaintenanceOrderVO(order);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesMaintenanceOrderVO completeMaintenanceOrder(Long id, MesMaintenanceOrderCompleteRequest request) {
        MesMaintenanceOrder order = requireMaintenanceOrder(id);
        if (!"IN_PROGRESS".equals(order.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.maintenance-work-order-status-cannot-completion", "当前保养工单状态不可完工：{0}",
                          order.getStatus());
        }
        List<String> items = splitChecklist(order.getChecklist());
        if (items.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.maintenance-work-order-configured-maintenance-item", "保养工单未配置保养项，不可完工");
        }
        Map<String, String> provided = new LinkedHashMap<>();
        for (String raw : request.getChecklistResults()) {
            String line = trimToNull(raw);
            if (line == null) {
                continue;
            }
            int index = line.indexOf('=');
            if (index <= 0 || index == line.length() - 1) {
                throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.maintenance-item-conclusion-format-should-item", "保养项结论格式应为：项名=结论");
            }
            String name = line.substring(0, index).trim();
            String conclusion = line.substring(index + 1).trim();
            if (!StringUtils.hasText(name) || !StringUtils.hasText(conclusion)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.maintenance-item-name-conclusion-empty", "保养项名称与结论均不能为空");
            }
            if (provided.putIfAbsent(name, conclusion) != null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.maintenance-item-conclusion-duplicate", "保养项结论重复：{0}", name);
            }
        }
        for (String item : items) {
            if (!provided.containsKey(item)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.maintenance-item-provide-conclusion", "保养项未给出结论：{0}", item);
            }
        }
        for (String name : provided.keySet()) {
            if (!items.contains(name)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.maintenance-item-belong-work-order", "保养项不属于该工单：{0}", name);
            }
        }
        String normalized = items.stream()
                .map(item -> item + "=" + provided.get(item))
                .collect(Collectors.joining(";"));
        order.setChecklistResult(normalized);
        order.setStatus("COMPLETED");
        order.setCompletedAt(LocalDateTime.now());
        order.setCompletedBy(request.getCompletedBy());
        if (StringUtils.hasText(request.getRemark())) {
            order.setRemark(request.getRemark());
        }
        updateMaintenanceOrder(order);
        return toMaintenanceOrderVO(order);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesMaintenanceOrderVO acceptMaintenanceOrder(Long id, MesMaintenanceOrderAcceptRequest request) {
        MesMaintenanceOrder order = requireMaintenanceOrder(id);
        if (!"COMPLETED".equals(order.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.maintenance-work-order-status-cannot-acceptance", "当前保养工单状态不可验收：{0}",
                          order.getStatus());
        }
        order.setStatus("ACCEPTED");
        order.setAcceptedBy(request.getAcceptedBy());
        order.setAcceptedAt(LocalDateTime.now());
        order.setConclusion(request.getConclusion());
        updateMaintenanceOrder(order);
        if (order.getPlanId() != null) {
            MesMaintenancePlan plan = maintenancePlanMapper.selectById(order.getPlanId());
            if (plan != null) {
                LocalDateTime lastDoneAt = order.getCompletedAt() == null ? LocalDateTime.now() : order.getCompletedAt();
                plan.setLastDoneAt(lastDoneAt);
                plan.setNextDueAt(lastDoneAt.plusDays(defaultIfNull(plan.getCycleDays(), 30)));
                maintenancePlanMapper.updateById(plan);
            }
        }
        return toMaintenanceOrderVO(order);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesRepairOrderVO createRepair(MesRepairCreateRequest request) {
        MesEquipment equipment = requireEquipment(request.getEquipmentId());
        MesRepairOrder order = new MesRepairOrder();
        order.setRepairNo(MesCodeGenerator.nextNo("EQR"));
        order.setEquipmentId(equipment.getId());
        order.setEquipmentCode(equipment.getEquipmentCode());
        order.setFaultDesc(request.getFaultDesc());
        order.setSeverity(defaultIfBlank(request.getSeverity(), "MAJOR"));
        order.setStatus("REPORTED");
        order.setReportedBy(request.getReportedBy());
        order.setReportedAt(request.getReportedAt() == null ? LocalDateTime.now() : request.getReportedAt());
        order.setRemark(request.getRemark());
        repairOrderMapper.insert(order);
        return toRepairOrderVO(order);
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesRepairOrderVO> pageRepairs(MesRepairQueryRequest request) {
        LambdaQueryWrapper<MesRepairOrder> wrapper = new LambdaQueryWrapper<MesRepairOrder>()
                .like(StringUtils.hasText(request.getRepairNo()), MesRepairOrder::getRepairNo, request.getRepairNo())
                .eq(request.getEquipmentId() != null, MesRepairOrder::getEquipmentId, request.getEquipmentId())
                .like(StringUtils.hasText(request.getEquipmentCode()), MesRepairOrder::getEquipmentCode,
                        request.getEquipmentCode())
                .eq(StringUtils.hasText(request.getStatus()), MesRepairOrder::getStatus, request.getStatus())
                .eq(StringUtils.hasText(request.getSeverity()), MesRepairOrder::getSeverity, request.getSeverity())
                .orderByDesc(MesRepairOrder::getReportedAt);
        Page<MesRepairOrder> page = repairOrderMapper.selectPage(newPage(request.getCurrent(), request.getSize()),
                wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toRepairOrderVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesRepairOrderVO assignRepair(Long id, MesRepairActionRequest request) {
        MesRepairOrder order = requireRepairOrder(id);
        if (!"REPORTED".equals(order.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.maintenance-work-order-status-cannot-dispatch", "当前维修工单状态不可派工：{0}",
                          order.getStatus());
        }
        if (!StringUtils.hasText(request.getAssignee())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.maintenance-person-empty", "维修人不能为空");
        }
        order.setStatus("ASSIGNED");
        order.setAssignee(request.getAssignee());
        order.setAssignedAt(LocalDateTime.now());
        updateRepairOrder(order);
        return toRepairOrderVO(order);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesRepairOrderVO startRepair(Long id, MesRepairActionRequest request) {
        MesRepairOrder order = requireRepairOrder(id);
        if (!"ASSIGNED".equals(order.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.maintenance-work-order-status-cannot-start-2", "当前维修工单状态不可开始维修：{0}",
                          order.getStatus());
        }
        order.setStatus("REPAIRING");
        order.setStartedAt(LocalDateTime.now());
        updateRepairOrder(order);
        return toRepairOrderVO(order);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesRepairOrderVO verifyRepair(Long id, MesRepairActionRequest request) {
        MesRepairOrder order = requireRepairOrder(id);
        if (!"REPAIRING".equals(order.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.maintenance-work-order-status-cannot-verification", "当前维修工单状态不可验证：{0}",
                          order.getStatus());
        }
        if (!StringUtils.hasText(request.getVerifiedBy())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.verification-person-empty", "验证人不能为空");
        }
        if (!StringUtils.hasText(request.getVerifyConclusion())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.verification-conclusion-empty", "验证结论不能为空");
        }
        order.setStatus("VERIFIED");
        order.setRepairDesc(request.getRepairDesc());
        order.setVerifiedBy(request.getVerifiedBy());
        order.setVerifiedAt(LocalDateTime.now());
        order.setVerifyConclusion(request.getVerifyConclusion());
        updateRepairOrder(order);
        return toRepairOrderVO(order);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesRepairOrderVO closeRepair(Long id, MesRepairActionRequest request) {
        MesRepairOrder order = requireRepairOrder(id);
        if (!"VERIFIED".equals(order.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.maintenance-work-order-status-cannot-close", "当前维修工单状态不可关闭：{0}",
                          order.getStatus());
        }
        LocalDateTime now = LocalDateTime.now();
        order.setStatus("CLOSED");
        order.setClosedAt(now);
        if (StringUtils.hasText(request.getRemark())) {
            order.setRemark(request.getRemark());
        }
        MesEquipmentDowntime downtime = findOpenDowntime(order.getEquipmentId());
        if (downtime != null) {
            BigDecimal minutes = minutesBetween(downtime.getStartAt(), now);
            downtime.setEndAt(now);
            downtime.setDurationMinutes(minutes);
            if (downtimeMapper.updateById(downtime) < 1) {
                throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.downtime-record-was-modified-refresh-retry", "停机记录已被其他用户修改，请刷新后重试");
            }
            order.setDowntimeMinutes(minutes);
        }
        updateRepairOrder(order);
        return toRepairOrderVO(order);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesSparePartVO saveSparePart(MesSparePartSaveRequest request) {
        MesSparePart entity;
        String code = trimToNull(request.getPartCode());
        if (request.getId() == null) {
            entity = new MesSparePart();
            if (code == null) {
                code = MesCodeGenerator.nextNo("EQSP");
            }
            assertPartCodeUnique(null, code);
            entity.setPartCode(code);
            entity.setStatus(defaultIfBlank(request.getStatus(), "ENABLED"));
            entity.setUom(defaultIfBlank(request.getUom(), "PCS"));
            entity.setSafetyStock(request.getSafetyStock() == null ? ZERO : request.getSafetyStock());
        } else {
            entity = sparePartMapper.selectById(request.getId());
            if (entity == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.equipment.spare-part-found", "备件不存在：{0}", request.getId());
            }
            if (code != null && !code.equals(entity.getPartCode())) {
                assertPartCodeUnique(entity.getId(), code);
                entity.setPartCode(code);
            }
            if (request.getStatus() != null) {
                entity.setStatus(request.getStatus());
            }
            if (request.getUom() != null) {
                entity.setUom(request.getUom());
            }
            if (request.getSafetyStock() != null) {
                entity.setSafetyStock(request.getSafetyStock());
            }
        }
        entity.setPartName(request.getPartName());
        entity.setSpec(request.getSpec());
        entity.setRemark(request.getRemark());
        if (entity.getId() == null) {
            sparePartMapper.insert(entity);
        } else if (sparePartMapper.updateById(entity) < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.spare-part-was-modified-refresh-retry", "备件已被其他用户修改，请刷新后重试");
        }
        return toSparePartVO(entity);
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesSparePartVO> pageSpareParts(MesSparePartQueryRequest request) {
        LambdaQueryWrapper<MesSparePart> wrapper = new LambdaQueryWrapper<MesSparePart>()
                .like(StringUtils.hasText(request.getPartCode()), MesSparePart::getPartCode, request.getPartCode())
                .like(StringUtils.hasText(request.getPartName()), MesSparePart::getPartName, request.getPartName())
                .eq(StringUtils.hasText(request.getStatus()), MesSparePart::getStatus, request.getStatus())
                .orderByAsc(MesSparePart::getPartCode);
        Page<MesSparePart> page = sparePartMapper.selectPage(newPage(request.getCurrent(), request.getSize()),
                wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toSparePartVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesSparePartStockVO> pageSparePartStocks(MesSparePartQueryRequest request) {
        LambdaQueryWrapper<MesSparePartStock> wrapper = new LambdaQueryWrapper<MesSparePartStock>()
                .like(StringUtils.hasText(request.getPartCode()), MesSparePartStock::getPartCode,
                        request.getPartCode())
                .eq(StringUtils.hasText(request.getWarehouseCode()), MesSparePartStock::getWarehouseCode,
                        request.getWarehouseCode())
                .orderByAsc(MesSparePartStock::getPartCode);
        Page<MesSparePartStock> page = sparePartStockMapper.selectPage(newPage(request.getCurrent(),
                request.getSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toSparePartStockVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesSparePartMovementVO> pageSparePartMovements(MesSparePartQueryRequest request) {
        LambdaQueryWrapper<MesSparePartMovement> wrapper = new LambdaQueryWrapper<MesSparePartMovement>()
                .like(StringUtils.hasText(request.getPartCode()), MesSparePartMovement::getPartCode,
                        request.getPartCode())
                .eq(StringUtils.hasText(request.getWarehouseCode()), MesSparePartMovement::getWarehouseCode,
                        request.getWarehouseCode())
                .orderByDesc(MesSparePartMovement::getOccurredAt);
        Page<MesSparePartMovement> page = sparePartMovementMapper.selectPage(newPage(request.getCurrent(),
                request.getSize()), wrapper);
        return PageResult.of(page.getRecords().stream()
                .map(this::toSparePartMovementVO).collect(Collectors.toList()), page.getTotal(), page.getCurrent(),
                page.getSize());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesSparePartMovementVO stockIn(MesSparePartStockRequest request) {
        return changeStock(request, "IN");
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesSparePartMovementVO stockOut(MesSparePartStockRequest request) {
        return changeStock(request, "OUT");
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesOeeSnapshotVO computeOee(MesOeeSnapshotRequest request) {
        MesEquipment equipment = requireEquipment(request.getEquipmentId());
        BigDecimal plannedMinutes = request.getPlannedMinutes();
        BigDecimal plannedDowntime = request.getPlannedDowntimeMinutes() == null
                ? ZERO : request.getPlannedDowntimeMinutes();
        BigDecimal runMinutes = request.getRunMinutes();
        BigDecimal totalCount = request.getTotalCount();
        BigDecimal goodCount = request.getGoodCount();
        if (goodCount.compareTo(totalCount) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.qualified-output-greater-than-total-output", "合格产量不能大于总产量");
        }
        BigDecimal availableMinutes = plannedMinutes.subtract(plannedDowntime);
        if (availableMinutes.signum() <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.planned-downtime-time-less-than-plan", "计划停机时间必须小于计划生产时间");
        }
        if (runMinutes.compareTo(availableMinutes) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.actual-runtime-greater-than-available-planned", "实际运行时间不得大于可用计划时间");
        }
        BigDecimal availability = ratio(runMinutes, availableMinutes);
        BigDecimal theoreticalOutput = totalCount.multiply(request.getIdealCycleSeconds())
                .divide(SIXTY, 6, RoundingMode.HALF_UP);
        BigDecimal performance = ratio(theoreticalOutput, runMinutes);
        BigDecimal quality = ratio(goodCount, totalCount);
        BigDecimal oee = availability.multiply(performance).multiply(quality)
                .setScale(6, RoundingMode.HALF_UP);

        MesOeeSnapshot snapshot = oeeSnapshotMapper.selectOne(new LambdaQueryWrapper<MesOeeSnapshot>()
                .eq(MesOeeSnapshot::getEquipmentId, equipment.getId())
                .eq(MesOeeSnapshot::getShiftDate, request.getShiftDate())
                .eq(MesOeeSnapshot::getShiftCode, request.getShiftCode()));
        boolean insert = snapshot == null;
        if (insert) {
            snapshot = new MesOeeSnapshot();
            snapshot.setSnapshotNo(MesCodeGenerator.nextNo("EQOEE"));
            snapshot.setEquipmentId(equipment.getId());
            snapshot.setEquipmentCode(equipment.getEquipmentCode());
            snapshot.setShiftDate(request.getShiftDate());
            snapshot.setShiftCode(request.getShiftCode());
        }
        snapshot.setPlannedMinutes(plannedMinutes);
        snapshot.setPlannedDowntimeMinutes(plannedDowntime);
        snapshot.setRunMinutes(runMinutes);
        snapshot.setDowntimeMinutes(request.getDowntimeMinutes() == null ? ZERO : request.getDowntimeMinutes());
        snapshot.setTotalCount(totalCount);
        snapshot.setGoodCount(goodCount);
        snapshot.setIdealCycleSeconds(request.getIdealCycleSeconds());
        snapshot.setAvailability(availability);
        snapshot.setPerformance(performance);
        snapshot.setQuality(quality);
        snapshot.setOee(oee);
        snapshot.setFormulaConfig(defaultIfBlank(request.getFormulaConfig(), DEFAULT_FORMULA));
        snapshot.setComputedAt(LocalDateTime.now());
        snapshot.setRemark(request.getRemark());
        if (insert) {
            oeeSnapshotMapper.insert(snapshot);
        } else if (oeeSnapshotMapper.updateById(snapshot) < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.o-e-e-snapshot-was-modified", "OEE 快照已被其他用户修改，请刷新后重试");
        }
        return toOeeVO(snapshot);
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<MesOeeSnapshotVO> pageOee(MesOeeQueryRequest request) {
        LambdaQueryWrapper<MesOeeSnapshot> wrapper = new LambdaQueryWrapper<MesOeeSnapshot>()
                .eq(request.getEquipmentId() != null, MesOeeSnapshot::getEquipmentId, request.getEquipmentId())
                .like(StringUtils.hasText(request.getEquipmentCode()), MesOeeSnapshot::getEquipmentCode,
                        request.getEquipmentCode())
                .eq(StringUtils.hasText(request.getShiftDate()), MesOeeSnapshot::getShiftDate,
                        parseDate(request.getShiftDate()))
                .eq(StringUtils.hasText(request.getShiftCode()), MesOeeSnapshot::getShiftCode,
                        request.getShiftCode())
                .orderByDesc(MesOeeSnapshot::getShiftDate)
                .orderByAsc(MesOeeSnapshot::getShiftCode);
        Page<MesOeeSnapshot> page = oeeSnapshotMapper.selectPage(newPage(request.getCurrent(), request.getSize()),
                wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toOeeVO).collect(Collectors.toList()),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 变更备件库存并写入流水
     *
     * @param request      出入库请求
     * @param movementType 流水类型 IN / OUT
     * @return 流水视图
     */
    private MesSparePartMovementVO changeStock(MesSparePartStockRequest request, String movementType) {
        MesSparePart part = sparePartMapper.selectOne(new LambdaQueryWrapper<MesSparePart>()
                .eq(MesSparePart::getPartCode, request.getPartCode()));
        if (part == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.equipment.spare-part-found-2", "备件不存在：{0}", request.getPartCode());
        }
        String warehouseCode = defaultIfBlank(request.getWarehouseCode(), "DEFAULT");
        MesSparePartStock stock = sparePartStockMapper.selectOne(new LambdaQueryWrapper<MesSparePartStock>()
                .eq(MesSparePartStock::getPartId, part.getId())
                .eq(MesSparePartStock::getWarehouseCode, warehouseCode));
        if (stock == null) {
            stock = new MesSparePartStock();
            stock.setPartId(part.getId());
            stock.setPartCode(part.getPartCode());
            stock.setWarehouseCode(warehouseCode);
            stock.setQuantity(ZERO);
        }
        BigDecimal before = stock.getQuantity() == null ? ZERO : stock.getQuantity();
        BigDecimal after = "OUT".equals(movementType)
                ? before.subtract(request.getQuantity())
                : before.add(request.getQuantity());
        if (after.signum() < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.spare-part-inventory-insufficient-inventory", "备件库存不足，当前库存：{0}",
                          before.toPlainString());
        }
        stock.setQuantity(after);
        if (stock.getId() == null) {
            sparePartStockMapper.insert(stock);
        } else if (sparePartStockMapper.updateById(stock) < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.spare-part-inventory-was-modified-refresh", "备件库存已被其他用户修改，请刷新后重试");
        }
        MesSparePartMovement movement = new MesSparePartMovement();
        movement.setMovementNo(MesCodeGenerator.nextNo("EQMV"));
        movement.setPartId(part.getId());
        movement.setPartCode(part.getPartCode());
        movement.setStockId(stock.getId());
        movement.setWarehouseCode(warehouseCode);
        movement.setMovementType(movementType);
        movement.setQuantity(request.getQuantity());
        movement.setBeforeQuantity(before);
        movement.setAfterQuantity(after);
        movement.setRefNo(request.getRefNo());
        movement.setOperator(request.getOperator());
        movement.setOccurredAt(LocalDateTime.now());
        movement.setRemark(request.getRemark());
        sparePartMovementMapper.insert(movement);
        return toSparePartMovementVO(movement);
    }

    /**
     * 开启停机记录（同设备仅允许一条未关闭记录）
     *
     * @param equipment 设备
     * @param event     触发事件
     * @param request   状态变更请求（可为空）
     */
    private void openDowntime(MesEquipment equipment, MesEquipmentStatusEvent event,
                              MesEquipmentStatusChangeRequest request) {
        MesEquipmentDowntime existing = findOpenDowntime(equipment.getId());
        if (existing != null) {
            return;
        }
        MesEquipmentDowntime downtime = new MesEquipmentDowntime();
        downtime.setDowntimeNo(MesCodeGenerator.nextNo("EQDT"));
        downtime.setEquipmentId(equipment.getId());
        downtime.setEquipmentCode(equipment.getEquipmentCode());
        downtime.setStatusEventId(event.getId());
        String reasonCode = request == null ? null : request.getReasonCode();
        String reasonName = request == null ? null : request.getReasonName();
        downtime.setReasonCode(defaultIfBlank(reasonCode, "FAULT"));
        downtime.setReasonName(defaultIfBlank(reasonName, "设备故障"));
        downtime.setPlannedFlag(0);
        downtime.setStartAt(event.getOccurredAt());
        downtimeMapper.insert(downtime);
    }

    /**
     * 关闭未结束的停机记录并回填时长
     *
     * @param equipment 设备
     * @param endAt     结束时间
     */
    private void closeOpenDowntime(MesEquipment equipment, LocalDateTime endAt) {
        MesEquipmentDowntime downtime = findOpenDowntime(equipment.getId());
        if (downtime == null) {
            return;
        }
        downtime.setEndAt(endAt);
        downtime.setDurationMinutes(minutesBetween(downtime.getStartAt(), endAt));
        if (downtimeMapper.updateById(downtime) < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.downtime-record-was-modified-refresh-retry-2", "停机记录已被其他用户修改，请刷新后重试");
        }
    }

    /**
     * 查询设备当前未关闭的停机记录
     *
     * @param equipmentId 设备 ID
     * @return 停机记录，无则 null
     */
    private MesEquipmentDowntime findOpenDowntime(Long equipmentId) {
        return downtimeMapper.selectOne(new LambdaQueryWrapper<MesEquipmentDowntime>()
                .eq(MesEquipmentDowntime::getEquipmentId, equipmentId)
                .isNull(MesEquipmentDowntime::getEndAt)
                .orderByDesc(MesEquipmentDowntime::getStartAt)
                .last("LIMIT 1"));
    }

    /**
     * 加载设备，不存在则抛异常
     *
     * @param id 设备 ID
     * @return 设备
     */
    private MesEquipment requireEquipment(Long id) {
        MesEquipment equipment = equipmentMapper.selectById(id);
        if (equipment == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.equipment.equipment-found", "设备不存在：{0}", id);
        }
        return equipment;
    }

    /**
     * 加载保养工单，不存在则抛异常
     *
     * @param id 工单 ID
     * @return 保养工单
     */
    private MesMaintenanceOrder requireMaintenanceOrder(Long id) {
        MesMaintenanceOrder order = maintenanceOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.equipment.maintenance-work-order-found", "保养工单不存在：{0}", id);
        }
        return order;
    }

    /**
     * 加载维修工单，不存在则抛异常
     *
     * @param id 工单 ID
     * @return 维修工单
     */
    private MesRepairOrder requireRepairOrder(Long id) {
        MesRepairOrder order = repairOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.equipment.maintenance-work-order-found-2", "维修工单不存在：{0}", id);
        }
        return order;
    }

    /**
     * 更新保养工单并校验乐观锁
     *
     * @param order 保养工单
     */
    private void updateMaintenanceOrder(MesMaintenanceOrder order) {
        if (maintenanceOrderMapper.updateById(order) < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.maintenance-work-order-was-modified-refresh", "保养工单已被其他用户修改，请刷新后重试");
        }
    }

    /**
     * 更新维修工单并校验乐观锁
     *
     * @param order 维修工单
     */
    private void updateRepairOrder(MesRepairOrder order) {
        if (repairOrderMapper.updateById(order) < 1) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.maintenance-work-order-was-modified-refresh-2", "维修工单已被其他用户修改，请刷新后重试");
        }
    }

    /**
     * 校验设备编码唯一
     *
     * @param excludeId 排除的 ID
     * @param code      设备编码
     */
    private void assertEquipmentCodeUnique(Long excludeId, String code) {
        long count = equipmentMapper.selectCount(new LambdaQueryWrapper<MesEquipment>()
                .eq(MesEquipment::getEquipmentCode, code)
                .ne(excludeId != null, MesEquipment::getId, excludeId));
        if (count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.equipment-code-already-exists", "设备编码已存在：{0}", code);
        }
    }

    /**
     * 校验保养计划编号唯一
     *
     * @param excludeId 排除的 ID
     * @param planNo    计划编号
     */
    private void assertPlanNoUnique(Long excludeId, String planNo) {
        long count = maintenancePlanMapper.selectCount(new LambdaQueryWrapper<MesMaintenancePlan>()
                .eq(MesMaintenancePlan::getPlanNo, planNo)
                .ne(excludeId != null, MesMaintenancePlan::getId, excludeId));
        if (count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.maintenance-plan-number-already-exists", "保养计划编号已存在：{0}", planNo);
        }
    }

    /**
     * 校验备件编码唯一
     *
     * @param excludeId 排除的 ID
     * @param code      备件编码
     */
    private void assertPartCodeUnique(Long excludeId, String code) {
        long count = sparePartMapper.selectCount(new LambdaQueryWrapper<MesSparePart>()
                .eq(MesSparePart::getPartCode, code)
                .ne(excludeId != null, MesSparePart::getId, excludeId));
        if (count > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.equipment.spare-part-code-already-exists", "备件编码已存在：{0}", code);
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
     * 拆分分号分隔的保养项
     *
     * @param checklist 保养项清单
     * @return 保养项列表
     */
    private List<String> splitChecklist(String checklist) {
        if (!StringUtils.hasText(checklist)) {
            return new ArrayList<>();
        }
        return Arrays.stream(checklist.split(";"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * 计算比率并截断到 [0, 1]
     *
     * @param numerator   分子
     * @param denominator 分母
     * @return 比率（6 位小数）
     */
    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return ZERO.setScale(6, RoundingMode.HALF_UP);
        }
        BigDecimal value = numerator.divide(denominator, 6, RoundingMode.HALF_UP);
        return value.compareTo(ONE) > 0 ? ONE.setScale(6, RoundingMode.HALF_UP) : value;
    }

    /**
     * 计算两个时间点之间的分钟数（非负）
     *
     * @param start 开始时间
     * @param end   结束时间
     * @return 分钟数
     */
    private BigDecimal minutesBetween(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            return ZERO;
        }
        long seconds = Math.max(0L, Duration.between(start, end).getSeconds());
        return BigDecimal.valueOf(seconds)
                .divide(SIXTY, 4, RoundingMode.HALF_UP);
    }

    /**
     * 解析日期字符串
     *
     * @param value yyyy-MM-dd
     * @return 日期，空则 null
     */
    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return LocalDate.parse(value);
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
     * null 取默认值
     *
     * @param value    原始值
     * @param fallback 默认值
     * @return 处理后值
     */
    private Integer defaultIfNull(Integer value, Integer fallback) {
        return value == null ? fallback : value;
    }

    /**
     * 设备实体转视图
     *
     * @param entity 设备实体
     * @return 设备视图
     */
    private MesEquipmentVO toEquipmentVO(MesEquipment entity) {
        MesEquipmentVO vo = new MesEquipmentVO();
        vo.setId(entity.getId());
        vo.setEquipmentCode(entity.getEquipmentCode());
        vo.setEquipmentName(entity.getEquipmentName());
        vo.setEquipmentType(entity.getEquipmentType());
        vo.setWorkshopId(entity.getWorkshopId());
        vo.setWorkshopCode(entity.getWorkshopCode());
        vo.setLineId(entity.getLineId());
        vo.setLineCode(entity.getLineCode());
        vo.setWorkCenterId(entity.getWorkCenterId());
        vo.setWorkCenterCode(entity.getWorkCenterCode());
        vo.setStationCode(entity.getStationCode());
        vo.setModelNo(entity.getModelNo());
        vo.setManufacturer(entity.getManufacturer());
        vo.setPurchaseDate(entity.getPurchaseDate());
        vo.setMaintenanceCycleDays(entity.getMaintenanceCycleDays());
        vo.setStatusBaseline(entity.getStatusBaseline());
        vo.setStatus(entity.getStatus());
        vo.setEnabled(entity.getEnabled());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /**
     * 状态事件实体转视图
     *
     * @param entity 状态事件实体
     * @return 状态事件视图
     */
    private MesEquipmentStatusEventVO toStatusEventVO(MesEquipmentStatusEvent entity) {
        MesEquipmentStatusEventVO vo = new MesEquipmentStatusEventVO();
        vo.setId(entity.getId());
        vo.setEquipmentId(entity.getEquipmentId());
        vo.setEquipmentCode(entity.getEquipmentCode());
        vo.setFromStatus(entity.getFromStatus());
        vo.setToStatus(entity.getToStatus());
        vo.setEventType(entity.getEventType());
        vo.setOccurredAt(entity.getOccurredAt());
        vo.setDurationSeconds(entity.getDurationSeconds());
        vo.setOperator(entity.getOperator());
        vo.setRemark(entity.getRemark());
        return vo;
    }

    /**
     * 停机实体转视图
     *
     * @param entity 停机实体
     * @return 停机视图
     */
    private MesEquipmentDowntimeVO toDowntimeVO(MesEquipmentDowntime entity) {
        MesEquipmentDowntimeVO vo = new MesEquipmentDowntimeVO();
        vo.setDowntimeNo(entity.getDowntimeNo());
        vo.setEquipmentId(entity.getEquipmentId());
        vo.setEquipmentCode(entity.getEquipmentCode());
        vo.setReasonCode(entity.getReasonCode());
        vo.setReasonName(entity.getReasonName());
        vo.setPlannedFlag(entity.getPlannedFlag());
        vo.setStartAt(entity.getStartAt());
        vo.setEndAt(entity.getEndAt());
        vo.setDurationMinutes(entity.getDurationMinutes());
        vo.setRemark(entity.getRemark());
        return vo;
    }

    /**
     * 保养计划实体转视图
     *
     * @param entity 计划实体
     * @return 计划视图
     */
    private MesMaintenancePlanVO toMaintenancePlanVO(MesMaintenancePlan entity) {
        MesMaintenancePlanVO vo = new MesMaintenancePlanVO();
        vo.setId(entity.getId());
        vo.setPlanNo(entity.getPlanNo());
        vo.setEquipmentId(entity.getEquipmentId());
        vo.setEquipmentCode(entity.getEquipmentCode());
        vo.setMaintenanceType(entity.getMaintenanceType());
        vo.setCycleDays(entity.getCycleDays());
        vo.setChecklist(entity.getChecklist());
        vo.setLastDoneAt(entity.getLastDoneAt());
        vo.setNextDueAt(entity.getNextDueAt());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /**
     * 保养工单实体转视图
     *
     * @param entity 工单实体
     * @return 工单视图
     */
    private MesMaintenanceOrderVO toMaintenanceOrderVO(MesMaintenanceOrder entity) {
        MesMaintenanceOrderVO vo = new MesMaintenanceOrderVO();
        vo.setId(entity.getId());
        vo.setOrderNo(entity.getOrderNo());
        vo.setPlanId(entity.getPlanId());
        vo.setPlanNo(entity.getPlanNo());
        vo.setEquipmentId(entity.getEquipmentId());
        vo.setEquipmentCode(entity.getEquipmentCode());
        vo.setMaintenanceType(entity.getMaintenanceType());
        vo.setChecklist(entity.getChecklist());
        vo.setDueAt(entity.getDueAt());
        vo.setStatus(entity.getStatus());
        vo.setAssignee(entity.getAssignee());
        vo.setStartedAt(entity.getStartedAt());
        vo.setCompletedAt(entity.getCompletedAt());
        vo.setCompletedBy(entity.getCompletedBy());
        vo.setAcceptedBy(entity.getAcceptedBy());
        vo.setAcceptedAt(entity.getAcceptedAt());
        vo.setConclusion(entity.getConclusion());
        vo.setChecklistResult(entity.getChecklistResult());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /**
     * 维修工单实体转视图
     *
     * @param entity 工单实体
     * @return 工单视图
     */
    private MesRepairOrderVO toRepairOrderVO(MesRepairOrder entity) {
        MesRepairOrderVO vo = new MesRepairOrderVO();
        vo.setId(entity.getId());
        vo.setRepairNo(entity.getRepairNo());
        vo.setEquipmentId(entity.getEquipmentId());
        vo.setEquipmentCode(entity.getEquipmentCode());
        vo.setFaultDesc(entity.getFaultDesc());
        vo.setSeverity(entity.getSeverity());
        vo.setStatus(entity.getStatus());
        vo.setReportedBy(entity.getReportedBy());
        vo.setReportedAt(entity.getReportedAt());
        vo.setAssignee(entity.getAssignee());
        vo.setAssignedAt(entity.getAssignedAt());
        vo.setStartedAt(entity.getStartedAt());
        vo.setRepairDesc(entity.getRepairDesc());
        vo.setVerifiedBy(entity.getVerifiedBy());
        vo.setVerifiedAt(entity.getVerifiedAt());
        vo.setClosedAt(entity.getClosedAt());
        vo.setVerifyConclusion(entity.getVerifyConclusion());
        vo.setDowntimeMinutes(entity.getDowntimeMinutes());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /**
     * 备件实体转视图
     *
     * @param entity 备件实体
     * @return 备件视图
     */
    private MesSparePartVO toSparePartVO(MesSparePart entity) {
        MesSparePartVO vo = new MesSparePartVO();
        vo.setId(entity.getId());
        vo.setPartCode(entity.getPartCode());
        vo.setPartName(entity.getPartName());
        vo.setSpec(entity.getSpec());
        vo.setUom(entity.getUom());
        vo.setSafetyStock(entity.getSafetyStock());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /**
     * 备件库存实体转视图
     *
     * @param entity 库存实体
     * @return 库存视图
     */
    private MesSparePartStockVO toSparePartStockVO(MesSparePartStock entity) {
        MesSparePartStockVO vo = new MesSparePartStockVO();
        vo.setId(entity.getId());
        vo.setPartId(entity.getPartId());
        vo.setPartCode(entity.getPartCode());
        vo.setWarehouseCode(entity.getWarehouseCode());
        vo.setQuantity(entity.getQuantity());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /**
     * 备件流水实体转视图
     *
     * @param entity 流水实体
     * @return 流水视图
     */
    private MesSparePartMovementVO toSparePartMovementVO(MesSparePartMovement entity) {
        MesSparePartMovementVO vo = new MesSparePartMovementVO();
        vo.setMovementNo(entity.getMovementNo());
        vo.setPartCode(entity.getPartCode());
        vo.setWarehouseCode(entity.getWarehouseCode());
        vo.setMovementType(entity.getMovementType());
        vo.setQuantity(entity.getQuantity());
        vo.setBeforeQuantity(entity.getBeforeQuantity());
        vo.setAfterQuantity(entity.getAfterQuantity());
        vo.setRefNo(entity.getRefNo());
        vo.setOperator(entity.getOperator());
        vo.setOccurredAt(entity.getOccurredAt());
        vo.setRemark(entity.getRemark());
        return vo;
    }

    /**
     * OEE 快照实体转视图
     *
     * @param entity 快照实体
     * @return 快照视图
     */
    private MesOeeSnapshotVO toOeeVO(MesOeeSnapshot entity) {
        MesOeeSnapshotVO vo = new MesOeeSnapshotVO();
        vo.setId(entity.getId());
        vo.setSnapshotNo(entity.getSnapshotNo());
        vo.setEquipmentId(entity.getEquipmentId());
        vo.setEquipmentCode(entity.getEquipmentCode());
        vo.setShiftDate(entity.getShiftDate() == null ? null : entity.getShiftDate().toString());
        vo.setShiftCode(entity.getShiftCode());
        vo.setPlannedMinutes(entity.getPlannedMinutes());
        vo.setPlannedDowntimeMinutes(entity.getPlannedDowntimeMinutes());
        vo.setRunMinutes(entity.getRunMinutes());
        vo.setDowntimeMinutes(entity.getDowntimeMinutes());
        vo.setTotalCount(entity.getTotalCount());
        vo.setGoodCount(entity.getGoodCount());
        vo.setIdealCycleSeconds(entity.getIdealCycleSeconds());
        vo.setAvailability(entity.getAvailability());
        vo.setPerformance(entity.getPerformance());
        vo.setQuality(entity.getQuality());
        vo.setOee(entity.getOee());
        vo.setFormulaConfig(entity.getFormulaConfig());
        vo.setComputedAt(entity.getComputedAt());
        return vo;
    }
}
