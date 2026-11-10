package com.bluewhale.scene.application.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.scene.domain.exception.SceneErrorCode;
import com.bluewhale.scene.domain.model.SceneNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link SceneValidator} 单元测试。
 *
 * @author BlueWhale
 */
class SceneValidatorTest {

    @Test
    @DisplayName("合法场景标识通过校验")
    void shouldAcceptValidSceneKey() {
        SceneValidator.validateSceneKey("bluewhale-city");
    }

    @Test
    @DisplayName("非法场景标识被拒绝")
    void shouldRejectInvalidSceneKey() {
        for (String key : List.of("", "A", "1city", "City-Upper", "city_key", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")) {
            BizException exception = assertThrows(BizException.class,
                    () -> SceneValidator.validateSceneKey(key));
            assertEquals(SceneErrorCode.SCENE_KEY_INVALID, exception.getCode());
        }
    }

    @Test
    @DisplayName("节点标识与坐标校验")
    void shouldValidateNodeShape() {
        SceneValidator.validateNodeKey("oa-hall");
        SceneValidator.validateNodeShape("ENTRANCE", 0, 1000);

        assertEquals(SceneErrorCode.NODE_INVALID, assertThrows(BizException.class,
                () -> SceneValidator.validateNodeKey("-bad")).getCode());
        assertEquals(SceneErrorCode.NODE_INVALID, assertThrows(BizException.class,
                () -> SceneValidator.validateNodeShape("UNKNOWN", 10, 10)).getCode());
        assertEquals(SceneErrorCode.NODE_INVALID, assertThrows(BizException.class,
                () -> SceneValidator.validateNodeShape("REGION", null, 10)).getCode());
        assertEquals(SceneErrorCode.NODE_INVALID, assertThrows(BizException.class,
                () -> SceneValidator.validateNodeShape("REGION", -1, 10)).getCode());
        assertEquals(SceneErrorCode.NODE_INVALID, assertThrows(BizException.class,
                () -> SceneValidator.validateNodeShape("REGION", 1001, 10)).getCode());
    }

    @Test
    @DisplayName("入口类型校验")
    void shouldValidateEntryType() {
        SceneValidator.validateEntryType("SYSTEM");
        assertEquals(SceneErrorCode.BINDING_TYPE_INVALID, assertThrows(BizException.class,
                () -> SceneValidator.validateEntryType("UNKNOWN")).getCode());
    }

    @Test
    @DisplayName("入口地址白名单：平台内路径与 https 外链通过")
    void shouldAcceptSafeTargetPath() {
        SceneValidator.validateTargetPath("/oa/leave", "http,https");
        SceneValidator.validateTargetPath("https://example.com/oa", "http,https");
        SceneValidator.validateTargetPath("http://example.com:8080/erp", "http,https");
    }

    @Test
    @DisplayName("入口地址拒绝脚本协议、协议相对与内网地址")
    void shouldRejectUnsafeTargetPath() {
        List<String> unsafe = List.of(
                "",
                "javascript:alert(1)",
                "data:text/html;base64,PHNjcmlwdD4=",
                "//evil.example.com",
                "ftp://example.com/file",
                "http://localhost/oa",
                "http://127.0.0.1/oa",
                "http://10.1.2.3/oa",
                "http://192.168.1.1/oa",
                "http://172.16.0.1/oa",
                "http://169.254.1.1/oa",
                "http://10.0.0.1/oa",
                "http://user@evil.example.com/oa",
                "http://metadata.google.internal/");
        for (String path : unsafe) {
            BizException exception = assertThrows(BizException.class,
                    () -> SceneValidator.validateTargetPath(path, "http,https"));
            assertEquals(SceneErrorCode.BINDING_URL_INVALID, exception.getCode(), path);
        }
    }

    @Test
    @DisplayName("节点树：合法树通过，重复 / 缺父 / 环 / 超深被拒绝")
    void shouldValidateTree() {
        List<SceneNode> valid = List.of(node("root", null, 1), node("mid", "root", 2),
                node("leaf", "mid", 3));
        SceneValidator.validateTree(valid, 5);

        List<SceneNode> duplicated = List.of(node("root", null, 1), node("root", null, 2));
        assertEquals(SceneErrorCode.NODE_INVALID, assertThrows(BizException.class,
                () -> SceneValidator.validateTree(duplicated, 5)).getCode());

        List<SceneNode> missingParent = List.of(node("leaf", "ghost", 1));
        assertEquals(SceneErrorCode.NODE_INVALID, assertThrows(BizException.class,
                () -> SceneValidator.validateTree(missingParent, 5)).getCode());

        List<SceneNode> cycle = List.of(node("a", "b", 1), node("b", "a", 2));
        assertEquals(SceneErrorCode.NODE_CYCLE, assertThrows(BizException.class,
                () -> SceneValidator.validateTree(cycle, 5)).getCode());

        List<SceneNode> deep = List.of(node("n1", null, 1), node("n2", "n1", 2),
                node("n3", "n2", 3), node("n4", "n3", 4), node("n5", "n4", 5),
                node("n6", "n5", 6));
        assertEquals(SceneErrorCode.NODE_DEPTH_EXCEEDED, assertThrows(BizException.class,
                () -> SceneValidator.validateTree(deep, 5)).getCode());
    }

    @Test
    @DisplayName("可见性规则校验")
    void shouldValidateVisibility() {
        SceneValidator.validateVisibility("ALL", null, "ALLOW", 0L, Set.of());
        SceneValidator.validateVisibility("ROLE", "admin", "DENY", 5102L, Set.of(5102L));

        assertEquals(SceneErrorCode.VISIBILITY_INVALID, assertThrows(BizException.class,
                () -> SceneValidator.validateVisibility("UNKNOWN", "x", "ALLOW", 0L, Set.of())).getCode());
        assertEquals(SceneErrorCode.VISIBILITY_INVALID, assertThrows(BizException.class,
                () -> SceneValidator.validateVisibility("ROLE", null, "ALLOW", 0L, Set.of())).getCode());
        assertEquals(SceneErrorCode.VISIBILITY_INVALID, assertThrows(BizException.class,
                () -> SceneValidator.validateVisibility("ALL", null, "BLOCK", 0L, Set.of())).getCode());
        assertEquals(SceneErrorCode.VISIBILITY_INVALID, assertThrows(BizException.class,
                () -> SceneValidator.validateVisibility("ALL", null, "ALLOW", 5102L, Set.of())).getCode());
        assertEquals(SceneErrorCode.VISIBILITY_INVALID, assertThrows(BizException.class,
                () -> SceneValidator.validateVisibility("ALL", null, "ALLOW", null, Set.of())).getCode());
    }

    /**
     * 构造节点。
     *
     * @param key 节点标识
     * @param parentKey 父节点标识
     * @param sortNo 排序号
     * @return 节点
     */
    private SceneNode node(String key, String parentKey, int sortNo) {
        SceneNode node = new SceneNode();
        node.setNodeKey(key);
        node.setParentKey(parentKey);
        node.setSortNo(sortNo);
        return node;
    }
}
