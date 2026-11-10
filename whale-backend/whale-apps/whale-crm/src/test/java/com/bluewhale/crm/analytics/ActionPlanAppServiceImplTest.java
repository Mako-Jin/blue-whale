package com.bluewhale.crm.analytics;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.analytics.application.service.impl.ActionPlanAppServiceImpl;
import com.bluewhale.crm.analytics.domain.model.ActionItem;
import com.bluewhale.crm.analytics.domain.model.ActionItemQuery;
import com.bluewhale.crm.analytics.domain.model.ActionItemStatus;
import com.bluewhale.crm.analytics.domain.model.ActionRule;
import com.bluewhale.crm.analytics.domain.model.ActionRuleType;
import com.bluewhale.crm.analytics.domain.port.AnalyticsSourcePort;
import com.bluewhale.crm.analytics.domain.repository.ActionItemRepository;
import com.bluewhale.crm.analytics.domain.repository.ActionRuleRepository;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmAnalyticsProperties;
import com.bluewhale.crm.config.CrmProperties;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ActionPlanAppServiceImpl} 应用服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class ActionPlanAppServiceImplTest {

    @Mock
    private ActionRuleRepository actionRuleRepository;

    @Mock
    private ActionItemRepository actionItemRepository;

    @Mock
    private AnalyticsSourcePort analyticsSourcePort;

    private CrmProperties crmProperties;

    private CrmAnalyticsProperties analyticsProperties;

    private ActionPlanAppServiceImpl service;

    @BeforeEach
    void setUp() {
        crmProperties = new CrmProperties();
        analyticsProperties = new CrmAnalyticsProperties();
        service = new ActionPlanAppServiceImpl(actionRuleRepository, actionItemRepository, analyticsSourcePort,
                crmProperties, analyticsProperties);
    }

    @Test
    @DisplayName("手工关闭：原因空白时拒绝并提示必填")
    void shouldRejectClosingWithoutReason() {
        when(actionItemRepository.findById(1L)).thenReturn(openItem());

        BizException exception = assertThrows(BizException.class,
                () -> service.closeItem(1L, "   ", adminActor()));

        assertEquals(CrmErrorCode.PARAM_INVALID, exception.getCode());
        verify(actionItemRepository, never()).update(any(ActionItem.class));
    }

    @Test
    @DisplayName("手工关闭：已关闭行动项再次关闭返回状态冲突")
    void shouldRejectClosingClosedItem() {
        ActionItem item = openItem();
        item.setStatus(ActionItemStatus.CLOSED);
        when(actionItemRepository.findById(1L)).thenReturn(item);

        BizException exception = assertThrows(BizException.class,
                () -> service.closeItem(1L, "重复关闭", adminActor()));

        assertEquals(CrmErrorCode.ACTION_ITEM_STATE_CONFLICT, exception.getCode());
        verify(actionItemRepository, never()).update(any(ActionItem.class));
    }

    @Test
    @DisplayName("手工关闭：OPEN 行动项写入原因、关闭人与关闭时间")
    void shouldCloseOpenItemWithReason() {
        ActionItem item = openItem();
        when(actionItemRepository.findById(1L)).thenReturn(item);

        ActionItem result = service.closeItem(1L, " 已电话沟通 ", adminActor());

        assertEquals(ActionItemStatus.CLOSED, result.getStatus());
        assertEquals("已电话沟通", result.getCloseReason());
        assertEquals(1L, result.getClosedBy());
        assertNotNull(result.getClosedAt());
        verify(actionItemRepository).update(item);
    }

    @Test
    @DisplayName("手工关闭：行动项不存在返回 ACTION_ITEM_NOT_FOUND")
    void shouldRejectClosingMissingItem() {
        when(actionItemRepository.findById(1L)).thenReturn(null);

        BizException exception = assertThrows(BizException.class,
                () -> service.closeItem(1L, "已处理", adminActor()));

        assertEquals(CrmErrorCode.ACTION_ITEM_NOT_FOUND, exception.getCode());
    }

    @Test
    @DisplayName("手工关闭：非全量数据范围且非本人负责时按不存在处理")
    void shouldHideItemOutsideDataScope() {
        ActionItem item = openItem();
        when(actionItemRepository.findById(1L)).thenReturn(item);

        BizException exception = assertThrows(BizException.class,
                () -> service.closeItem(1L, "已处理", regularActor()));

        assertEquals(CrmErrorCode.ACTION_ITEM_NOT_FOUND, exception.getCode());
        verify(actionItemRepository, never()).update(any(ActionItem.class));
    }

    @Test
    @DisplayName("分页：非全量数据范围自动按负责人过滤")
    void shouldScopePageForRegularUser() {
        when(actionItemRepository.page(any(ActionItemQuery.class))).thenReturn(List.of());
        when(actionItemRepository.count(any(ActionItemQuery.class))).thenReturn(0L);
        ActionItemQuery query = new ActionItemQuery();

        PageResult<ActionItem> result = service.page(query, regularActor());

        assertEquals(1L, query.getScopeOwnerId());
        assertTrue(result.getRecords().isEmpty());
    }

    @Test
    @DisplayName("门禁：需求门禁未通过或分析模块未启用时拒绝访问")
    void shouldRejectWhenGateClosed() {
        crmProperties.setDemandGatePassed(false);
        BizException gateClosed = assertThrows(BizException.class,
                () -> service.page(new ActionItemQuery(), adminActor()));
        assertEquals(CrmErrorCode.GATE_CLOSED, gateClosed.getCode());

        crmProperties.setDemandGatePassed(true);
        analyticsProperties.setEnabled(false);
        BizException disabled = assertThrows(BizException.class,
                () -> service.page(new ActionItemQuery(), adminActor()));
        assertEquals(CrmErrorCode.GATE_CLOSED, disabled.getCode());
    }

    @Test
    @DisplayName("停用规则：关闭规则后 enabled 置为 false 并持久化")
    void shouldDisableRule() {
        ActionRule rule = new ActionRule();
        rule.setId(10L);
        rule.setRuleCode(ActionRuleType.CUSTOMER_IDLE.name());
        rule.setEnabled(Boolean.TRUE);
        when(actionRuleRepository.findById(10L)).thenReturn(rule);

        ActionRule result = service.closeRule(10L, adminActor());

        assertEquals(Boolean.FALSE, result.getEnabled());
        verify(actionRuleRepository).update(rule);
    }

    /**
     * 构造待处理行动项。
     *
     * @return 行动项
     */
    private ActionItem openItem() {
        ActionItem item = new ActionItem();
        item.setId(1L);
        item.setStatus(ActionItemStatus.OPEN);
        item.setOwnerId(20L);
        item.setTitle("客户「客户A」待跟进");
        item.setDedupKey("KEY_CUSTOMER_IDLE:100:2026-11");
        return item;
    }

    /**
     * 构造管理员操作人。
     *
     * @return 操作人
     */
    private CrmActor adminActor() {
        return new CrmActor(1L, "admin", Set.of("tenant_admin"), Set.of());
    }

    /**
     * 构造普通销售操作人。
     *
     * @return 操作人
     */
    private CrmActor regularActor() {
        return new CrmActor(1L, "sales", Set.of("crm_sales"), Set.of());
    }
}
