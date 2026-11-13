package com.bluewhale.auth.domain.model.vo;

import com.bluewhale.common.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 三方登录值对象单元测试（权限中心扩展 M2）。
 *
 * @author BlueWhale
 */
class SocialValueObjectsTest {

    @Test
    @DisplayName("SocialProvider.of：按编码解析（大小写不敏感）")
    void socialProviderParsesByCode() {
        assertThat(SocialProvider.of("dingtalk")).isEqualTo(SocialProvider.DINGTALK);
        assertThat(SocialProvider.of("FEISHU")).isEqualTo(SocialProvider.FEISHU);
        assertThat(SocialProvider.of("wecom")).isEqualTo(SocialProvider.WECOM);
    }

    @Test
    @DisplayName("SocialProvider.of：空值与未知编码拒绝")
    void socialProviderRejectsInvalid() {
        assertThatThrownBy(() -> SocialProvider.of(null)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> SocialProvider.of(" ")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> SocialProvider.of("google")).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("SocialProvider：编码与显示名一致")
    void socialProviderCodesAreStable() {
        assertThat(SocialProvider.DINGTALK.code()).isEqualTo("dingtalk");
        assertThat(SocialProvider.FEISHU.code()).isEqualTo("feishu");
        assertThat(SocialProvider.WECOM.code()).isEqualTo("wecom");
        assertThat(SocialProvider.DINGTALK.displayName()).isEqualTo("钉钉");
    }

    @Test
    @DisplayName("ProviderUserId：裁剪空白、非空、长度受限")
    void providerUserIdValidates() {
        assertThat(ProviderUserId.of("  abc  ").value()).isEqualTo("abc");
        assertThatThrownBy(() -> ProviderUserId.of(" ")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> ProviderUserId.of("x".repeat(129))).isInstanceOf(DomainException.class);
    }

    @Test
    @DisplayName("SocialProfile：文本字段裁剪，空白归一为 null；渠道与标识必填")
    void socialProfileTrimsAndNormalizes() {
        SocialProfile profile = SocialProfile.of(SocialProvider.FEISHU, "ou-1", " u ",
                "  ", " https://a/1.png ", " user@ex.com ");

        assertThat(profile.providerUserId().value()).isEqualTo("ou-1");
        assertThat(profile.unionId()).isEqualTo("u");
        assertThat(profile.nickname()).isNull();
        assertThat(profile.avatar()).isEqualTo("https://a/1.png");
        assertThat(profile.email()).isEqualTo("user@ex.com");

        assertThatThrownBy(() -> SocialProfile.of(SocialProvider.FEISHU, " ", null, null, null, null))
                .isInstanceOf(DomainException.class);
    }
}
