package com.bluewhale.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.message.converter.MessageConverter;
import com.bluewhale.message.dto.MessageRecordQueryRequest;
import com.bluewhale.message.entity.MessageDelivery;
import com.bluewhale.message.entity.MessageRecord;
import com.bluewhale.message.mapper.MessageDeliveryMapper;
import com.bluewhale.message.mapper.MessageRecordMapper;
import com.bluewhale.message.service.MessageRecordService;
import com.bluewhale.message.vo.MessageDeliveryVO;
import com.bluewhale.message.vo.MessageRecordVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 消息记录查询服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MessageRecordServiceImpl implements MessageRecordService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    private final MessageRecordMapper recordMapper;

    private final MessageDeliveryMapper deliveryMapper;

    @Override
    public PageResult<MessageRecordVO> page(MessageRecordQueryRequest request) {
        LambdaQueryWrapper<MessageRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(request.getBizType()), MessageRecord::getBizType, request.getBizType());
        wrapper.eq(StringUtils.hasText(request.getBizId()), MessageRecord::getBizId, request.getBizId());
        wrapper.eq(StringUtils.hasText(request.getStatus()), MessageRecord::getStatus, request.getStatus());
        wrapper.like(StringUtils.hasText(request.getChannel()), MessageRecord::getChannels,
                StringUtils.hasText(request.getChannel()) ? request.getChannel().trim().toUpperCase() : null);
        wrapper.orderByDesc(MessageRecord::getCreateTime);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<MessageRecord> page = recordMapper.selectPage(new Page<>(request.getCurrent(), size), wrapper);
        List<MessageRecordVO> records = page.getRecords().stream().map(MessageRecordServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public MessageRecordVO detail(Long id) {
        MessageRecord record = recordMapper.selectById(id);
        if (record == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "消息不存在: " + id);
        }
        MessageRecordVO vo = toVo(record);
        LambdaQueryWrapper<MessageDelivery> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageDelivery::getMessageId, id)
                .orderByAsc(MessageDelivery::getId);
        vo.setDeliveries(deliveryMapper.selectList(wrapper).stream()
                .map(MessageRecordServiceImpl::toDeliveryVo).toList());
        return vo;
    }

    /**
     * 消息记录转视图。
     *
     * @param record 消息记录
     * @return 视图
     */
    private static MessageRecordVO toVo(MessageRecord record) {
        MessageRecordVO vo = MessageConverter.INSTANCE.toRecordVo(record);
        return vo;
    }

    /**
     * 投递记录转视图。
     *
     * @param delivery 投递记录
     * @return 视图
     */
    private static MessageDeliveryVO toDeliveryVo(MessageDelivery delivery) {
        MessageDeliveryVO vo = MessageConverter.INSTANCE.toDeliveryVo(delivery);
        return vo;
    }
}
