package com.bluewhale.file.service.support;

import com.bluewhale.file.config.FileProperties;
import com.bluewhale.file.service.model.UploadLimits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FileUploadPolicy} G1 按权限码解析上传上限与配额的单元测试。
 *
 * @author BlueWhale
 */
class FileUploadPolicyTest {

    /** 默认单文件上限：50MB */
    private static final long DEFAULT_MAX_SIZE = 52428800L;

    /** 默认个人配额：1GB */
    private static final long DEFAULT_QUOTA = 1073741824L;

    /** 视频单文件上限：2GB */
    private static final long VIDEO_MAX_SIZE = 2147483648L;

    /** 视频个人配额：8GB */
    private static final long VIDEO_QUOTA = 8589934592L;

    /** 电影单文件上限：4GB */
    private static final long MOVIE_MAX_SIZE = 4294967296L;

    private FileProperties fileProperties;

    private FileUploadPolicy policy;

    /**
     * 每个用例使用全新的默认配置（视频 2GB、电影 4GB、音乐 100MB）
     */
    @BeforeEach
    void setUp() {
        fileProperties = new FileProperties();
        policy = new FileUploadPolicy(fileProperties);
    }

    @Test
    @DisplayName("未命中任何规则：回落全局默认 50MB / 1GB")
    void shouldFallbackToDefaultsWhenNoRuleMatched() {
        UploadLimits limits = policy.resolve(Set.of("sys:file:upload"));

        assertEquals(DEFAULT_MAX_SIZE, limits.maxSizeBytes());
        assertEquals(DEFAULT_QUOTA, limits.quotaBytes());
    }

    @Test
    @DisplayName("权限码为空或 null：回落全局默认")
    void shouldFallbackWhenCodesMissing() {
        assertEquals(policy.defaults(), policy.resolve(null));
        assertEquals(policy.defaults(), policy.resolve(Set.of()));
    }

    @Test
    @DisplayName("sys:video:upload：2GB 单文件上限 / 8GB 配额")
    void shouldApplyVideoRule() {
        UploadLimits limits = policy.resolve(Set.of("sys:video:upload"));

        assertEquals(VIDEO_MAX_SIZE, limits.maxSizeBytes());
        assertEquals(VIDEO_QUOTA, limits.quotaBytes());
    }

    @Test
    @DisplayName("同时持有视频与电影权限：取单文件上限最大的电影规则（4GB / 不限）")
    void shouldPickLargestMatchedRule() {
        UploadLimits limits = policy.resolve(Set.of("sys:video:upload", "sys:video:movie:manage"));

        assertEquals(MOVIE_MAX_SIZE, limits.maxSizeBytes());
        assertEquals(0L, limits.quotaBytes());
        assertTrue(limits.quotaUnlimited());
    }

    @Test
    @DisplayName("super_admin：视为命中全部规则，取最大上限")
    void shouldGrantMaxLimitsForSuperAdmin() {
        UploadLimits limits = policy.resolve(Set.of("super_admin"));

        assertEquals(MOVIE_MAX_SIZE, limits.maxSizeBytes());
        assertEquals(0L, limits.quotaBytes());
    }

    @Test
    @DisplayName("规则未配置配额：沿用全局默认配额")
    void shouldInheritDefaultQuotaWhenRuleOmitsIt() {
        fileProperties.getUploadPolicy().getRules().clear();
        fileProperties.getUploadPolicy().getRules().add(
                FileProperties.Rule.of("sys:file:upload", 104857600L, null));

        UploadLimits limits = policy.resolve(Set.of("sys:file:upload"));

        assertEquals(104857600L, limits.maxSizeBytes());
        assertEquals(DEFAULT_QUOTA, limits.quotaBytes());
    }

    @Test
    @DisplayName("策略开关关闭：任何权限码都回落全局默认")
    void shouldIgnoreRulesWhenPolicyDisabled() {
        fileProperties.getUploadPolicy().setEnabled(false);

        UploadLimits limits = policy.resolve(Set.of("sys:video:upload"));

        assertEquals(DEFAULT_MAX_SIZE, limits.maxSizeBytes());
        assertEquals(DEFAULT_QUOTA, limits.quotaBytes());
    }

    @Test
    @DisplayName("非法规则（权限码为空或上限非正）被忽略，不放大上限")
    void shouldIgnoreInvalidRules() {
        List<FileProperties.Rule> rules = new ArrayList<>();
        rules.add(FileProperties.Rule.of(" ", 4294967296L, 0L));
        rules.add(FileProperties.Rule.of("sys:video:upload", 0L, 0L));
        fileProperties.getUploadPolicy().setRules(rules);

        UploadLimits limits = policy.resolve(Set.of("sys:video:upload"));

        assertEquals(DEFAULT_MAX_SIZE, limits.maxSizeBytes());
        assertEquals(DEFAULT_QUOTA, limits.quotaBytes());
    }
}
