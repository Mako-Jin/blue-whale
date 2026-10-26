package com.bluewhale.mes.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.dto.MesWorkOrderQueryRequest;
import com.bluewhale.mes.dto.MesWorkOrderSaveRequest;
import com.bluewhale.mes.entity.MesWorkOrder;
import com.bluewhale.mes.mapper.MesWorkOrderMapper;
import com.bluewhale.mes.service.MesWorkOrderService;
import com.bluewhale.mes.vo.MesWorkOrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 生产工单服务实现
 *
 * <p>工单编码在租户内唯一（逻辑删除行仍占用，避免复用冲突）。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MesWorkOrderServiceImpl implements MesWorkOrderService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final MesWorkOrderMapper mesWorkOrderMapper;

    @Override
    public PageResult<MesWorkOrderVO> page(MesWorkOrderQueryRequest request) {
        LambdaQueryWrapper<MesWorkOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(request.getCode()), MesWorkOrder::getCode, request.getCode());
        wrapper.like(StringUtils.hasText(request.getName()), MesWorkOrder::getName, request.getName());
        wrapper.eq(StringUtils.hasText(request.getStatus()), MesWorkOrder::getStatus, request.getStatus());
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
        MesWorkOrder workOrder = new MesWorkOrder();
        BeanUtils.copyProperties(request, workOrder);
        mesWorkOrderMapper.insert(workOrder);
        log.info("工单已创建: id={}, code={}", workOrder.getId(), workOrder.getCode());
        return toVo(workOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MesWorkOrderVO update(Long id, MesWorkOrderSaveRequest request) {
        MesWorkOrder workOrder = requireWorkOrder(id);
        assertCodeUnique(request.getCode(), id);
        BeanUtils.copyProperties(request, workOrder);
        mesWorkOrderMapper.updateById(workOrder);
        log.info("工单已更新: id={}, code={}", id, workOrder.getCode());
        return toVo(workOrder);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireWorkOrder(id);
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
            throw new BizException(ErrorCode.NOT_FOUND, "工单不存在");
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
            throw new BizException(ErrorCode.PARAM_ERROR, "工单编码已存在");
        }
    }

    /**
     * 实体转视图对象
     *
     * @param workOrder 工单实体
     * @return 视图对象
     */
    private static MesWorkOrderVO toVo(MesWorkOrder workOrder) {
        MesWorkOrderVO vo = new MesWorkOrderVO();
        BeanUtils.copyProperties(workOrder, vo);
        return vo;
    }
}
