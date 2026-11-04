package com.bluewhale.scene.application.support;

import com.bluewhale.scene.domain.model.NodeBinding;
import com.bluewhale.scene.domain.model.SceneNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SceneLayoutSnapshot} 单元测试。
 *
 * @author BlueWhale
 */
class SceneLayoutSnapshotTest {

    @Test
    @DisplayName("同一布局生成稳定 JSON 与校验和")
    void shouldBuildStableSnapshot() {
        SceneNode node = new SceneNode();
        node.setId(1L);
        node.setNodeKey("oa-hall");
        node.setParentKey("city-core");
        node.setNodeType("ENTRANCE");
        node.setName("协同办公");
        node.setX(250);
        node.setY(300);
        node.setSortNo(1);
        NodeBinding binding = new NodeBinding();
        binding.setNodeId(1L);
        binding.setEntryType("SYSTEM");
        binding.setTargetPath("/oa");

        SceneLayoutSnapshot.Snapshot first = SceneLayoutSnapshot.build("bluewhale-city",
                List.of(node), Map.of(1L, binding));
        SceneLayoutSnapshot.Snapshot second = SceneLayoutSnapshot.build("bluewhale-city",
                List.of(node), Map.of(1L, binding));

        assertEquals(first.layoutJson(), second.layoutJson());
        assertEquals(first.checksum(), second.checksum());
        assertEquals(64, first.checksum().length());
        assertTrue(first.layoutJson().contains("\"targetPath\":\"/oa\""));
    }

    @Test
    @DisplayName("不同布局校验和不同")
    void shouldProduceDifferentChecksum() {
        assertNotEquals(SceneLayoutSnapshot.checksum("a"), SceneLayoutSnapshot.checksum("b"));
    }
}
