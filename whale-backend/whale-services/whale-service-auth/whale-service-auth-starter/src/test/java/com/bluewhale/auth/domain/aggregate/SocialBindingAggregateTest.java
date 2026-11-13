package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.auth.domain.model.vo.SocialProvider;
import com.bluewhale.common.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SocialBindingAggregate} 单元测试（权限中心扩展 M2）。
 *
 * <p>覆盖不变量：归属校验、唯一标识非空、状态域、解绑幂等、资料刷新、用户归属判断。</p>
 *
 * @author BlueWhale
 */
class SocialBindingAggregateTest {

    @Test
    @DisplayName("create：默认绑定生效，快照字段可空")
    void createDefaultsToBound() {
        SocialBindingAggregate binding = SocialBindingAggregate.create(
                1L, 2L, SocialProvider.DINGTALK, "openid-123", "union-456", "张三", "https://a/x.png");

        assertThat(binding.getId()).isNull();
        assertThat(binding.getTenantId()).isEqualTo(1L);
        assertThat(binding.getUserId()).isEqualTo(2L);
        assertThat(binding.getProvider()).isEqualTo(SocialProvider.DINGTALK);
        assertThat(binding.getProviderUserId().value()).isEqualTo("openid-123");
        assertThat(binding.getUnionId()).isEqualTo("union-456");
        assertThat(binding.isBound()).isTrue();
        assertThat(binding.getStatus()).isEqualTo(SocialBindingAggregate.STATUS_BOUND);
        assertThat(binding.getCreateTime()).isNotNull();
    }

    @Test
    @DisplayName("create：缺失租户/用户/三方标识拒绝")
    void createRejectsMissingInvariants() {
        assertThatThrownBy(() -> SocialBindingAggregate.create(
                null, 2L, SocialProvider.DINGTALK, "openid", null, null, null))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> SocialBindingAggregate.create(
                1L, null, SocialProvider.DINGTALK, "openid", null, null, null))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> SocialBindingAggregate.create(
                1L, 2L, SocialProvider.DINGTALK, " ", null, null, null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("unbind：解绑后状态翻转、幂等可重复调用")
    void unbindIsIdempotent() {
        SocialBindingAggregate binding = SocialBindingAggregate.create(
                1L, 2L, SocialProvider.FEISHU, "ou-1", null, null, null);

        binding.unbind();
        assertThat(binding.isBound()).isFalse();
        assertThat(binding.getStatus()).isEqualTo(SocialBindingAggregate.STATUS_UNBOUND);

        binding.unbind();
        assertThat(binding.getStatus()).isEqualTo(SocialBindingAggregate.STATUS_UNBOUND);
    }

    @Test
    @DisplayName("refreshProfile：非空字段覆盖，空值保留旧快照")
    void refreshProfileOverwritesNonBlankOnly() {
        SocialBindingAggregate binding = SocialBindingAggregate.create(
                1L, 2L, SocialProvider.WECOM, "u-1", "union-old", "旧名", "https://a/old.png");

        binding.refreshProfile(null, "新名", " ");
        assertThat(binding.getUnionId()).isEqualTo("union-old");
        assertThat(binding.getNickname()).isEqualTo("新名");
        assertThat(binding.getAvatar()).isEqualTo("https://a/old.png");

        binding.refreshProfile("union-new", null, "https://a/new.png");
        assertThat(binding.getUnionId()).isEqualTo("union-new");
        assertThat(binding.getAvatar()).isEqualTo("https://a/new.png");
    }

    @Test
    @DisplayName("belongsTo：仅归属用户自身返回 true")
    void belongsToMatchesOnlyOwner() {
        SocialBindingAggregate binding = SocialBindingAggregate.create(
                1L, 2L, SocialProvider.DINGTALK, "u-1", null, null, null);

        assertThat(binding.belongsTo(2L)).isTrue();
        assertThat(binding.belongsTo(3L)).isFalse();
        assertThat(binding.belongsTo(null)).isFalse();
    }

    @Test
    @DisplayName("reconstitute：状态缺失时按已解绑兜底，非法渠道拒绝")
    void reconstituteDefaultsAndValidates() {
        SocialBindingAggregate binding = SocialBindingAggregate.reconstitute(
                9L, 1L, 2L, "dingtalk", "u-1", null, null, null, null,
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        assertThat(binding.getId()).isEqualTo(9L);
        assertThat(binding.isBound()).isFalse();

        assertThatThrownBy(() -> SocialBindingAggregate.reconstitute(
                9L, 1L, 2L, "unknown", "u-1", null, null, null, 1,
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now()))
                .isInstanceOf(DomainException.class);
    }
}
