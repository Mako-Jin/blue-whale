package com.bluewhale.crm.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.config.CrmProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CrmMetaController} 单元测试：门禁未通过时只返回规划状态。
 *
 * @author BlueWhale
 */
class CrmMetaControllerTest {

    @Test
    @DisplayName("默认门禁未通过：返回提示与里程碑，不暴露业务数据")
    void shouldReportGateNotPassed() {
        CrmProperties properties = new CrmProperties();
        CrmMetaController controller = new CrmMetaController(properties);
        Result<CrmMetaController.CrmMetaView> result = controller.meta();

        assertEquals(0, result.getCode());
        CrmMetaController.CrmMetaView view = result.getData();
        assertFalse(view.isDemandGatePassed());
        assertTrue(view.getMessage().contains("门禁未通过"));
        assertEquals(2, view.getMilestones().size());
        assertEquals(3, view.getBoundaries().size());
    }

    @Test
    @DisplayName("门禁通过后返回 M4 提示")
    void shouldReportGatePassed() {
        CrmProperties properties = new CrmProperties();
        properties.setDemandGatePassed(true);
        CrmMetaController.CrmMetaView view = new CrmMetaController(properties).meta().getData();

        assertTrue(view.isDemandGatePassed());
        assertTrue(view.getMessage().contains("M4"));
        assertNotEquals("需求门禁未通过", view.getMessage());
    }

    @Test
    @DisplayName("运行参数默认值可读可写（门禁通过后 M4 使用）")
    void shouldExposeProperties() {
        CrmProperties properties = new CrmProperties();
        properties.setCustomerCodePrefix("CUS");
        properties.setFollowUpPageLimit(50);
        properties.setContractReferenceEnabled(true);

        assertEquals("CUS", properties.getCustomerCodePrefix());
        assertEquals(50, properties.getFollowUpPageLimit());
        assertTrue(properties.isContractReferenceEnabled());
    }
}
