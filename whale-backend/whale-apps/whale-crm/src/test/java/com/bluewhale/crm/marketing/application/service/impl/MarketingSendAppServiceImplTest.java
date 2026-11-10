package com.bluewhale.crm.marketing.application.service.impl;

import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.config.CrmMarketingProperties;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.customer.application.service.CustomerAppService;
import com.bluewhale.crm.marketing.application.view.MarketingSendView;
import com.bluewhale.crm.marketing.domain.model.MarketingSend;
import com.bluewhale.crm.marketing.domain.model.MarketingSendItem;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MarketingSendAppServiceImpl} 执行逻辑单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("批量触达应用服务")
class MarketingSendAppServiceImplTest {

    @Mock
    private MarketingSendRepository marketingSendRepository;

    @Mock
    private MarketingSendItemRepository marketingSendItemRepository;

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private CampaignAudienceRepository campaignAudienceRepository;

    @Mock
    private CampaignMemberRepository campaignMemberRepository;

    @Mock
    private CustomerAppService customerAppService;

    @Mock
    private MessageDispatchPort messageDispatchPort;

    private MarketingSendAppServiceImpl service;

    private final CrmActor actor = new CrmActor(1L, "tester", Set.of("crm_admin"), Set.of());

    @BeforeEach
    void setUp() {
        service = new MarketingSendAppServiceImpl(marketingSendRepository, marketingSendItemRepository,
                campaignRepository, campaignAudienceRepository, campaignMemberRepository, customerAppService,
                messageDispatchPort, new CrmProperties(), new CrmMarketingProperties());
    }

    @Test
    @DisplayName("重复触达按活动+联系人+渠道幂等跳过并计入跳过数")
    void duplicateSendSkipped() {
        MarketingSend send = draftSend();
        MarketingSendItem first = pendingItem(1L, 11L);
        MarketingSendItem second = pendingItem(2L, 12L);
        when(marketingSendRepository.findById(10L)).thenReturn(send);
        when(marketingSendItemRepository.findBySend(10L)).thenReturn(List.of(first, second));
        when(marketingSendItemRepository.existsByCampaignContactChannel(100L, 11L, SendChannel.EMAIL))
                .thenReturn(true);
        when(marketingSendItemRepository.existsByCampaignContactChannel(100L, 12L, SendChannel.EMAIL))
                .thenReturn(false);
        when(messageDispatchPort.dispatch(any())).thenReturn(MessageDispatchResult.sent());

        MarketingSendView view = service.execute(10L, actor);

        assertEquals(MarketingSendStatus.COMPLETED.name(), view.getStatus());
        assertEquals(1, send.getSentCount());
        assertEquals(1, send.getSkippedCount());
        assertEquals(0, send.getFailedCount());
        assertEquals(SendItemStatus.SKIPPED, first.getStatus());
        assertEquals(SendItemStatus.SENT, second.getStatus());
        verify(marketingSendRepository).update(send);
    }

    @Test
    @DisplayName("消息服务不可用时任务失败且不伪造成功")
    void dispatchFailureMarksFailed() {
        MarketingSend send = draftSend();
        MarketingSendItem first = pendingItem(1L, 11L);
        MarketingSendItem second = pendingItem(2L, 12L);
        when(marketingSendRepository.findById(10L)).thenReturn(send);
        when(marketingSendItemRepository.findBySend(10L)).thenReturn(List.of(first, second));
        when(messageDispatchPort.dispatch(any())).thenReturn(MessageDispatchResult.failed("消息服务不可用"));

        MarketingSendView view = service.execute(10L, actor);

        assertEquals(MarketingSendStatus.FAILED.name(), view.getStatus());
        assertEquals(0, send.getSentCount());
        assertEquals(0, send.getSkippedCount());
        assertEquals(2, send.getFailedCount());
        assertNotNull(send.getLastError());
        assertTrue(send.getLastError().contains("消息服务不可用"));
        assertEquals(SendItemStatus.FAILED, first.getStatus());
        assertEquals(SendItemStatus.FAILED, second.getStatus());
    }

    /**
     * 构造草稿触达任务。
     *
     * @return 触达任务
     */
    private MarketingSend draftSend() {
        MarketingSend send = new MarketingSend();
        send.setId(10L);
        send.setCampaignId(100L);
        send.setAudienceId(20L);
        send.setChannel(SendChannel.EMAIL);
        send.setStatus(MarketingSendStatus.DRAFT);
        send.setTotalCount(2);
        return send;
    }

    /**
     * 构造待发送明细。
     *
     * @param id 明细 ID
     * @param contactId 联系人 ID
     * @return 明细
     */
    private MarketingSendItem pendingItem(Long id, Long contactId) {
        MarketingSendItem item = new MarketingSendItem();
        item.setId(id);
        item.setSendId(10L);
        item.setCampaignId(100L);
        item.setCustomerId(contactId);
        item.setContactId(contactId);
        item.setRecipient("user" + contactId + "@example.com");
        item.setChannel(SendChannel.EMAIL);
        item.setStatus(SendItemStatus.PENDING);
        return item;
    }
}
