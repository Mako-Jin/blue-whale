package com.bluewhale.file.service.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.file.config.FileProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link UploadPolicyRegistry} 规则快照与热更新校验的单元测试。
 *
 * @author BlueWhale
 */
class UploadPolicyRegistryTest {

    private static final int PARAM_ERROR_CODE = 400;

    private static final long VIDEO_MAX_SIZE = 2147483648L;

    private FileProperties fileProperties;

    private UploadPolicyRegistry registry;

    /**
     * 每个用例使用内置默认规则（视频 / 电影 / 音乐）
     */
    @BeforeEach
    void setUp() {
        fileProperties = new FileProperties();
        registry = new UploadPolicyRegistry(fileProperties);
    }

    @Test
    @DisplayName("初始快照来自配置：默认 3 条规则")
    void shouldCopyConfiguredRules() {
        List<FileProperties.Rule> rules = registry.rules();

        assertEquals(3, rules.size());
        assertEquals("sys:video:upload", rules.get(0).getPermission());
        assertEquals(VIDEO_MAX_SIZE, rules.get(0).getMaxSizeBytes());
    }

    @Test
    @DisplayName("替换后返回新快照并生效")
    void shouldReplaceRules() {
        List<FileProperties.Rule> replaced = registry.replace(
                List.of(FileProperties.Rule.of("sys:file:upload", 209715200L, null)));

        assertEquals(replaced, registry.rules());
        assertEquals(1, registry.rules().size());
        assertEquals("sys:file:upload", registry.rules().get(0).getPermission());
        assertEquals(209715200L, registry.rules().get(0).getMaxSizeBytes());
    }

    @Test
    @DisplayName("快照与输入列表、输入规则对象解耦")
    void shouldSnapshotIndependentlyOfInputs() {
        List<FileProperties.Rule> source = new ArrayList<>();
        FileProperties.Rule mutable = FileProperties.Rule.of("sys:file:upload", 209715200L, 0L);
        source.add(mutable);
        registry.replace(source);

        source.clear();
        mutable.setPermission("sys:file:manage");
        mutable.setMaxSizeBytes(1024L);
        mutable.setQuotaBytes(2048L);

        assertEquals(1, registry.rules().size());
        assertEquals("sys:file:upload", registry.rules().get(0).getPermission());
        assertEquals(209715200L, registry.rules().get(0).getMaxSizeBytes());
        assertEquals(0L, registry.rules().get(0).getQuotaBytes());
    }

    @Test
    @DisplayName("快照不可变：外部无法追加或删除规则")
    void shouldExposeImmutableSnapshot() {
        List<FileProperties.Rule> rules = registry.rules();

        assertThrows(UnsupportedOperationException.class,
                () -> rules.add(FileProperties.Rule.of("sys:file:upload", 1024L, null)));
        assertThrows(UnsupportedOperationException.class, () -> rules.remove(0));
    }

    @Test
    @DisplayName("空规则列表被拒绝且保留旧快照")
    void shouldRejectEmptyRules() {
        List<FileProperties.Rule> before = registry.rules();

        assertEquals(PARAM_ERROR_CODE, assertThrows(BizException.class,
                () -> registry.replace(List.of())).getCode());
        assertEquals(PARAM_ERROR_CODE, assertThrows(BizException.class,
                () -> registry.replace(null)).getCode());
        assertEquals(before, registry.rules());
    }

    @Test
    @DisplayName("空条目、空白权限码、非正上限与负配额被拒绝且保留旧快照")
    void shouldRejectInvalidRules() {
        List<FileProperties.Rule> before = registry.rules();
        List<List<FileProperties.Rule>> invalidInputs = List.of(
                Collections.singletonList(null),
                List.of(FileProperties.Rule.of(" ", 1024L, null)),
                List.of(FileProperties.Rule.of("sys:file:upload", 0L, null)),
                List.of(FileProperties.Rule.of("sys:file:upload", 1024L, -1L)));

        for (List<FileProperties.Rule> invalid : invalidInputs) {
            BizException ex = assertThrows(BizException.class, () -> registry.replace(invalid));
            assertEquals(PARAM_ERROR_CODE, ex.getCode());
            assertTrue(ex.getMessage().startsWith("上传策略"));
        }
        assertEquals(before, registry.rules());
    }
}
