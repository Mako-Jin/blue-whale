package com.bluewhale.mes.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.converter.MesConverter;
import com.bluewhale.mes.dto.MesWorkOrderQueryRequest;
import com.bluewhale.mes.dto.MesWorkOrderSaveRequest;
import com.bluewhale.mes.entity.MesWorkOrder;
import com.bluewhale.mes.mapper.MesWorkOrderMapper;
import com.bluewhale.mes.service.MesWorkOrderService;
import com.bluewhale.mes.vo.MesWorkOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 生产工单服务实现
 *
 * <p>工单编码与 ERP 生产订单号在租户内唯一（逻辑删除行仍占用，避免复用冲突）。
 * 工单状态按固定生命周期流转，终态不可再变更；仅 {@code RECEIVED / CANCELLED}
 * 允许删除，避免已投产工单被误删导致执行数据悬空。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesWorkOrderServiceImpl implements MesWorkOrderService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 已接收（新建默认状态） */
    private static final String STATUS_RECEIVED = "RECEIVED";

    /** 已派工 */
    private static final String STATUS_DISPATCHED = "DISPATCHED";

    /** 生产中 */
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    /** 已暂停 */
    private static final String STATUS_PAUSED = "PAUSED";

    /** 已完工 */
    private static final String STATUS_COMPLETED = "COMPLETED";

    /** 已关闭 */
    private static final String STATUS_CLOSED = "CLOSED";

    /** 已取消 */
    private static final String STATUS_CANCELLED = "CANCELLED";

    /** 默认优先级 */
    private static final String PRIORITY_NORMAL = "NORMAL";

    /** 允许删除的状态集合 */
    private static final Set<String> DELETABLE_STATUSES = Set.of(STATUS_RECEIVED, STATUS_CANCELLED);

    /** 工单状态机：键为当前状态，值为允许迁移的目标状态 */
    private static final Map<String, Set<String>> STATUS_TRANSITIONS = Map.of(
            STATUS_RECEIVED, Set.of(STATUS_DISPATCHED, STATUS_CANCELLED),
            STATUS_DISPATCHED, Set.of(STATUS_IN_PROGRESS, STATUS_CANCELLED),
            STATUS_IN_PROGRESS, Set.of(STATUS_PAUSED, STATUS_COMPLETED, STATUS_CANCELLED),
            STATUS_PAUSED, Set.of(STATUS_IN_PROGRESS, STATUS_COMPLETED, STATUS_CANCELLED),
            STATUS_COMPLETED, Set.of(STATUS_CLOSED),
            STATUS_CLOSED, Set.of(),
            STATUS_CANCELLED, Set.of());

    private final MesWorkOrderMapper mesWorkOrderMapper;

    @Override
    public PageResult<MesWorkOrderVO> page(MesWorkOrderQueryRequest request) {
        LambdaQueryWrapper<MesWorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getCode()), MesWorkOrder::getCode, request.getCode());
        wrapper.like(StringUtils.hasText(request.getName()), MesWorkOrder::getName, request.getName());
        wrapper.like(StringUtils.hasText(request.getErpOrderNo()), MesWorkOrder::getErpOrderNo,
                request.getErpOrderNo());
        wrapper.like(StringUtils.hasText(request.getItemCode()), MesWorkOrder::getItemCode, request.getItemCode());
        wrapper.eq(StringUtils.hasText(request.getStatus()), MesWorkOrder::getStatus, request.getStatus());
        wrapper.eq(StringUtils.hasText(request.getPriority()), MesWorkOrder::getPriority, request.getPriority());
        wrapper.orderByDesc(MesWorkOrder::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<MesWorkOrder> page = mesWorkOrderMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<MesWorkOrderVO> records = page.getRecords().stream().map(MesWorkOrderServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public MesWorkOrderVO getDetail(Long id) {
        return toVo(requireWorkOrder(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesWorkOrderVO create(MesWorkOrderSaveRequest request) {
        assertCodeUnique(request.getCode(), null);
        assertErpOrderNoUnique(request.getErpOrderNo(), null);
        MesWorkOrder workOrder = MesConverter.INSTANCE.toEntity(request);
        if (workOrder.getStatus() == null) {
            workOrder.setStatus(STATUS_RECEIVED);
        }
        if (workOrder.getPriority() == null) {
            workOrder.setPriority(PRIORITY_NORMAL);
        }
        workOrder.setErpOrderNo(trimToNull(workOrder.getErpOrderNo()));
        workOrder.setVersion(1);
        assertPlanTimeValid(workOrder);
        mesWorkOrderMapper.insert(workOrder);
        log.info("工单已创建: id={}, code={}, status={}", workOrder.getId(), workOrder.getCode(),
                workOrder.getStatus());
        return toVo(workOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesWorkOrderVO update(Long id, MesWorkOrderSaveRequest request) {
        MesWorkOrder workOrder = requireWorkOrder(id);
        String currentStatus = workOrder.getStatus();
        assertStatusTransition(currentStatus, request.getStatus());
        assertCodeUnique(request.getCode(), id);
        assertErpOrderNoUnique(request.getErpOrderNo(), id);
        MesConverter.INSTANCE.updateEntity(request, workOrder);
        if (workOrder.getStatus() == null) {
            workOrder.setStatus(currentStatus);
        }
        if (workOrder.getPriority() == null) {
            workOrder.setPriority(PRIORITY_NORMAL);
        }
        workOrder.setErpOrderNo(trimToNull(workOrder.getErpOrderNo()));
        assertPlanTimeValid(workOrder);
        if (mesWorkOrderMapper.updateById(workOrder) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.service.work-order-was-modified-refresh-retry", "工单已被其他用户修改，请刷新后重试");
        }
        log.info("工单已更新: id={}, code={}, status={}", id, workOrder.getCode(), workOrder.getStatus());
        return toVo(workOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        MesWorkOrder workOrder = requireWorkOrder(id);
        if (!DELETABLE_STATUSES.contains(workOrder.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.service.recipient-cancelled-work-order-can-deleted", "只有已接收或已取消的工单允许删除");
        }
        mesWorkOrderMapper.deleteById(id);
        log.info("工单已删除: id={}", id);
    }

    /**
     * 按 ID 查询未删除的工单。
     *
     * @param id 主键ID
     * @return 工单实体
     */
    private MesWorkOrder requireWorkOrder(Long id) {
        MesWorkOrder workOrder = id == null ? null : mesWorkOrderMapper.selectById(id);
        if (workOrder == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.service.work-order-found", "工单不存在");
        }
        return workOrder;
    }

    /**
     * 校验工单编码在租户内唯一。
     *
     * @param code      工单编码
     * @param excludeId 更新时排除自身ID，新增传 {@code null}
     */
    private void assertCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<MesWorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MesWorkOrder::getCode, code);
        if (excludeId != null) {
            wrapper.ne(MesWorkOrder::getId, excludeId);
        }
        if (mesWorkOrderMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.service.work-order-code-already-exists", "工单编码已存在");
        }
    }

    /**
     * 校验 ERP 生产订单号在租户内唯一。
     *
     * @param erpOrderNo ERP 生产订单号，为空时不校验
     * @param excludeId  更新时排除自身ID，新增传 {@code null}
     */
    private void assertErpOrderNoUnique(String erpOrderNo, Long excludeId) {
        if (!StringUtils.hasText(erpOrderNo)) {
            return;
        }
        LambdaQueryWrapper<MesWorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MesWorkOrder::getErpOrderNo, erpOrderNo.trim());
        if (excludeId != null) {
            wrapper.ne(MesWorkOrder::getId, excludeId);
        }
        if (mesWorkOrderMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.service.e-r-p-production-order-number", "ERP 生产订单号已存在：{0}", erpOrderNo.trim());
        }
    }

    /**
     * 校验工单状态迁移合法。
     *
     * @param from 当前状态
     * @param to   目标状态，为空表示不变更
     */
    private void assertStatusTransition(String from, String to) {
        if (to == null || Objects.equals(from, to)) {
            return;
        }
        Set<String> allowed = STATUS_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.service.work-order-status-cannot-change-from", "工单状态不能从 {0} 变更为 {1}", from, to);
        }
    }

    /**
     * 校验计划开工 / 完工时间顺序。
     *
     * @param workOrder 待保存工单
     */
    private void assertPlanTimeValid(MesWorkOrder workOrder) {
        if (workOrder.getPlannedStart() != null && workOrder.getPlannedEnd() != null
                && workOrder.getPlannedEnd().isBefore(workOrder.getPlannedStart())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.service.plan-completion-time-earlier-than-plan", "计划完工时间不能早于计划开工时间");
        }
    }

    /**
     * 去除首尾空白，空白串归一为 {@code null}，避免误用唯一索引。
     *
     * @param value 原始值
     * @return 归一化后的值
     */
    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 实体转视图对象
     *
     * @param workOrder 工单实体
     * @return 视图对象
     */
    private static MesWorkOrderVO toVo(MesWorkOrder workOrder) {
        return MesConverter.INSTANCE.toWorkOrderVo(workOrder);
    }
}
