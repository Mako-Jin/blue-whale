package com.bluewhale.crm.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.config.CrmProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CrmMetaController} 单元测试：门禁默认通过，可显式回退维护态。
 *
 * @author BlueWhale
 */
class CrmMetaControllerTest {

    @Test
    @DisplayName("默认为门禁通过：返回 M0/M1 里程碑")
    void shouldReportGatePassedByDefault() {
        CrmProperties properties = new CrmProperties();
        CrmMetaController controller = new CrmMetaController(properties);
        Result<CrmMetaController.CrmMetaView> result = controller.meta();

        assertEquals(0, result.getCode());
        CrmMetaController.CrmMetaView view = result.getData();
        assertTrue(view.isDemandGatePassed());
        assertTrue(view.getMessage().contains("M1"));
        assertEquals(3, view.getMilestones().size());
        assertEquals(3, view.getBoundaries().size());
    }

    @Test
    @DisplayName("显式关闭门禁：返回维护态提示")
    void shouldReportGateClosed() {
        CrmProperties properties = new CrmProperties();
        properties.setDemandGatePassed(false);
        CrmMetaController.CrmMetaView view = new CrmMetaController(properties).meta().getData();

        assertFalse(view.isDemandGatePassed());
        assertTrue(view.getMessage().contains("门禁未通过"));
        assertTrue(view.getMessage().contains("业务接口未启用"));
    }

    @Test
    @DisplayName("运行参数默认值可读可写")
    void shouldExposeProperties() {
        CrmProperties properties = new CrmProperties();
        properties.setCustomerCodePrefix("CUS");
        properties.setFollowUpPageLimit(50);
        properties.setContractReferenceEnabled(true);
        properties.setPoolRecycleEnabled(true);

        assertEquals("CUS", properties.getCustomerCodePrefix());
        assertEquals(50, properties.getFollowUpPageLimit());
        assertTrue(properties.isContractReferenceEnabled());
        assertTrue(properties.isPoolRecycleEnabled());
    }

    @Test
    @DisplayName("M0 默认参数：客户默认 C 级、公海回收默认关闭")
    void shouldExposeFoundationDefaults() {
        CrmProperties properties = new CrmProperties();
        assertEquals("C", properties.getDefaultCustomerLevel());
        assertFalse(properties.isPoolRecycleEnabled());
        assertEquals(200, properties.getPoolRecycleBatchSize());
    }
}
