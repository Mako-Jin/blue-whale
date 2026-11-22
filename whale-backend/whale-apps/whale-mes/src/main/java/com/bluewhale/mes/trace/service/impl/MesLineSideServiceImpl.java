package com.bluewhale.mes.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.common.support.MesCodeGenerator;
import com.bluewhale.mes.trace.dto.MesLineSideMovementQueryRequest;
import com.bluewhale.mes.trace.dto.MesLineSideOperationRequest;
import com.bluewhale.mes.trace.dto.MesLineSideStockQueryRequest;
import com.bluewhale.mes.trace.dto.MesReplenishmentQueryRequest;
import com.bluewhale.mes.trace.dto.MesReplenishmentRequest;
import com.bluewhale.mes.trace.entity.MesLineSideMovement;
import com.bluewhale.mes.trace.entity.MesLineSideStock;
import com.bluewhale.mes.trace.entity.MesReplenishment;
import com.bluewhale.mes.trace.mapper.MesLineSideMovementMapper;
import com.bluewhale.mes.trace.mapper.MesLineSideStockMapper;
import com.bluewhale.mes.trace.mapper.MesReplenishmentMapper;
import com.bluewhale.mes.trace.service.MesLineSideService;
import com.bluewhale.mes.trace.vo.MesLineSideMovementVO;
import com.bluewhale.mes.trace.vo.MesLineSideStockVO;
import com.bluewhale.mes.trace.vo.MesReplenishmentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * MES 线边仓应用服务实现
 *
 * <p>库存余额按“产线 + 线边仓 + 物料 + 批次”维护并加乐观锁；任何扣减后余额不得为负；
 * 流水只追加并记录变动前/后快照；补货请求以请求号为幂等键。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesLineSideServiceImpl implements MesLineSideService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 流水类型：入库 */
    private static final String TYPE_INBOUND = "INBOUND";

    /** 流水类型：退料退回 */
    private static final String TYPE_RETURN = "RETURN";

    /** 流水类型：领用 */
    private static final String TYPE_ISSUE = "ISSUE";

    /** 方向：增加 */
    private static final int DIRECTION_IN = 1;

    /** 方向：扣减 */
    private static final int DIRECTION_OUT = -1;

    /** 补货状态：已请求 */
    private static final String REPLENISH_REQUESTED = "REQUESTED";

    /** ERP 调拨接口（对接桩，实际调拨由 ERP 承接） */
    private static final String ERP_TRANSFER_ENDPOINT = "/erp/v1/scm/transfer";

    private final MesLineSideStockMapper stockMapper;

    private final MesLineSideMovementMapper movementMapper;

    private final MesReplenishmentMapper replenishmentMapper;

    @Override
    public PageResult<MesLineSideStockVO> pageStock(MesLineSideStockQueryRequest request) {
        LambdaQueryWrapper<MesLineSideStock> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getLineId() != null, MesLineSideStock::getLineId, request.getLineId());
        wrapper.eq(StringUtils.hasText(request.getWarehouseCode()),
                MesLineSideStock::getWarehouseCode, request.getWarehouseCode());
        wrapper.eq(StringUtils.hasText(request.getItemCode()), MesLineSideStock::getItemCode, request.getItemCode());
        wrapper.eq(StringUtils.hasText(request.getBatchNo()), MesLineSideStock::getBatchNo, request.getBatchNo());
        wrapper.orderByDesc(MesLineSideStock::getUpdateTime);
        long size = Math.min(Math.max(request.getSize() == null ? 20L : request.getSize(), 1L), MAX_PAGE_SIZE);
        long current = Math.max(request.getCurrent() == null ? 1L : request.getCurrent(), 1L);
        Page<MesLineSideStock> page = stockMapper.selectPage(new Page<>(current, size), wrapper);
        List<MesLineSideStockVO> records = page.getRecords().stream().map(this::toStockVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String inbound(MesLineSideOperationRequest request) {
        String movementType = TYPE_RETURN.equals(request.getMovementType()) ? TYPE_RETURN : TYPE_INBOUND;
        String batchNo = normalizeBatchNo(request.getBatchNo());
        BigDecimal quantity = request.getQuantity();
        MesLineSideStock stock = findStock(request.getLineId(), request.getWarehouseCode(),
                request.getItemCode(), batchNo);
        BigDecimal before;
        if (stock == null) {
            before = BigDecimal.ZERO;
            stock = new MesLineSideStock();
            stock.setLineId(request.getLineId());
            stock.setLineCode(request.getLineCode());
            stock.setWarehouseCode(request.getWarehouseCode());
            stock.setItemCode(request.getItemCode());
            stock.setItemName(request.getItemName());
            stock.setBatchNo(batchNo);
            stock.setUom(request.getUom());
            stock.setQuantity(quantity);
            stock.setSafetyQuantity(BigDecimal.ZERO);
            stockMapper.insert(stock);
        } else {
            before = stock.getQuantity() == null ? BigDecimal.ZERO : stock.getQuantity();
            stock.setQuantity(before.add(quantity));
            if (stockMapper.updateById(stock) == 0) {
                throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.trace.line-side-inventory-was-modified-refresh", "线边库存已被其他用户修改，请刷新后重试");
            }
        }
        MesLineSideMovement movement = buildMovement(request, stock, movementType, DIRECTION_IN, quantity, before);
        movement.setSourceType(TYPE_RETURN.equals(movementType) ? "RETURN" : "MANUAL");
        movementMapper.insert(movement);
        return movement.getMovementNo();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String issue(MesLineSideOperationRequest request) {
        String batchNo = normalizeBatchNo(request.getBatchNo());
        MesLineSideStock stock = findStock(request.getLineId(), request.getWarehouseCode(),
                request.getItemCode(), batchNo);
        if (stock == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.trace.line-side-inventory-found-cannot-requisition", "线边库存不存在，无法领用：{0}/{1}",
                          request.getItemCode(), batchNo);
        }
        BigDecimal before = stock.getQuantity() == null ? BigDecimal.ZERO : stock.getQuantity();
        BigDecimal after = before.subtract(request.getQuantity());
        if (after.compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.trace.line-side-inventory-insufficient-balance", "线边库存不足，当前余额 {0}",
                          before.stripTrailingZeros().toPlainString());
        }
        stock.setQuantity(after);
        if (stockMapper.updateById(stock) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.trace.line-side-inventory-was-modified-refresh-2", "线边库存已被其他用户修改，请刷新后重试");
        }
        MesLineSideMovement movement = buildMovement(request, stock, TYPE_ISSUE, DIRECTION_OUT,
                request.getQuantity(), before);
        movement.setSourceType(TYPE_ISSUE);
        movementMapper.insert(movement);
        return movement.getMovementNo();
    }

    @Override
    public PageResult<MesLineSideMovementVO> pageMovements(MesLineSideMovementQueryRequest request) {
        LambdaQueryWrapper<MesLineSideMovement> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getLineId() != null, MesLineSideMovement::getLineId, request.getLineId());
        wrapper.eq(StringUtils.hasText(request.getItemCode()),
                MesLineSideMovement::getItemCode, request.getItemCode());
        wrapper.eq(StringUtils.hasText(request.getBatchNo()),
                MesLineSideMovement::getBatchNo, request.getBatchNo());
        wrapper.eq(StringUtils.hasText(request.getMovementType()),
                MesLineSideMovement::getMovementType, request.getMovementType());
        wrapper.orderByDesc(MesLineSideMovement::getOccurredAt);
        long size = Math.min(Math.max(request.getSize() == null ? 20L : request.getSize(), 1L), MAX_PAGE_SIZE);
        long current = Math.max(request.getCurrent() == null ? 1L : request.getCurrent(), 1L);
        Page<MesLineSideMovement> page = movementMapper.selectPage(new Page<>(current, size), wrapper);
        List<MesLineSideMovementVO> records = page.getRecords().stream().map(this::toMovementVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesReplenishmentVO replenish(MesReplenishmentRequest request) {
        if (StringUtils.hasText(request.getRequestNo())) {
            MesReplenishment existed = replenishmentMapper.selectOne(
                    new LambdaQueryWrapper<MesReplenishment>()
                            .eq(MesReplenishment::getRequestNo, request.getRequestNo())
                            .last("LIMIT 1"));
            if (existed != null) {
                return toReplenishmentVo(existed);
            }
        }
        MesReplenishment entity = new MesReplenishment();
        entity.setRequestNo(StringUtils.hasText(request.getRequestNo())
                ? request.getRequestNo() : MesCodeGenerator.nextNo("RPL"));
        entity.setLineId(request.getLineId());
        entity.setLineCode(request.getLineCode());
        entity.setWarehouseCode(request.getWarehouseCode());
        entity.setItemCode(request.getItemCode());
        entity.setItemName(request.getItemName());
        entity.setQuantity(request.getQuantity());
        entity.setUom(request.getUom());
        entity.setStatus(REPLENISH_REQUESTED);
        entity.setReason(request.getReason());
        entity.setRequestedBy(request.getRequestedBy());
        entity.setRequestedAt(LocalDateTime.now());
        entity.setRemark(request.getRemark());
        replenishmentMapper.insert(entity);
        log.info("线边叫料请求已登记: requestNo={}, itemCode={}, quantity={}, 待对接 ERP {}",
                entity.getRequestNo(), entity.getItemCode(), entity.getQuantity(), ERP_TRANSFER_ENDPOINT);
        return toReplenishmentVo(entity);
    }

    @Override
    public PageResult<MesReplenishmentVO> pageReplenishments(MesReplenishmentQueryRequest request) {
        LambdaQueryWrapper<MesReplenishment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(request.getLineId() != null, MesReplenishment::getLineId, request.getLineId());
        wrapper.eq(StringUtils.hasText(request.getItemCode()), MesReplenishment::getItemCode, request.getItemCode());
        wrapper.eq(StringUtils.hasText(request.getStatus()), MesReplenishment::getStatus, request.getStatus());
        wrapper.orderByDesc(MesReplenishment::getRequestedAt);
        long size = Math.min(Math.max(request.getSize() == null ? 20L : request.getSize(), 1L), MAX_PAGE_SIZE);
        long current = Math.max(request.getCurrent() == null ? 1L : request.getCurrent(), 1L);
        Page<MesReplenishment> page = replenishmentMapper.selectPage(new Page<>(current, size), wrapper);
        List<MesReplenishmentVO> records = page.getRecords().stream().map(this::toReplenishmentVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * 查询线边库存行
     *
     * @param lineId        产线 ID
     * @param warehouseCode 线边仓编码
     * @param itemCode      物料编码
     * @param batchNo       批次号（空串表示无批次）
     * @return 库存行，不存在返回 {@code null}
     */
    private MesLineSideStock findStock(Long lineId, String warehouseCode, String itemCode, String batchNo) {
        return stockMapper.selectOne(new LambdaQueryWrapper<MesLineSideStock>()
                .eq(MesLineSideStock::getLineId, lineId)
                .eq(MesLineSideStock::getWarehouseCode, warehouseCode)
                .eq(MesLineSideStock::getItemCode, itemCode)
                .eq(MesLineSideStock::getBatchNo, batchNo)
                .last("LIMIT 1"));
    }

    /**
     * 归一化批次号（无批次统一为空串）
     *
     * @param batchNo 批次号
     * @return 非空批次号
     */
    private String normalizeBatchNo(String batchNo) {
        return batchNo == null ? "" : batchNo;
    }

    /**
     * 构建线边流水（只追加，记录变动前/后余额）
     *
     * @param request      出入库请求
     * @param stock        库存行
     * @param movementType 流水类型
     * @param direction    方向
     * @param quantity     变动数量
     * @param before       变动前数量
     * @return 流水实体
     */
    private MesLineSideMovement buildMovement(MesLineSideOperationRequest request, MesLineSideStock stock,
                                              String movementType, int direction, BigDecimal quantity,
                                              BigDecimal before) {
        MesLineSideMovement movement = new MesLineSideMovement();
        movement.setMovementNo(MesCodeGenerator.nextNo("LSM"));
        movement.setStockId(stock.getId());
        movement.setLineId(request.getLineId());
        movement.setLineCode(request.getLineCode());
        movement.setWarehouseCode(request.getWarehouseCode());
        movement.setItemCode(request.getItemCode());
        movement.setBatchNo(stock.getBatchNo());
        movement.setMovementType(movementType);
        movement.setDirection(direction);
        movement.setQuantity(quantity);
        movement.setBeforeQuantity(before);
        movement.setAfterQuantity(before.add(quantity.multiply(BigDecimal.valueOf(direction))));
        movement.setWorkOrderId(request.getWorkOrderId());
        movement.setTaskId(request.getTaskId());
        movement.setOperator(request.getOperator());
        movement.setRemark(request.getRemark());
        movement.setOccurredAt(LocalDateTime.now());
        return movement;
    }

    /**
     * 库存实体转视图
     *
     * @param stock 库存实体
     * @return 库存视图
     */
    private MesLineSideStockVO toStockVo(MesLineSideStock stock) {
        MesLineSideStockVO vo = new MesLineSideStockVO();
        vo.setId(stock.getId());
        vo.setLineId(stock.getLineId());
        vo.setLineCode(stock.getLineCode());
        vo.setWarehouseCode(stock.getWarehouseCode());
        vo.setItemCode(stock.getItemCode());
        vo.setItemName(stock.getItemName());
        vo.setBatchNo(stock.getBatchNo());
        vo.setUom(stock.getUom());
        vo.setQuantity(stock.getQuantity());
        vo.setSafetyQuantity(stock.getSafetyQuantity());
        BigDecimal safety = stock.getSafetyQuantity() == null ? BigDecimal.ZERO : stock.getSafetyQuantity();
        BigDecimal quantity = stock.getQuantity() == null ? BigDecimal.ZERO : stock.getQuantity();
        vo.setBelowSafety(quantity.compareTo(safety) < 0);
        vo.setUpdateTime(stock.getUpdateTime());
        return vo;
    }

    /**
     * 流水实体转视图
     *
     * @param movement 流水实体
     * @return 流水视图
     */
    private MesLineSideMovementVO toMovementVo(MesLineSideMovement movement) {
        MesLineSideMovementVO vo = new MesLineSideMovementVO();
        vo.setId(movement.getId());
        vo.setMovementNo(movement.getMovementNo());
        vo.setLineId(movement.getLineId());
        vo.setLineCode(movement.getLineCode());
        vo.setWarehouseCode(movement.getWarehouseCode());
        vo.setItemCode(movement.getItemCode());
        vo.setBatchNo(movement.getBatchNo());
        vo.setMovementType(movement.getMovementType());
        vo.setDirection(movement.getDirection());
        vo.setQuantity(movement.getQuantity());
        vo.setBeforeQuantity(movement.getBeforeQuantity());
        vo.setAfterQuantity(movement.getAfterQuantity());
        vo.setSourceType(movement.getSourceType());
        vo.setSourceNo(movement.getSourceNo());
        vo.setWorkOrderId(movement.getWorkOrderId());
        vo.setTaskId(movement.getTaskId());
        vo.setOperator(movement.getOperator());
        vo.setRemark(movement.getRemark());
        vo.setOccurredAt(movement.getOccurredAt());
        return vo;
    }

    /**
     * 补货请求实体转视图
     *
     * @param entity 补货实体
     * @return 补货视图
     */
    private MesReplenishmentVO toReplenishmentVo(MesReplenishment entity) {
        MesReplenishmentVO vo = new MesReplenishmentVO();
        vo.setId(entity.getId());
        vo.setRequestNo(entity.getRequestNo());
        vo.setLineId(entity.getLineId());
        vo.setLineCode(entity.getLineCode());
        vo.setWarehouseCode(entity.getWarehouseCode());
        vo.setItemCode(entity.getItemCode());
        vo.setItemName(entity.getItemName());
        vo.setQuantity(entity.getQuantity());
        vo.setUom(entity.getUom());
        vo.setStatus(entity.getStatus());
        vo.setReason(entity.getReason());
        vo.setRequestedBy(entity.getRequestedBy());
        vo.setRequestedAt(entity.getRequestedAt());
        vo.setErpTransferNo(entity.getErpTransferNo());
        vo.setRemark(entity.getRemark());
        return vo;
    }
}
