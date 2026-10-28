package com.bluewhale.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.message.dto.InAppMessageQueryRequest;
import com.bluewhale.message.entity.MessageDelivery;
import com.bluewhale.message.entity.MessageRecord;
import com.bluewhale.message.enums.MessageChannel;
import com.bluewhale.message.mapper.MessageDeliveryMapper;
import com.bluewhale.message.mapper.MessageRecordMapper;
import com.bluewhale.message.service.MessageInAppService;
import com.bluewhale.message.vo.InAppMessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 站内信收件箱服务实现。
 *
 * <p>数据口径：{@code msg_delivery.channel=INAPP} 且 {@code recipient=当前用户ID}。
 * 已读状态记在投递记录上，因此同一消息发给多个用户时各用户互不影响。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MessageInAppServiceImpl implements MessageInAppService {

    /** 单页最大条数：防止深度分页拖垮数据库 */
    private static final long MAX_PAGE_SIZE = 200L;

    /** 未读 */
    private static final int UNREAD = 0;

    /** 已读 */
    private static final int READ = 1;

    private final MessageDeliveryMapper deliveryMapper;

    private final MessageRecordMapper recordMapper;

    @Override
    public PageResult<InAppMessageVO> page(Long userId, InAppMessageQueryRequest request) {
        LambdaQueryWrapper<MessageDelivery> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageDelivery::getChannel, MessageChannel.INAPP.name())
                .eq(MessageDelivery::getRecipient, String.valueOf(userId))
                .eq(request.getReadFlag() != null, MessageDelivery::getReadFlag, request.getReadFlag())
                .orderByDesc(MessageDelivery::getId);
        long size = Math.min(Math.max(request.getSize(), 1L), MAX_PAGE_SIZE);
        Page<MessageDelivery> page = deliveryMapper.selectPage(
                new Page<>(request.getCurrent(), size), wrapper);
        List<MessageDelivery> deliveries = page.getRecords();
        if (deliveries.isEmpty()) {
            return PageResult.of(List.of(), page.getTotal(), page.getCurrent(), page.getSize());
        }
        List<Long> messageIds = deliveries.stream()
                .map(MessageDelivery::getMessageId)
                .distinct()
                .toList();
        Map<Long, MessageRecord> records = recordMapper.selectBatchIds(messageIds).stream()
                .collect(Collectors.toMap(MessageRecord::getId, record -> record, (first, second) -> first));
        List<InAppMessageVO> rows = deliveries.stream()
                .map(delivery -> toVo(delivery, records.get(delivery.getMessageId())))
                .toList();
        return PageResult.of(rows, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public long unreadCount(Long userId) {
        LambdaQueryWrapper<MessageDelivery> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MessageDelivery::getChannel, MessageChannel.INAPP.name())
                .eq(MessageDelivery::getRecipient, String.valueOf(userId))
                .eq(MessageDelivery::getReadFlag, UNREAD);
        return deliveryMapper.selectCount(wrapper);
    }

    @Override
    public void markRead(Long userId, Long messageId) {
        LambdaQueryWrapper<MessageDelivery> exists = new LambdaQueryWrapper<>();
        exists.eq(MessageDelivery::getMessageId, messageId)
                .eq(MessageDelivery::getChannel, MessageChannel.INAPP.name())
                .eq(MessageDelivery::getRecipient, String.valueOf(userId));
        if (deliveryMapper.selectCount(exists) == 0) {
            throw new BizException(ErrorCode.NOT_FOUND, "站内信不存在: " + messageId);
        }
        // 用列名字符串构造更新条件：本方法只做单表固定列更新，避免依赖实体 Lambda 缓存
        UpdateWrapper<MessageDelivery> update = new UpdateWrapper<>();
        update.eq("message_id", messageId)
                .eq("channel", MessageChannel.INAPP.name())
                .eq("recipient", String.valueOf(userId))
                .set("read_flag", READ);
        deliveryMapper.update(null, update);
    }

    /**
     * 投递记录与消息记录合成收件箱条目。
     *
     * @param delivery 投递记录
     * @param record   消息记录（可能因逻辑删除为空）
     * @return 收件箱条目
     */
    private static InAppMessageVO toVo(MessageDelivery delivery, MessageRecord record) {
        InAppMessageVO vo = new InAppMessageVO();
        vo.setDeliveryId(delivery.getId());
        vo.setMessageId(delivery.getMessageId());
        vo.setDeliveryStatus(delivery.getStatus());
        vo.setReadFlag(delivery.getReadFlag());
        vo.setSentAt(delivery.getSentAt());
        vo.setCreateTime(delivery.getCreateTime());
        if (record != null) {
            vo.setTitle(record.getTitle());
            vo.setContent(record.getContent());
            vo.setBizType(record.getBizType());
            vo.setBizId(record.getBizId());
        }
        return vo;
    }
}
