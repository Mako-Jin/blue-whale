package com.bluewhale.mes.trace.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.common.support.MesCodeGenerator;
import com.bluewhale.mes.trace.dto.MesBatchQueryRequest;
import com.bluewhale.mes.trace.dto.MesBatchSaveRequest;
import com.bluewhale.mes.trace.dto.MesTraceLinkRequest;
import com.bluewhale.mes.trace.entity.MesMaterialBatch;
import com.bluewhale.mes.trace.entity.MesTraceLink;
import com.bluewhale.mes.trace.mapper.MesMaterialBatchMapper;
import com.bluewhale.mes.trace.mapper.MesTraceLinkMapper;
import com.bluewhale.mes.trace.service.MesBatchService;
import com.bluewhale.mes.trace.vo.MesBatchVO;
import com.bluewhale.mes.trace.vo.MesTraceNodeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * MES 批次与谱系应用服务实现
 *
 * <p>不变量：批次号租户内唯一；谱系新增边不得成环；父批次累计消耗量不得超过批次投入量；
 * 追溯按层级广度优先展开并去重，可配置最大层级（默认 3，上限 10）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesBatchServiceImpl implements MesBatchService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 批次状态：有效 */
    private static final String BATCH_ACTIVE = "ACTIVE";

    /** 批次状态：已封批 */
    private static final String BATCH_CLOSED = "CLOSED";

    /** 链接状态：有效 */
    private static final String LINK_ACTIVE = "ACTIVE";

    /** 链接状态：已冲销 */
    private static final String LINK_REVERSED = "REVERSED";

    /** 默认追溯层级 */
    private static final int DEFAULT_DEPTH = 3;

    /** 最大追溯层级 */
    private static final int MAX_DEPTH = 10;

    /** 关系：下游 */
    private static final String RELATION_CHILD = "CHILD";

    /** 关系：上游 */
    private static final String RELATION_PARENT = "PARENT";

    /** 防环遍历节点上限 */
    private static final int MAX_TRAVERSE_NODES = 500;

    private final MesMaterialBatchMapper batchMapper;

    private final MesTraceLinkMapper linkMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesBatchVO create(MesBatchSaveRequest request) {
        String batchNo = StringUtils.hasText(request.getBatchNo())
                ? request.getBatchNo() : MesCodeGenerator.nextNo("BAT");
        if (batchMapper.selectCount(new LambdaQueryWrapper<MesMaterialBatch>()
                .eq(MesMaterialBatch::getBatchNo, batchNo)) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.trace.batch-number-already-exists", "批次号已存在：{0}", batchNo);
        }
        MesMaterialBatch batch = new MesMaterialBatch();
        batch.setBatchNo(batchNo);
        batch.setItemCode(request.getItemCode());
        batch.setItemName(request.getItemName());
        batch.setBatchType(StringUtils.hasText(request.getBatchType()) ? request.getBatchType() : "RAW");
        batch.setSourceType(StringUtils.hasText(request.getSourceType()) ? request.getSourceType() : "PRODUCTION");
        batch.setWorkOrderId(request.getWorkOrderId());
        batch.setWorkOrderCode(request.getWorkOrderCode());
        batch.setSupplierCode(request.getSupplierCode());
        batch.setQuantity(request.getQuantity() == null ? BigDecimal.ZERO : request.getQuantity());
        batch.setConsumedQuantity(BigDecimal.ZERO);
        batch.setUom(request.getUom());
        batch.setProductionDate(request.getProductionDate());
        batch.setExpiryDate(request.getExpiryDate());
        batch.setStatus(BATCH_ACTIVE);
        batch.setRemark(request.getRemark());
        batchMapper.insert(batch);
        log.info("批次建档: batchNo={}, itemCode={}, quantity={}",
                batch.getBatchNo(), batch.getItemCode(), batch.getQuantity());
        return toBatchVo(batch);
    }

    @Override
    public PageResult<MesBatchVO> page(MesBatchQueryRequest request) {
        LambdaQueryWrapper<MesMaterialBatch> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getBatchNo()), MesMaterialBatch::getBatchNo, request.getBatchNo());
        wrapper.eq(StringUtils.hasText(request.getItemCode()), MesMaterialBatch::getItemCode, request.getItemCode());
        wrapper.eq(StringUtils.hasText(request.getBatchType()), MesMaterialBatch::getBatchType, request.getBatchType());
        wrapper.eq(StringUtils.hasText(request.getStatus()), MesMaterialBatch::getStatus, request.getStatus());
        wrapper.orderByDesc(MesMaterialBatch::getCreateTime);
        long size = Math.min(Math.max(request.getSize() == null ? 20L : request.getSize(), 1L), MAX_PAGE_SIZE);
        long current = Math.max(request.getCurrent() == null ? 1L : request.getCurrent(), 1L);
        Page<MesMaterialBatch> page = batchMapper.selectPage(new Page<>(current, size), wrapper);
        List<MesBatchVO> records = page.getRecords().stream().map(this::toBatchVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesBatchVO close(Long id) {
        MesMaterialBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.trace.batch-found", "批次不存在：{0}", id);
        }
        if (BATCH_CLOSED.equals(batch.getStatus())) {
            return toBatchVo(batch);
        }
        batch.setStatus(BATCH_CLOSED);
        if (batchMapper.updateById(batch) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.trace.batch-was-modified-refresh-retry", "批次已被其他用户修改，请刷新后重试");
        }
        return toBatchVo(batch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long link(MesTraceLinkRequest request) {
        if (request.getParentBatchNo().equals(request.getChildBatchNo())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.trace.lineage-link-cannot-created-within-same", "同一批次不能建立谱系链接");
        }
        MesMaterialBatch parent = requireBatch(request.getParentBatchNo());
        MesMaterialBatch child = requireBatch(request.getChildBatchNo());
        if (BATCH_CLOSED.equals(parent.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.trace.parent-batch-batch-closure-cannot-continue", "上级批次已封批，不能继续消耗：{0}",
                          parent.getBatchNo());
        }
        if (canReachDownstream(child.getId(), parent.getId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.trace.lineage-cycle-detected-link-creation-forbidden", "谱系成环，禁止建链：{0} -> {1}",
                          parent.getBatchNo(), child.getBatchNo());
        }
        BigDecimal consumed = parent.getConsumedQuantity() == null ? BigDecimal.ZERO : parent.getConsumedQuantity();
        BigDecimal after = consumed.add(request.getQuantity());
        if (after.compareTo(parent.getQuantity() == null ? BigDecimal.ZERO : parent.getQuantity()) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.trace.consumption-quantity-exceed-batch-input-quantity", "消耗数量超过批次投入量：{0}，可消耗 {1}",
                          parent.getBatchNo(), parent.getQuantity().subtract(consumed).stripTrailingZeros().toPlainString());
        }
        parent.setConsumedQuantity(after);
        if (batchMapper.updateById(parent) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.trace.batch-was-modified-refresh-retry-2", "批次已被其他用户修改，请刷新后重试");
        }
        MesTraceLink link = new MesTraceLink();
        link.setParentBatchId(parent.getId());
        link.setParentBatchNo(parent.getBatchNo());
        link.setChildBatchId(child.getId());
        link.setChildBatchNo(child.getBatchNo());
        link.setLinkType("CONSUME");
        link.setQuantity(request.getQuantity());
        link.setUom(StringUtils.hasText(request.getUom()) ? request.getUom() : parent.getUom());
        link.setWorkOrderId(request.getWorkOrderId());
        link.setStatus(LINK_ACTIVE);
        link.setRemark(request.getRemark());
        linkMapper.insert(link);
        log.info("批次谱系建链: {} -> {}, quantity={}",
                parent.getBatchNo(), child.getBatchNo(), request.getQuantity());
        return link.getId();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverseLink(Long id, String remark) {
        MesTraceLink link = linkMapper.selectById(id);
        if (link == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.trace.lineage-link-found", "谱系链接不存在：{0}", id);
        }
        if (LINK_REVERSED.equals(link.getStatus())) {
            return;
        }
        if (!LINK_ACTIVE.equals(link.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.trace.lineage-link-status-allowed-reversal", "谱系链接状态不允许冲销：{0}", link.getStatus());
        }
        MesMaterialBatch parent = batchMapper.selectById(link.getParentBatchId());
        if (parent == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.trace.parent-batch-found", "上级批次不存在：{0}", link.getParentBatchNo());
        }
        BigDecimal quantity = link.getQuantity() == null ? BigDecimal.ZERO : link.getQuantity();
        BigDecimal consumed = parent.getConsumedQuantity() == null ? BigDecimal.ZERO : parent.getConsumedQuantity();
        BigDecimal after = consumed.subtract(quantity);
        parent.setConsumedQuantity(after.signum() < 0 ? BigDecimal.ZERO : after);
        if (batchMapper.updateById(parent) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.trace.batch-was-modified-refresh-retry-3", "批次已被其他用户修改，请刷新后重试");
        }
        link.setStatus(LINK_REVERSED);
        if (StringUtils.hasText(remark)) {
            link.setRemark(remark);
        }
        if (linkMapper.updateById(link) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.trace.lineage-link-was-modified-refresh-retry", "谱系链接已被其他用户修改，请刷新后重试");
        }
        log.info("批次谱系冲销: linkId={}, {} -> {}, quantity={}",
                id, link.getParentBatchNo(), link.getChildBatchNo(), quantity);
    }

    @Override
    public List<MesTraceNodeVO> forward(String batchNo, Integer depth) {
        MesMaterialBatch start = requireBatch(batchNo);
        return trace(start, resolveDepth(depth), true);
    }

    @Override
    public List<MesTraceNodeVO> backward(String batchNo, Integer depth) {
        MesMaterialBatch start = requireBatch(batchNo);
        return trace(start, resolveDepth(depth), false);
    }

    /**
     * 按层级广度优先展开谱系
     *
     * @param start    起点批次
     * @param maxDepth 最大层级
     * @param forward  {@code true} 正向（下游），{@code false} 反向（上游）
     * @return 追溯节点
     */
    private List<MesTraceNodeVO> trace(MesMaterialBatch start, int maxDepth, boolean forward) {
        List<MesTraceNodeVO> nodes = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        visited.add(start.getId());
        Set<Long> frontier = new LinkedHashSet<>();
        frontier.add(start.getId());
        for (int level = 1; level <= maxDepth && !frontier.isEmpty() && nodes.size() < MAX_TRAVERSE_NODES; level++) {
            LambdaQueryWrapper<MesTraceLink> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MesTraceLink::getStatus, LINK_ACTIVE);
            if (forward) {
                wrapper.in(MesTraceLink::getParentBatchId, frontier);
            } else {
                wrapper.in(MesTraceLink::getChildBatchId, frontier);
            }
            List<MesTraceLink> links = linkMapper.selectList(wrapper);
            Set<Long> next = new LinkedHashSet<>();
            for (MesTraceLink link : links) {
                Long batchId = forward ? link.getChildBatchId() : link.getParentBatchId();
                if (!visited.add(batchId)) {
                    continue;
                }
                MesMaterialBatch batch = batchMapper.selectById(batchId);
                if (batch == null) {
                    continue;
                }
                nodes.add(toNode(batch, level, link.getQuantity(), forward ? RELATION_CHILD : RELATION_PARENT));
                next.add(batchId);
                if (nodes.size() >= MAX_TRAVERSE_NODES) {
                    break;
                }
            }
            frontier = next;
        }
        return nodes;
    }

    /**
     * 判断从起点沿下游方向是否可到达目标批次（用于成环检测）
     *
     * @param fromBatchId   起点批次 ID
     * @param targetBatchId 目标批次 ID
     * @return 可到达返回 {@code true}
     */
    private boolean canReachDownstream(Long fromBatchId, Long targetBatchId) {
        Set<Long> visited = new HashSet<>();
        Set<Long> frontier = new LinkedHashSet<>();
        frontier.add(fromBatchId);
        int traversed = 0;
        while (!frontier.isEmpty() && traversed < MAX_TRAVERSE_NODES) {
            LambdaQueryWrapper<MesTraceLink> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(MesTraceLink::getStatus, LINK_ACTIVE);
            wrapper.in(MesTraceLink::getParentBatchId, frontier);
            List<MesTraceLink> links = linkMapper.selectList(wrapper);
            Set<Long> next = new LinkedHashSet<>();
            for (MesTraceLink link : links) {
                if (link.getChildBatchId().equals(targetBatchId)) {
                    return true;
                }
                traversed++;
                if (visited.add(link.getChildBatchId())) {
                    next.add(link.getChildBatchId());
                }
            }
            frontier = next;
        }
        return false;
    }

    /**
     * 按批次号加载批次，不存在即报错
     *
     * @param batchNo 批次号
     * @return 批次实体
     */
    private MesMaterialBatch requireBatch(String batchNo) {
        MesMaterialBatch batch = batchMapper.selectOne(new LambdaQueryWrapper<MesMaterialBatch>()
                .eq(MesMaterialBatch::getBatchNo, batchNo)
                .last("LIMIT 1"));
        if (batch == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.trace.batch-found-2", "批次不存在：{0}", batchNo);
        }
        return batch;
    }

    /**
     * 归一化追溯层级
     *
     * @param depth 请求层级
     * @return 有效层级
     */
    private int resolveDepth(Integer depth) {
        if (depth == null) {
            return DEFAULT_DEPTH;
        }
        return Math.min(Math.max(depth, 1), MAX_DEPTH);
    }

    /**
     * 批次实体转视图
     *
     * @param batch 批次实体
     * @return 批次视图
     */
    private MesBatchVO toBatchVo(MesMaterialBatch batch) {
        MesBatchVO vo = new MesBatchVO();
        vo.setId(batch.getId());
        vo.setBatchNo(batch.getBatchNo());
        vo.setItemCode(batch.getItemCode());
        vo.setItemName(batch.getItemName());
        vo.setBatchType(batch.getBatchType());
        vo.setSourceType(batch.getSourceType());
        vo.setWorkOrderId(batch.getWorkOrderId());
        vo.setWorkOrderCode(batch.getWorkOrderCode());
        vo.setSupplierCode(batch.getSupplierCode());
        vo.setQuantity(batch.getQuantity());
        vo.setConsumedQuantity(batch.getConsumedQuantity());
        BigDecimal remaining = (batch.getQuantity() == null ? BigDecimal.ZERO : batch.getQuantity())
                .subtract(batch.getConsumedQuantity() == null ? BigDecimal.ZERO : batch.getConsumedQuantity());
        vo.setRemainingQuantity(remaining.max(BigDecimal.ZERO));
        vo.setUom(batch.getUom());
        vo.setProductionDate(batch.getProductionDate());
        vo.setExpiryDate(batch.getExpiryDate());
        vo.setStatus(batch.getStatus());
        vo.setRemark(batch.getRemark());
        vo.setCreateTime(batch.getCreateTime());
        return vo;
    }

    /**
     * 批次实体转追溯节点
     *
     * @param batch    批次实体
     * @param level    层级
     * @param quantity 与上层级的关系数量
     * @param relation 关系方向
     * @return 追溯节点
     */
    private MesTraceNodeVO toNode(MesMaterialBatch batch, int level, BigDecimal quantity, String relation) {
        MesTraceNodeVO vo = new MesTraceNodeVO();
        vo.setLevel(level);
        vo.setBatchId(batch.getId());
        vo.setBatchNo(batch.getBatchNo());
        vo.setItemCode(batch.getItemCode());
        vo.setItemName(batch.getItemName());
        vo.setBatchType(batch.getBatchType());
        vo.setStatus(batch.getStatus());
        vo.setQuantity(quantity);
        vo.setUom(batch.getUom());
        vo.setRelation(relation);
        return vo;
    }
}
