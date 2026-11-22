package com.bluewhale.mes.quality.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.quality.dto.MesReworkActionRequest;
import com.bluewhale.mes.quality.dto.MesReworkQueryRequest;
import com.bluewhale.mes.quality.entity.MesReworkOrder;
import com.bluewhale.mes.quality.mapper.MesReworkOrderMapper;
import com.bluewhale.mes.quality.service.MesReworkService;
import com.bluewhale.mes.quality.vo.MesReworkOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * MES 返工单应用服务实现
 *
 * <p>返工单状态机：{@code CREATED → IN_PROGRESS → COMPLETED}；完工后 NCR 方可关闭。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesReworkServiceImpl implements MesReworkService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 状态：已创建 */
    private static final String STATUS_CREATED = "CREATED";

    /** 状态：返工中 */
    private static final String STATUS_IN_PROGRESS = "IN_PROGRESS";

    /** 状态：已完工 */
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final MesReworkOrderMapper reworkMapper;

    @Override
    public PageResult<MesReworkOrderVO> page(MesReworkQueryRequest request) {
        LambdaQueryWrapper<MesReworkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getReworkNo()),
                MesReworkOrder::getReworkNo, request.getReworkNo());
        wrapper.eq(StringUtils.hasText(request.getNcrNo()), MesReworkOrder::getNcrNo, request.getNcrNo());
        wrapper.eq(request.getWorkOrderId() != null,
                MesReworkOrder::getWorkOrderId, request.getWorkOrderId());
        wrapper.eq(StringUtils.hasText(request.getStatus()), MesReworkOrder::getStatus, request.getStatus());
        wrapper.orderByDesc(MesReworkOrder::getCreateTime);
        Page<MesReworkOrder> page = reworkMapper.selectPage(pageOf(request.getCurrent(), request.getSize()), wrapper);
        return PageResult.of(page.getRecords().stream().map(this::toVo).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesReworkOrderVO start(Long id, MesReworkActionRequest request) {
        MesReworkOrder rework = load(id);
        if (!STATUS_CREATED.equals(rework.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.quality.status-rework-single-allow-start-work", "返工单当前状态不可开工：{0}",
                          rework.getStatus());
        }
        rework.setStatus(STATUS_IN_PROGRESS);
        rework.setOperator(request == null ? null : request.getOperator());
        rework.setStartedAt(LocalDateTime.now());
        if (request != null && StringUtils.hasText(request.getRemark())) {
            rework.setRemark(request.getRemark());
        }
        update(rework);
        log.info("返工开工: reworkNo={}", rework.getReworkNo());
        return toVo(rework);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesReworkOrderVO complete(Long id, MesReworkActionRequest request) {
        MesReworkOrder rework = load(id);
        if (!STATUS_IN_PROGRESS.equals(rework.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.quality.status-rework-single-allow-completion", "返工单当前状态不可完工：{0}",
                          rework.getStatus());
        }
        rework.setStatus(STATUS_COMPLETED);
        rework.setCompletedAt(LocalDateTime.now());
        if (request != null && StringUtils.hasText(request.getOperator())) {
            rework.setOperator(request.getOperator());
        }
        if (request != null && StringUtils.hasText(request.getRemark())) {
            rework.setRemark(request.getRemark());
        }
        update(rework);
        log.info("返工完工: reworkNo={}", rework.getReworkNo());
        return toVo(rework);
    }

    /**
     * 加载返工单
     *
     * @param id 返工单 ID
     * @return 返工单
     */
    private MesReworkOrder load(Long id) {
        MesReworkOrder rework = reworkMapper.selectById(id);
        if (rework == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "biz.mes.quality.rework-single-found", "返工单不存在：{0}", id);
        }
        return rework;
    }

    /**
     * 更新返工单并校验乐观锁
     *
     * @param rework 返工单
     */
    private void update(MesReworkOrder rework) {
        if (reworkMapper.updateById(rework) == 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.mes.quality.rework-single-was-modified-refresh-retry", "返工单已被其他用户修改，请刷新后重试");
        }
    }

    /**
     * 构建分页对象
     *
     * @param current 当前页
     * @param size    每页条数
     * @return 分页对象
     */
    private <T> Page<T> pageOf(Long current, Long size) {
        long pageSize = Math.min(Math.max(size == null ? 20L : size, 1L), MAX_PAGE_SIZE);
        long pageCurrent = Math.max(current == null ? 1L : current, 1L);
        return new Page<>(pageCurrent, pageSize);
    }

    /**
     * 返工单实体转视图
     *
     * @param rework 返工单
     * @return 视图
     */
    private MesReworkOrderVO toVo(MesReworkOrder rework) {
        MesReworkOrderVO vo = new MesReworkOrderVO();
        vo.setId(rework.getId());
        vo.setReworkNo(rework.getReworkNo());
        vo.setNcrId(rework.getNcrId());
        vo.setNcrNo(rework.getNcrNo());
        vo.setDispositionId(rework.getDispositionId());
        vo.setWorkOrderId(rework.getWorkOrderId());
        vo.setWorkOrderCode(rework.getWorkOrderCode());
        vo.setExecTaskId(rework.getExecTaskId());
        vo.setExecTaskNo(rework.getExecTaskNo());
        vo.setItemCode(rework.getItemCode());
        vo.setItemName(rework.getItemName());
        vo.setBatchNo(rework.getBatchNo());
        vo.setQuantity(rework.getQuantity());
        vo.setStatus(rework.getStatus());
        vo.setReason(rework.getReason());
        vo.setOperator(rework.getOperator());
        vo.setStartedAt(rework.getStartedAt());
        vo.setCompletedAt(rework.getCompletedAt());
        vo.setRemark(rework.getRemark());
        vo.setCreateTime(rework.getCreateTime());
        return vo;
    }
}
