package com.bluewhale.crm.marketing.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmMarketingProperties;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.customer.application.view.ContactView;
import com.bluewhale.crm.marketing.application.command.DeliveryReceiptCommand;
import com.bluewhale.crm.marketing.application.command.MarketingSendCreateCommand;
import com.bluewhale.crm.marketing.application.service.MarketingSendAppService;
import com.bluewhale.crm.marketing.application.view.DeliveryReceiptView;
import com.bluewhale.crm.marketing.application.view.MarketingSendView;
import com.bluewhale.crm.marketing.domain.model.Campaign;
import com.bluewhale.crm.marketing.domain.model.CampaignAudience;
import com.bluewhale.crm.marketing.domain.model.CampaignMember;
import com.bluewhale.crm.marketing.domain.model.MarketingMessage;
import com.bluewhale.crm.marketing.domain.model.MarketingSend;
import com.bluewhale.crm.marketing.domain.model.MarketingSendItem;
import com.bluewhale.crm.marketing.domain.model.MarketingSendQuery;
import com.bluewhale.crm.marketing.domain.model.MarketingSendStatus;
import com.bluewhale.crm.marketing.domain.model.SendChannel;
import com.bluewhale.crm.marketing.domain.model.SendItemStatus;
import com.bluewhale.crm.marketing.domain.port.MessageDispatchPort;
import com.bluewhale.crm.marketing.domain.port.MessageDispatchResult;
import com.bluewhale.crm.marketing.domain.repository.CampaignAudienceRepository;
import com.bluewhale.crm.marketing.domain.repository.CampaignMemberRepository;
import com.bluewhale.crm.marketing.domain.repository.CampaignRepository;
import com.bluewhale.crm.marketing.domain.repository.MarketingSendItemRepository;
import com.bluewhale.crm.marketing.domain.repository.MarketingSendRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 批量触达应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MarketingSendAppServiceImpl implements MarketingSendAppService {

    /** 触达任务编号前缀 */
    private static final String CODE_PREFIX = "SEND-";

    /** 日期格式 */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MarketingSendRepository marketingSendRepository;

    private final MarketingSendItemRepository marketingSendItemRepository;

    private final CampaignRepository campaignRepository;

    private final CampaignAudienceRepository campaignAudienceRepository;

    private final CampaignMemberRepository campaignMemberRepository;

    private final CustomerAppService customerAppService;

    private final MessageDispatchPort messageDispatchPort;

    private final CrmProperties crmProperties;

    private final CrmMarketingProperties marketingProperties;

    @Override
    public PageResult<MarketingSendView> page(MarketingSendQuery query, CrmActor actor) {
        ensureGate();
        if (!actor.allDataScope()) {
            query.setScopeUserId(actor.requireUserId());
        }
        List<MarketingSendView> records = marketingSendRepository.page(query).stream()
                .map(send -> MarketingSendView.from(send, null)).toList();
        long total = marketingSendRepository.count(query);
        return PageResult.of(records, total, query.getCurrent(), query.getSize());
    }

    @Override
    public MarketingSendView detail(Long id, CrmActor actor) {
        ensureGate();
        MarketingSend send = loadSend(id, actor);
        return MarketingSendView.from(send, marketingSendItemRepository.findBySend(send.getId()));
    }

    @Override
    @Transactional
    public MarketingSendView create(MarketingSendCreateCommand command, CrmActor actor) {
        ensureGate();
        Long userId = actor.requireUserId();
        SendChannel channel = SendChannel.from(command.getChannel());
        Campaign campaign = campaignRepository.findById(command.getCampaignId());
        if (campaign == null) {
            throw CrmErrorCode.exception(CrmErrorCode.CAMPAIGN_NOT_FOUND);
        }
        requireCampaignScope(campaign, actor);
        CampaignAudience audience = campaignAudienceRepository.findById(command.getAudienceId());
        if (audience == null) {
            throw CrmErrorCode.exception(CrmErrorCode.AUDIENCE_NOT_FOUND);
        }
        if (!Objects.equals(audience.getCampaignId(), campaign.getId())) {
            throw CrmErrorCode.exception(CrmErrorCode.AUDIENCE_INVALID, "人群快照不属于该活动");
        }
        if (!Boolean.TRUE.equals(audience.getLocked())) {
            throw CrmErrorCode.exception(CrmErrorCode.AUDIENCE_INVALID, "人群快照尚未锁定，不能创建触达任务");
        }
        List<CampaignMember> members = campaignMemberRepository.findByAudience(audience.getId());
        List<MarketingSendItem> items = buildItems(members, command, campaign, channel, actor);
        MarketingSend send = new MarketingSend();
        send.setSendNo(nextCode());
        send.setCampaignId(campaign.getId());
        send.setAudienceId(audience.getId());
        send.setChannel(channel);
        send.setTemplateCode(command.getTemplateCode());
        send.setSubject(command.getSubject());
        send.setContentRefDocId(command.getContentRefDocId());
        send.setStatus(MarketingSendStatus.DRAFT);
        send.setTotalCount(items.size());
        send.setSentCount(0);
        send.setSkippedCount(0);
        send.setFailedCount(0);
        send.setOwnerId(command.getOwnerId() == null ? userId : command.getOwnerId());
        send.setDeptId(command.getDeptId());
        send.setLockVersion(0);
        marketingSendRepository.insert(send);
        for (MarketingSendItem item : items) {
            item.setSendId(send.getId());
        }
        marketingSendItemRepository.insertBatch(items);
        return MarketingSendView.from(send, items);
    }

    @Override
    @Transactional
    public MarketingSendView execute(Long id, CrmActor actor) {
        ensureGate();
        MarketingSend send = loadSend(id, actor);
        LocalDateTime now = LocalDateTime.now();
        send.beginExecute(now);
        List<MarketingSendItem> items = marketingSendItemRepository.findBySend(send.getId());
        int sent = 0;
        int skipped = 0;
        int failed = 0;
        String lastError = null;
        for (MarketingSendItem item : items) {
            if (item.getStatus() == SendItemStatus.SENT || item.getStatus() == SendItemStatus.SKIPPED) {
                continue;
            }
            if (item.getChannel() == null) {
                item.markFailed("触达渠道为空");
                failed++;
                lastError = "触达渠道为空";
                marketingSendItemRepository.update(item);
                continue;
            }
            if (item.getContactId() != null && marketingSendItemRepository.existsByCampaignContactChannel(
                    send.getCampaignId(), item.getContactId(), item.getChannel())) {
                item.markSkipped("同一活动同一渠道已触达");
                skipped++;
                marketingSendItemRepository.update(item);
                continue;
            }
            MessageDispatchResult result = dispatch(send, item);
            if (result == null) {
                result = MessageDispatchResult.failed("消息服务无返回");
            }
            if (result.status() == SendItemStatus.SENT) {
                item.setMsgId(result.messageId());
                item.markSent(now);
                sent++;
            } else if (result.status() == SendItemStatus.SKIPPED) {
                item.markSkipped(result.error());
                skipped++;
            } else {
                item.markFailed(result.error());
                failed++;
                lastError = result.error();
            }
            marketingSendItemRepository.update(item);
        }
        send.finish(sent, skipped, failed, lastError, now);
        marketingSendRepository.update(send);
        return MarketingSendView.from(send, marketingSendItemRepository.findBySend(send.getId()));
    }

    @Override
    @Transactional
    public DeliveryReceiptView applyDeliveryReceipt(DeliveryReceiptCommand command) {
        SendChannel channel = SendChannel.fromMessageChannel(command.getChannel());
        SendItemStatus target = SendItemStatus.fromReceipt(command.getStatus());
        MarketingSendItem item = resolveReceiptItem(command, channel);
        if (item == null) {
            return DeliveryReceiptView.of(false, null, null, null, "未匹配到触达明细");
        }
        String current = item.getStatus() == null ? null : item.getStatus().name();
        LocalDateTime at = command.getReceiptTime() == null ? LocalDateTime.now() : command.getReceiptTime();
        if (!item.applyReceipt(target, at, command.getErrorMessage())) {
            return DeliveryReceiptView.of(false, item.getId(), item.getSendId(), current,
                    "当前状态不可推进到 " + target.name());
        }
        marketingSendItemRepository.update(item);
        refreshReceiptCounters(item.getSendId());
        return DeliveryReceiptView.of(true, item.getId(), item.getSendId(), target.name(), null);
    }

    /**
     * 定位回执对应的触达明细：优先按消息服务消息ID，缺失时按业务标识（触达任务ID）+
     * 渠道 + 收件人兜底，兼容 V14 之前未落 {@code msg_id} 的历史明细。
     *
     * <p>兜底命中后顺带回填 {@code msgId}，让历史明细在首次回执后自动收敛到主匹配路径。</p>
     *
     * @param command 回执命令
     * @param channel 渠道
     * @return 明细；无法定位返回 {@code null}
     */
    private MarketingSendItem resolveReceiptItem(DeliveryReceiptCommand command, SendChannel channel) {
        MarketingSendItem item = marketingSendItemRepository.findByMsgId(command.getMessageId(), channel);
        if (item != null) {
            return item;
        }
        Long sendId = parseLong(command.getBizId());
        if (sendId == null) {
            return null;
        }
        MarketingSendItem matched = marketingSendItemRepository.findBySend(sendId).stream()
                .filter(candidate -> candidate.getChannel() == channel)
                .filter(candidate -> candidate.getStatus() != null && candidate.getStatus().isReached())
                .filter(candidate -> command.getRecipient() == null
                        || command.getRecipient().equals(candidate.getRecipient()))
                .findFirst().orElse(null);
        if (matched != null && matched.getMsgId() == null && command.getMessageId() != null) {
            matched.setMsgId(command.getMessageId());
        }
        return matched;
    }

    /**
     * 按明细状态重算任务侧的回执计数（送达 / 退信 / 已读）。
     *
     * @param sendId 触达任务 ID
     */
    private void refreshReceiptCounters(Long sendId) {
        MarketingSend send = sendId == null ? null : marketingSendRepository.findById(sendId);
        if (send == null) {
            return;
        }
        int delivered = 0;
        int bounced = 0;
        int read = 0;
        for (MarketingSendItem item : marketingSendItemRepository.findBySend(sendId)) {
            SendItemStatus status = item.getStatus();
            if (status == SendItemStatus.DELIVERED) {
                delivered++;
            } else if (status == SendItemStatus.READ) {
                delivered++;
                read++;
            } else if (status == SendItemStatus.BOUNCED) {
                bounced++;
            }
        }
        send.applyReceiptCounters(delivered, bounced, read);
        marketingSendRepository.update(send);
    }

    /**
     * 解析长整型标识。
     *
     * @param value 文本
     * @return 数值；无法解析返回 {@code null}
     */
    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 校验需求门禁与营销开关。
     */
    private void ensureGate() {        if (!crmProperties.isDemandGatePassed() || !marketingProperties.isEnabled()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED);
        }
    }

    /**
     * 加载触达任务并校验数据范围。
     *
     * @param id 任务 ID
     * @param actor 当前操作人
     * @return 触达任务
     */
    private MarketingSend loadSend(Long id, CrmActor actor) {
        MarketingSend send = id == null ? null : marketingSendRepository.findById(id);
        if (send == null) {
            throw CrmErrorCode.exception(CrmErrorCode.MARKETING_SEND_NOT_FOUND);
        }
        if (!actor.allDataScope() && !Objects.equals(send.getOwnerId(), actor.requireUserId())) {
            throw CrmErrorCode.exception(CrmErrorCode.MARKETING_SEND_NOT_FOUND);
        }
        return send;
    }

    /**
     * 校验活动数据范围。
     *
     * @param campaign 活动
     * @param actor 当前操作人
     */
    private void requireCampaignScope(Campaign campaign, CrmActor actor) {
        if (!actor.allDataScope() && !Objects.equals(campaign.getOwnerId(), actor.requireUserId())) {
            throw CrmErrorCode.exception(CrmErrorCode.CAMPAIGN_NOT_FOUND);
        }
    }

    /**
     * 由人群成员构建触达明细（排除 excluded，按联系人去重）。
     *
     * @param members 人群成员
     * @param command 创建命令
     * @param campaign 活动
     * @param channel 渠道
     * @param actor 当前操作人
     * @return 明细列表
     */
    private List<MarketingSendItem> buildItems(List<CampaignMember> members,
                                               MarketingSendCreateCommand command,
                                               Campaign campaign,
                                               SendChannel channel,
                                               CrmActor actor) {
        List<MarketingSendItem> items = new ArrayList<>();
        Set<Long> seenContacts = new LinkedHashSet<>();
        for (CampaignMember member : members) {
            if (Boolean.TRUE.equals(member.getExcluded()) || member.getContactId() == null) {
                continue;
            }
            if (!seenContacts.add(member.getContactId())) {
                continue;
            }
            ContactView contact = findContact(member.getCustomerId(), member.getContactId(), actor);
            MarketingSendItem item = new MarketingSendItem();
            item.setCampaignId(campaign.getId());
            item.setCustomerId(member.getCustomerId());
            item.setContactId(member.getContactId());
            item.setContactName(member.getContactName());
            item.setRecipient(recipient(contact, channel));
            item.setChannel(channel);
            item.setStatus(SendItemStatus.PENDING);
            items.add(item);
        }
        return items;
    }

    /**
     * 查询联系人视图。
     *
     * @param customerId 客户 ID
     * @param contactId 联系人 ID
     * @param actor 当前操作人
     * @return 联系人；不存在返回 null
     */
    private ContactView findContact(Long customerId, Long contactId, CrmActor actor) {
        List<ContactView> contacts = customerAppService.listContacts(customerId, actor);
        if (contacts == null) {
            return null;
        }
        return contacts.stream().filter(item -> Objects.equals(item.getId(), contactId))
                .findFirst().orElse(null);
    }

    /**
     * 按渠道推导收件人。
     *
     * @param contact 联系人
     * @param channel 渠道
     * @return 收件人
     */
    private String recipient(ContactView contact, SendChannel channel) {
        if (channel == SendChannel.INBOX) {
            return contact == null ? null : String.valueOf(contact.getId());
        }
        if (contact == null) {
            return null;
        }
        return channel == SendChannel.EMAIL ? contact.getEmail() : contact.getPhone();
    }

    /**
     * 投递单条明细消息。
     *
     * @param send 触达任务
     * @param item 明细
     * @return 投递结果
     */
    private MessageDispatchResult dispatch(MarketingSend send, MarketingSendItem item) {
        MarketingMessage message = new MarketingMessage();
        message.setSendId(send.getId());
        message.setCampaignId(send.getCampaignId());
        message.setCustomerId(item.getCustomerId());
        message.setContactId(item.getContactId());
        message.setContactName(item.getContactName());
        message.setRecipient(item.getRecipient());
        message.setChannel(item.getChannel());
        message.setTemplateCode(send.getTemplateCode());
        message.setSubject(send.getSubject());
        message.setContentDocId(send.getContentRefDocId());
        if (!StringUtils.hasText(item.getRecipient())) {
            return MessageDispatchResult.failed("收件人为空");
        }
        try {
            return messageDispatchPort.dispatch(message);
        } catch (RuntimeException exception) {
            return MessageDispatchResult.failed("消息服务异常：" + exception.getMessage());
        }
    }

    /**
     * 生成触达任务编号。
     *
     * @return 任务编号
     */
    private String nextCode() {
        String prefix = CODE_PREFIX + LocalDate.now().format(DATE_FORMAT) + "-";
        return prefix + String.format("%04d", marketingSendRepository.countBySendNoPrefix(prefix) + 1);
    }
}
