package com.bluewhale.message.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.message.entity.MessageAnnouncement;
import com.bluewhale.message.entity.MessageDelivery;
import com.bluewhale.message.enums.AnnouncementStatus;
import com.bluewhale.message.enums.DeliveryStatus;
import com.bluewhale.message.enums.MessageChannel;
import com.bluewhale.message.mapper.MessageAnnouncementMapper;
import com.bluewhale.message.mapper.MessageDeliveryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 系统公告收件箱惰性展开器。
 *
 * <p>公告发布时不枚举接收人；用户打开收件箱（分页/未读数/标记已读）时，
 * 将当前租户已发布且尚未展开给该用户的公告补写为个人 INAPP 投递记录。
 * 展开以 {@code uk_message_channel_recipient} 唯一索引为并发护栏，
 * 并发请求撞键时视为已展开、静默跳过。任何展开异常都不阻断收件箱读取。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnnouncementExpander {

    private final MessageAnnouncementMapper announcementMapper;

    private final MessageDeliveryMapper deliveryMapper;

    /**
     * 将当前租户已发布、尚未展开给指定用户的公告补写为个人投递记录。
     *
     * @param userId 当前用户ID
     */
    public void expand(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            doExpand(userId);
        } catch (Exception e) {
            // 展开属于收件箱读取的附属写入，失败只降级（本次看不到新公告），不阻断收件箱
            log.warn("系统公告展开失败，本次按未展开数据返回: userId={}, reason={}", userId, e.getMessage());
        }
    }

    /**
     * 执行展开：已发布公告 - 已有个人投递 = 待补写集合。
     *
     * @param userId 当前用户ID
     */
    private void doExpand(Long userId) {
        List<MessageAnnouncement> published = announcementMapper.selectList(
                new LambdaQueryWrapper<MessageAnnouncement>()
                        .eq(MessageAnnouncement::getStatus, AnnouncementStatus.PUBLISHED.name()));
        if (published.isEmpty()) {
            return;
        }
        List<Long> recordIds = published.stream()
                .map(MessageAnnouncement::getRecordId)
                .distinct()
                .toList();
        Set<Long> expanded = deliveryMapper.selectList(new LambdaQueryWrapper<MessageDelivery>()
                        .select(MessageDelivery::getMessageId)
                        .eq(MessageDelivery::getChannel, MessageChannel.INAPP.name())
                        .eq(MessageDelivery::getRecipient, String.valueOf(userId))
                        .in(MessageDelivery::getMessageId, recordIds))
                .stream()
                .map(MessageDelivery::getMessageId)
                .collect(Collectors.toSet());
        String recipient = String.valueOf(userId);
        for (MessageAnnouncement announcement : published) {
            if (expanded.contains(announcement.getRecordId())) {
                continue;
            }
            insertExpandedDelivery(announcement, recipient);
        }
    }

    /**
     * 补写一条个人投递记录，撞唯一键视为并发已展开。
     *
     * @param announcement 公告
     * @param recipient    接收标识（用户ID文本）
     */
    private void insertExpandedDelivery(MessageAnnouncement announcement, String recipient) {
        MessageDelivery delivery = new MessageDelivery();
        delivery.setMessageId(announcement.getRecordId());
        delivery.setChannel(MessageChannel.INAPP.name());
        delivery.setRecipient(recipient);
        delivery.setStatus(DeliveryStatus.SENT.name());
        delivery.setAttempts(1);
        delivery.setSentAt(announcement.getPublishTime() == null
                ? LocalDateTime.now() : announcement.getPublishTime());
        delivery.setReadFlag(0);
        try {
            deliveryMapper.insert(delivery);
            log.info("系统公告已展开为个人消息: announcementId={}, messageId={}, userId={}",
                    announcement.getId(), announcement.getRecordId(), recipient);
        } catch (DuplicateKeyException e) {
            log.debug("系统公告并发展开撞键，跳过: messageId={}, userId={}",
                    announcement.getRecordId(), recipient);
        }
    }
}
