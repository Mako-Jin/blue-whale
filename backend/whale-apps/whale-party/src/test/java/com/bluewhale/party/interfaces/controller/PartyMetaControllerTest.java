package com.bluewhale.party.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.interfaces.controller.PartyMetaController.PartyMetaView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PartyMetaController} 单元测试：门禁开合两种返回口径。
 *
 * @author BlueWhale
 */
class PartyMetaControllerTest {

    private PartyProperties properties;

    private PartyMetaController controller;

    @BeforeEach
    void setUp() {
        properties = new PartyProperties();
        controller = new PartyMetaController(properties);
    }

    @Test
    @DisplayName("门禁通过：返回启用说明与里程碑")
    void metaShouldReportOpenGate() {
        Result<PartyMetaView> result = controller.meta();
        assertTrue(result.getData().isDemandGatePassed());
        assertTrue(result.getData().getMessage().contains("已启用"));
        assertEquals(5, result.getData().getMilestones().size());
        assertEquals(4, result.getData().getBoundaries().size());
    }

    @Test
    @DisplayName("门禁关闭：仅返回维护态说明")
    void metaShouldReportClosedGate() {
        properties.setDemandGatePassed(false);
        Result<PartyMetaView> result = controller.meta();
        assertFalse(result.getData().isDemandGatePassed());
        assertTrue(result.getData().getMessage().contains("未启用"));
    }

    @Test
    @DisplayName("边界说明必须写明不读 OA 表")
    void boundariesShouldDeclareNoOaDependency() {
        Result<PartyMetaView> result = controller.meta();
        assertTrue(result.getData().getBoundaries().stream()
                        .anyMatch(line -> line.contains("bluewhale_oa")),
                "党政工会独立成服务后，边界说明必须显式声明不读 OA 库");
    }

    @Test
    @DisplayName("默认参数：支部层级上限与分页上限为正数")
    void defaultPropertiesShouldBeSane() {
        assertTrue(properties.getBranchMaxDepth() > 1);
        assertTrue(properties.getBranchPageLimit() > 0);
        assertTrue(properties.getMemberSyncPageSize() > 0);
    }
}
