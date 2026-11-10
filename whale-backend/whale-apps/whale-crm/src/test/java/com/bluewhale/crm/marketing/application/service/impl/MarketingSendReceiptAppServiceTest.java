package com.bluewhale.crm.marketing.application.service.impl;

import com.bluewhale.crm.config.CrmMarketingProperties;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.marketing.application.command.DeliveryReceiptCommand;
import com.bluewhale.crm.marketing.application.view.DeliveryReceiptView;
import com.bluewhale.crm.marketing.domain.model.MarketingSend;
import com.bluewhale.crm.marketing.domain.model.MarketingSendItem;
import com.bluewhale.crm.marketing.domain.model.SendChannel;
import com.bluewhale.crm.marketing.domain.model.SendItemStatus;
import com.bluewhale.crm.marketing.domain.port.MessageDispatchPort;
import com.bluewhale.crm.marketing.domain.repository.CampaignAudienceRepository;
import com.bluewhale.crm.marketing.domain.repository.CampaignMemberRepository;
import com.bluewhale.crm.marketing.domain.repository.CampaignRepository;
import com.bluewhale.crm.marketing.domain.repository.MarketingSendItemRepository;
import com.bluewhale.crm.marketing.domain.repository.MarketingSendRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 触达回执承接应用服务单元测试。
 *
 * @author BlueWhale
 */
class MarketingSendReceiptAppServiceTest {

    /** 触达任务ID */
    private static final long SEND_ID = 9001L;

    /** 消息ID */
    private static final long MESSAGE_ID = 1001L;

    private final MarketingSendRepository marketingSendRepository = mock(MarketingSendRepository.class);

    private final MarketingSendItemRepository marketingSendItemRepository = mock(MarketingSendItemRepository.class);

    private final CampaignRepository campaignRepository = mock(CampaignRepository.class);

    private final CampaignAudienceRepository campaignAudienceRepository = mock(CampaignAudienceRepository.class);

    private final CampaignMemberRepository campaignMemberRepository = mock(CampaignMemberRepository.class);

    private final CustomerAppService customerAppService = mock(CustomerAppService.class);

    private final MessageDispatchPort messageDispatchPort = mock(MessageDispatchPort.class);

    private final CrmProperties crmProperties = new CrmProperties();

    private final CrmMarketingProperties marketingProperties = new CrmMarketingProperties();

    /**
     * 构造被测服务。
     *
     * @return 应用服务
     */
    private MarketingSendAppServiceImpl service() {
        return new MarketingSendAppServiceImpl(marketingSendRepository, marketingSendItemRepository,
                campaignRepository, campaignAudienceRepository, campaignMemberRepository,
                customerAppService, messageDispatchPort, crmProperties, marketingProperties);
    }

    /**
     * 构造指定状态的明细。
     *
     * @param status 状态
     * @return 明细
     */
    private static MarketingSendItem item(SendItemStatus status) {
        MarketingSendItem item = new MarketingSendItem();
        item.setId(501L);
        item.setSendId(SEND_ID);
        item.setMsgId(MESSAGE_ID);
        item.setChannel(SendChannel.INBOX);
        item.setRecipient("101");
        item.setStatus(status);
        return item;
    }

    /**
     * 构造回执命令。
     *
     * @param status 回执状态
     * @return 命令
     */
    private static DeliveryReceiptCommand command(String status) {
        DeliveryReceiptCommand command = new DeliveryReceiptCommand();
        command.setMessageId(MESSAGE_ID);
        command.setBizId(String.valueOf(SEND_ID));
        command.setChannel("INBOX");
        command.setStatus(status);
        command.setRecipient("101");
        command.setReceiptTime(LocalDateTime.of(2026, 11, 8, 18, 0));
        return command;
    }

    @Test
    @DisplayName("按消息ID命中明细：推进状态并重算送达计数")
    void shouldAdvanceItemAndRefreshCounters() {
        MarketingSendItem item = item(SendItemStatus.SENT);
        when(marketingSendItemRepository.findByMsgId(MESSAGE_ID, SendChannel.INBOX)).thenReturn(item);
        when(marketingSendItemRepository.findBySend(SEND_ID)).thenReturn(List.of(item));
        MarketingSend send = new MarketingSend();
        send.setId(SEND_ID);
        when(marketingSendRepository.findById(SEND_ID)).thenReturn(send);

        DeliveryReceiptView view = service().applyDeliveryReceipt(command("DELIVERED"));

        assertTrue(view.isApplied());
        assertEquals("DELIVERED", view.getStatus());
        assertEquals(SendItemStatus.DELIVERED, item.getStatus());
        verify(marketingSendItemRepository).update(item);
        ArgumentCaptor<MarketingSend> captor = ArgumentCaptor.forClass(MarketingSend.class);
        verify(marketingSendRepository).update(captor.capture());
        assertEquals(1, captor.getValue().getDeliveredCount());
        assertEquals(0, captor.getValue().getReadCount());
    }

    @Test
    @DisplayName("幂等：已是终态时返回未推进原因，不重复写库")
    void shouldSkipWhenStatusCannotAdvance() {
        MarketingSendItem item = item(SendItemStatus.READ);
        when(marketingSendItemRepository.findByMsgId(MESSAGE_ID, SendChannel.INBOX)).thenReturn(item);

        DeliveryReceiptView view = service().applyDeliveryReceipt(command("DELIVERED"));

        assertFalse(view.isApplied());
        assertEquals("READ", view.getStatus());
        assertEquals(SendItemStatus.READ, item.getStatus());
    }

    @Test
    @DisplayName("未匹配到明细时明确返回未推进，不伪造成功")
    void shouldReturnNotAppliedWhenItemMissing() {
        when(marketingSendItemRepository.findByMsgId(any(), any())).thenReturn(null);
        when(marketingSendItemRepository.findBySend(SEND_ID)).thenReturn(List.of());

        DeliveryReceiptView view = service().applyDeliveryReceipt(command("DELIVERED"));

        assertFalse(view.isApplied());
        assertEquals("未匹配到触达明细", view.getReason());
    }

    @Test
    @DisplayName("消息服务渠道编码 INAPP / IM 归一为 INBOX 后再匹配")
    void shouldNormalizeMessageChannel() {
        MarketingSendItem item = item(SendItemStatus.SENT);
        when(marketingSendItemRepository.findByMsgId(MESSAGE_ID, SendChannel.INBOX)).thenReturn(item);
        when(marketingSendItemRepository.findBySend(SEND_ID)).thenReturn(List.of(item));
        MarketingSend send = new MarketingSend();
        send.setId(SEND_ID);
        when(marketingSendRepository.findById(SEND_ID)).thenReturn(send);
        DeliveryReceiptCommand command = command("DELIVERED");
        command.setChannel("INAPP");

        DeliveryReceiptView view = service().applyDeliveryReceipt(command);

        assertTrue(view.isApplied());
        assertEquals(SendItemStatus.DELIVERED, item.getStatus());
    }

    @Test
    @DisplayName("历史明细（无消息ID）按业务标识+渠道+收件人兜底匹配")
    void shouldFallbackMatchLegacyItem() {
        MarketingSendItem legacy = item(SendItemStatus.SENT);
        legacy.setMsgId(null);
        when(marketingSendItemRepository.findByMsgId(any(), any())).thenReturn(null);
        when(marketingSendItemRepository.findBySend(SEND_ID)).thenReturn(List.of(legacy));
        MarketingSend send = new MarketingSend();
        send.setId(SEND_ID);
        when(marketingSendRepository.findById(SEND_ID)).thenReturn(send);

        DeliveryReceiptView view = service().applyDeliveryReceipt(command("READ"));

        assertTrue(view.isApplied());
        assertEquals(SendItemStatus.READ, legacy.getStatus());
    }
}
