package com.bluewhale.erp.mfg.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.erp.mfg.domain.model.MrpPlannedOrder;
import com.bluewhale.erp.mfg.domain.model.MrpQuery;
import com.bluewhale.erp.mfg.domain.model.MrpRun;
import com.bluewhale.erp.mfg.domain.model.MrpRunLine;
import com.bluewhale.erp.mfg.domain.model.MrpTrace;
import com.bluewhale.erp.mfg.domain.repository.MrpRepository;
import com.bluewhale.erp.mfg.infrastructure.persistence.entity.MrpPlannedOrderEntity;
import com.bluewhale.erp.mfg.infrastructure.persistence.entity.MrpRunEntity;
import com.bluewhale.erp.mfg.infrastructure.persistence.entity.MrpRunLineEntity;
import com.bluewhale.erp.mfg.infrastructure.persistence.entity.MrpTraceEntity;
import com.bluewhale.erp.mfg.infrastructure.persistence.mapper.MrpPlannedOrderMapper;
import com.bluewhale.erp.mfg.infrastructure.persistence.mapper.MrpRunLineMapper;
import com.bluewhale.erp.mfg.infrastructure.persistence.mapper.MrpRunMapper;
import com.bluewhale.erp.mfg.infrastructure.persistence.mapper.MrpTraceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

/**
 * MRP 运行与计划订单仓储实现（MyBatis-Plus）。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class MrpRepositoryImpl implements MrpRepository {

    /** 运行单号前缀 */
    private static final String RUN_PREFIX = "MRP-";

    private final MrpRunMapper mrpRunMapper;

    private final MrpRunLineMapper mrpRunLineMapper;

    private final MrpPlannedOrderMapper mrpPlannedOrderMapper;

    private final MrpTraceMapper mrpTraceMapper;

    @Override
    public String nextRunNo(String datePrefix) {
        String prefix = RUN_PREFIX + datePrefix + "-";
        long sequence = mrpRunMapper.selectCount(new LambdaQueryWrapper<MrpRunEntity>()
                .likeRight(MrpRunEntity::getRunNo, prefix)) + 1;
        return String.format("%s%04d", prefix, sequence);
    }

    @Override
    public void insertRun(MrpRun run) {
        MrpRunEntity entity = new MrpRunEntity();
        fillRun(entity, run);
        mrpRunMapper.insert(entity);
        run.setId(entity.getId());
        for (MrpRunLine line : run.getLines()) {
            MrpRunLineEntity lineEntity = new MrpRunLineEntity();
            fillRunLine(lineEntity, run.getId(), line);
            mrpRunLineMapper.insert(lineEntity);
        }
    }

    @Override
    public void updateRun(MrpRun run) {
        MrpRunEntity entity = new MrpRunEntity();
        entity.setId(run.getId());
        entity.setStatus(run.getStatus());
        entity.setDemandLineCount(run.getDemandLineCount());
        entity.setSnapshotLineCount(run.getSnapshotLineCount());
        entity.setPlannedOrderCount(run.getPlannedOrderCount());
        entity.setErrorMessage(run.getErrorMessage());
        entity.setEndTime(run.getEndTime());
        mrpRunMapper.updateById(entity);
    }

    @Override
    public List<MrpRun> pageRuns(MrpQuery query) {
        Page<MrpRunEntity> page = mrpRunMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), runFilter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long countRuns(MrpQuery query) {
        return mrpRunMapper.selectCount(runFilter(query));
    }

    @Override
    public Optional<MrpRun> findRunById(Long id) {
        MrpRunEntity entity = mrpRunMapper.selectById(id);
        if (entity == null) {
            return Optional.empty();
        }
        MrpRun run = toDomain(entity);
        run.setLines(mrpRunLineMapper.selectList(new LambdaQueryWrapper<MrpRunLineEntity>()
                        .eq(MrpRunLineEntity::getRunId, id)
                        .orderByAsc(MrpRunLineEntity::getLineNo))
                .stream().map(this::toDomain).toList());
        return Optional.of(run);
    }

    @Override
    public List<MrpPlannedOrder> listOrdersByRunId(Long runId) {
        return mrpPlannedOrderMapper.selectList(new LambdaQueryWrapper<MrpPlannedOrderEntity>()
                        .eq(MrpPlannedOrderEntity::getRunId, runId)
                        .orderByAsc(MrpPlannedOrderEntity::getOrderNo))
                .stream().map(this::toDomain).toList();
    }

    @Override
    public void insertPlannedOrders(List<MrpPlannedOrder> orders) {
        for (MrpPlannedOrder order : orders) {
            MrpPlannedOrderEntity entity = new MrpPlannedOrderEntity();
            fillOrder(entity, order);
            mrpPlannedOrderMapper.insert(entity);
            order.setId(entity.getId());
        }
    }

    @Override
    public void insertTraces(List<MrpTrace> traces) {
        for (MrpTrace trace : traces) {
            MrpTraceEntity entity = new MrpTraceEntity();
            fillTrace(entity, trace);
            mrpTraceMapper.insert(entity);
            trace.setId(entity.getId());
        }
    }

    @Override
    public List<MrpPlannedOrder> pageOrders(MrpQuery query) {
        Page<MrpPlannedOrderEntity> page = mrpPlannedOrderMapper.selectPage(
                new Page<>(Math.max(query.getCurrent(), 1L), Math.max(query.getSize(), 1L)), orderFilter(query));
        return page.getRecords().stream().map(this::toDomain).toList();
    }

    @Override
    public long countOrders(MrpQuery query) {
        return mrpPlannedOrderMapper.selectCount(orderFilter(query));
    }

    @Override
    public Optional<MrpPlannedOrder> findOrderById(Long id) {
        return Optional.ofNullable(mrpPlannedOrderMapper.selectById(id)).map(this::toDomain);
    }

    @Override
    public List<MrpTrace> listTracesByOrderId(Long plannedOrderId) {
        return mrpTraceMapper.selectList(new LambdaQueryWrapper<MrpTraceEntity>()
                        .eq(MrpTraceEntity::getPlannedOrderId, plannedOrderId)
                        .orderByAsc(MrpTraceEntity::getDemandLineNo)
                        .orderByAsc(MrpTraceEntity::getBomPath))
                .stream().map(this::toDomain).toList();
    }

    /**
     * 构建运行记录查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<MrpRunEntity> runFilter(MrpQuery query) {
        LambdaQueryWrapper<MrpRunEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.like(MrpRunEntity::getRunNo, query.getKeyword());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(MrpRunEntity::getStatus, query.getStatus());
        }
        return wrapper.orderByDesc(MrpRunEntity::getCreateTime).orderByDesc(MrpRunEntity::getId);
    }

    /**
     * 构建计划订单查询条件。
     *
     * @param query 查询条件
     * @return Lambda 条件
     */
    private LambdaQueryWrapper<MrpPlannedOrderEntity> orderFilter(MrpQuery query) {
        LambdaQueryWrapper<MrpPlannedOrderEntity> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(query.getKeyword())) {
            wrapper.and(w -> w.like(MrpPlannedOrderEntity::getOrderNo, query.getKeyword())
                    .or().like(MrpPlannedOrderEntity::getItemCode, query.getKeyword())
                    .or().like(MrpPlannedOrderEntity::getItemName, query.getKeyword()));
        }
        if (query.getRunId() != null) {
            wrapper.eq(MrpPlannedOrderEntity::getRunId, query.getRunId());
        }
        if (StringUtils.hasText(query.getOrderType())) {
            wrapper.eq(MrpPlannedOrderEntity::getOrderType, query.getOrderType());
        }
        if (StringUtils.hasText(query.getStatus())) {
            wrapper.eq(MrpPlannedOrderEntity::getStatus, query.getStatus());
        }
        return wrapper.orderByDesc(MrpPlannedOrderEntity::getCreateTime).orderByAsc(MrpPlannedOrderEntity::getOrderNo);
    }

    /**
     * 运行实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private MrpRun toDomain(MrpRunEntity entity) {
        MrpRun run = new MrpRun();
        run.setId(entity.getId());
        run.setRunNo(entity.getRunNo());
        run.setStatus(entity.getStatus());
        run.setDemandLineCount(entity.getDemandLineCount());
        run.setSnapshotLineCount(entity.getSnapshotLineCount());
        run.setPlannedOrderCount(entity.getPlannedOrderCount());
        run.setErrorMessage(entity.getErrorMessage());
        run.setRemark(entity.getRemark());
        run.setStartTime(entity.getStartTime());
        run.setEndTime(entity.getEndTime());
        return run;
    }

    /**
     * 输入行实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private MrpRunLine toDomain(MrpRunLineEntity entity) {
        MrpRunLine line = new MrpRunLine();
        line.setLineNo(entity.getLineNo());
        line.setItemCode(entity.getItemCode());
        line.setBomNo(entity.getBomNo());
        line.setBomVersion(entity.getBomVersion());
        line.setQuantity(entity.getQuantity());
        line.setDueDate(entity.getDueDate());
        return line;
    }

    /**
     * 计划订单实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private MrpPlannedOrder toDomain(MrpPlannedOrderEntity entity) {
        MrpPlannedOrder order = new MrpPlannedOrder();
        order.setId(entity.getId());
        order.setRunId(entity.getRunId());
        order.setOrderNo(entity.getOrderNo());
        order.setOrderType(entity.getOrderType());
        order.setItemId(entity.getItemId());
        order.setItemCode(entity.getItemCode());
        order.setItemName(entity.getItemName());
        order.setUom(entity.getUom());
        order.setGrossQuantity(entity.getGrossQuantity());
        order.setAvailableQuantity(entity.getAvailableQuantity());
        order.setNetQuantity(entity.getNetQuantity());
        order.setItemLevel(entity.getItemLevel());
        order.setBomNo(entity.getBomNo());
        order.setBomVersion(entity.getBomVersion());
        order.setNeedDate(entity.getNeedDate());
        order.setStatus(entity.getStatus());
        return order;
    }

    /**
     * 追溯实体转领域模型。
     *
     * @param entity 实体
     * @return 领域模型
     */
    private MrpTrace toDomain(MrpTraceEntity entity) {
        MrpTrace trace = new MrpTrace();
        trace.setId(entity.getId());
        trace.setRunId(entity.getRunId());
        trace.setPlannedOrderId(entity.getPlannedOrderId());
        trace.setDemandLineNo(entity.getDemandLineNo());
        trace.setBomNo(entity.getBomNo());
        trace.setBomVersion(entity.getBomVersion());
        trace.setBomPath(entity.getBomPath());
        trace.setDepth(entity.getDepth());
        trace.setUnitQty(entity.getUnitQty());
        trace.setLossRate(entity.getLossRate());
        trace.setGrossQuantity(entity.getGrossQuantity());
        return trace;
    }

    /**
     * 运行领域模型写入实体。
     *
     * @param entity 实体
     * @param run 领域模型
     */
    private void fillRun(MrpRunEntity entity, MrpRun run) {
        entity.setRunNo(run.getRunNo());
        entity.setStatus(run.getStatus());
        entity.setDemandLineCount(run.getDemandLineCount());
        entity.setSnapshotLineCount(run.getSnapshotLineCount());
        entity.setPlannedOrderCount(run.getPlannedOrderCount());
        entity.setErrorMessage(run.getErrorMessage());
        entity.setRemark(run.getRemark());
        entity.setStartTime(run.getStartTime());
        entity.setEndTime(run.getEndTime());
    }

    /**
     * 输入行领域模型写入实体。
     *
     * @param entity 实体
     * @param runId 运行记录 ID
     * @param line 领域模型
     */
    private void fillRunLine(MrpRunLineEntity entity, Long runId, MrpRunLine line) {
        entity.setRunId(runId);
        entity.setLineNo(line.getLineNo());
        entity.setItemCode(line.getItemCode());
        entity.setBomNo(line.getBomNo());
        entity.setBomVersion(line.getBomVersion());
        entity.setQuantity(line.getQuantity());
        entity.setDueDate(line.getDueDate());
    }

    /**
     * 计划订单领域模型写入实体。
     *
     * @param entity 实体
     * @param order 领域模型
     */
    private void fillOrder(MrpPlannedOrderEntity entity, MrpPlannedOrder order) {
        entity.setRunId(order.getRunId());
        entity.setOrderNo(order.getOrderNo());
        entity.setOrderType(order.getOrderType());
        entity.setItemId(order.getItemId());
        entity.setItemCode(order.getItemCode());
        entity.setItemName(order.getItemName());
        entity.setUom(order.getUom());
        entity.setGrossQuantity(order.getGrossQuantity());
        entity.setAvailableQuantity(order.getAvailableQuantity());
        entity.setNetQuantity(order.getNetQuantity());
        entity.setItemLevel(order.getItemLevel());
        entity.setBomNo(order.getBomNo());
        entity.setBomVersion(order.getBomVersion());
        entity.setNeedDate(order.getNeedDate());
        entity.setStatus(order.getStatus());
    }

    /**
     * 追溯领域模型写入实体。
     *
     * @param entity 实体
     * @param trace 领域模型
     */
    private void fillTrace(MrpTraceEntity entity, MrpTrace trace) {
        entity.setRunId(trace.getRunId());
        entity.setPlannedOrderId(trace.getPlannedOrderId());
        entity.setDemandLineNo(trace.getDemandLineNo());
        entity.setBomNo(trace.getBomNo());
        entity.setBomVersion(trace.getBomVersion());
        entity.setBomPath(trace.getBomPath());
        entity.setDepth(trace.getDepth());
        entity.setUnitQty(trace.getUnitQty());
        entity.setLossRate(trace.getLossRate());
        entity.setGrossQuantity(trace.getGrossQuantity());
    }
}
