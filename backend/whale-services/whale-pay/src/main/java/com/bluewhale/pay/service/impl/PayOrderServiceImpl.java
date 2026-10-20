package com.bluewhale.pay.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pay.dto.PayOrderCreateRequest;
import com.bluewhale.pay.dto.PayOrderQueryRequest;
import com.bluewhale.pay.entity.PayOrder;
import com.bluewhale.pay.mapper.PayOrderMapper;
import com.bluewhale.pay.service.PayOrderService;
import com.bluewhale.pay.vo.PayOrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付单服务实现
 *
 * <p>创建前按 {@code order_no} 查重（仅未删除记录），冲突抛 {@code PARAM_ERROR}；
 * 详情 / 支付 / 取消前均校验存在性，缺失抛 {@code NOT_FOUND}。
 * 状态流转单向：仅 {@code CREATED → PAID}（回填 {@code pay_time}）与
 * {@code CREATED → CANCELLED}，其余迁移一律抛 {@code PARAM_ERROR}。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class PayOrderServiceImpl implements PayOrderService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 状态：待支付 */
    private static final String STATUS_CREATED = "CREATED";

    /** 状态：已支付 */
    private static final String STATUS_PAID = "PAID";

    /** 状态：已取消 */
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final PayOrderMapper payOrderMapper;

    @Override
    public PageResult<PayOrderVO> page(PayOrderQueryRequest request) {
        LambdaQueryWrapper<PayOrder> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(request.getKeyword())) {
            wrapper.and(inner -> inner.like(PayOrder::getOrderNo, request.getKeyword())
                    .or().like(PayOrder::getBusinessKey, request.getKeyword()));
        }
        wrapper.eq(StringUtils.hasText(request.getStatus()), PayOrder::getStatus, request.getStatus());
        wrapper.eq(StringUtils.hasText(request.getChannel()), PayOrder::getChannel, request.getChannel());
        wrapper.orderByDesc(PayOrder::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<PayOrder> page = payOrderMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<PayOrderVO> records = page.getRecords().stream().map(PayOrderServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PayOrderVO getDetail(Long id) {
        return toVo(requireOrder(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayOrderVO create(PayOrderCreateRequest request) {
        ensureUnique(request.getOrderNo());
        PayOrder entity = new PayOrder();
        BeanUtils.copyProperties(request, entity);
        entity.setStatus(STATUS_CREATED);
        payOrderMapper.insert(entity);
        return toVo(payOrderMapper.selectById(entity.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayOrderVO pay(Long id) {
        PayOrder existing = requireOrder(id);
        ensureCreated(existing);
        existing.setStatus(STATUS_PAID);
        existing.setPayTime(LocalDateTime.now());
        payOrderMapper.updateById(existing);
        return toVo(payOrderMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PayOrderVO cancel(Long id) {
        PayOrder existing = requireOrder(id);
        ensureCreated(existing);
        existing.setStatus(STATUS_CANCELLED);
        payOrderMapper.updateById(existing);
        return toVo(payOrderMapper.selectById(id));
    }

    /**
     * 查询支付单，不存在则抛出 {@code NOT_FOUND}
     *
     * @param id 支付单ID
     * @return 支付单实体
     */
    private PayOrder requireOrder(Long id) {
        PayOrder order = payOrderMapper.selectById(id);
        if (order == null) {
            throw new BizException(ErrorCode.NOT_FOUND);
        }
        return order;
    }

    /**
     * 状态流转前置校验：仅允许从 {@code CREATED} 发起迁移，否则抛 {@code PARAM_ERROR}
     *
     * @param order 支付单实体
     */
    private void ensureCreated(PayOrder order) {
        if (!STATUS_CREATED.equals(order.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    /**
     * 唯一性校验：{@code order_no} 冲突时抛出 {@code PARAM_ERROR}
     *
     * @param orderNo 支付单号
     */
    private void ensureUnique(String orderNo) {
        LambdaQueryWrapper<PayOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PayOrder::getOrderNo, orderNo);
        if (payOrderMapper.selectCount(wrapper) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR);
        }
    }

    /**
     * 支付单实体转视图对象
     *
     * @param order 支付单实体
     * @return 支付单视图对象
     */
    private static PayOrderVO toVo(PayOrder order) {
        PayOrderVO vo = new PayOrderVO();
        BeanUtils.copyProperties(order, vo);
        return vo;
    }
}
