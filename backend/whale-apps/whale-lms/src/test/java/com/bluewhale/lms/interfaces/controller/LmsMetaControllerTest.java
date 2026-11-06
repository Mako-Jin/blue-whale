package com.bluewhale.lms.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.lms.config.LmsProperties;
import com.bluewhale.lms.interfaces.controller.LmsMetaController.LmsMetaView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link LmsMetaController} 单元测试：门禁开合两种返回口径。
 *
 * @author BlueWhale
 */
class LmsMetaControllerTest {

    private LmsProperties properties;

    private LmsMetaController controller;

    @BeforeEach
    void setUp() {
        properties = new LmsProperties();
        controller = new LmsMetaController(properties);
    }

    @Test
    @DisplayName("门禁通过：返回启用说明与里程碑")
    void metaShouldReportOpenGate() {
        Result<LmsMetaView> result = controller.meta();
        assertTrue(result.getData().isDemandGatePassed());
        assertTrue(result.getData().getMessage().contains("已启用"));
        assertEquals(7, result.getData().getMilestones().size());
        assertEquals(3, result.getData().getBoundaries().size());
    }

    @Test
    @DisplayName("门禁关闭：仅返回维护态说明")
    void metaShouldReportClosedGate() {
        properties.setDemandGatePassed(false);
        Result<LmsMetaView> result = controller.meta();
        assertFalse(result.getData().isDemandGatePassed());
        assertTrue(result.getData().getMessage().contains("未启用"));
    }
}
