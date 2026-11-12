package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.common.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link BrandConfigAggregate} 核心不变量单元测试（权限中心扩展 M0）。
 *
 * @author BlueWhale
 */
class BrandConfigAggregateTest {

    @Test
    @DisplayName("创建平台默认品牌：启用、优先级生效、作用域 ID 固定 0")
    void shouldCreatePlatformBrand() {
        BrandConfigAggregate brand = BrandConfigAggregate.create(BrandConfigAggregate.TARGET_PLATFORM, 0L, "",
                "BlueWhale", "https://cdn.example.com/logo.png", "蓝鲸平台", "BlueWhale",
                "© BlueWhale", "#1677ff", "https://cdn.example.com/bg.png", 100);

        assertThat(brand.isEnabled()).isTrue();
        assertThat(brand.getPriority()).isEqualTo(100);
        assertThat(brand.getDomain()).isEmpty();
        assertThat(brand.getStatus()).isEqualTo(BrandConfigAggregate.STATUS_ENABLED);
    }

    @Test
    @DisplayName("作用域类型非法时拒绝创建")
    void shouldRejectUnknownTargetType() {
        assertThrows(DomainException.class, () -> BrandConfigAggregate.create("GLOBAL", 0L, "",
                "BlueWhale", null, null, null, null, null, null, 0));
    }

    @Test
    @DisplayName("平台级品牌的作用域 ID 非 0 时拒绝创建")
    void shouldRejectPlatformBrandWithNonZeroTargetId() {
        assertThrows(DomainException.class, () -> BrandConfigAggregate.create(
                BrandConfigAggregate.TARGET_PLATFORM, 1L, "", "BlueWhale",
                null, null, null, null, null, null, 0));
    }

    @Test
    @DisplayName("主题色非 #RGB/#RRGGBB 时拒绝创建")
    void shouldRejectInvalidPrimaryColor() {
        assertThrows(DomainException.class, () -> BrandConfigAggregate.create(
                BrandConfigAggregate.TARGET_TENANT, 1L, "", "BlueWhale",
                null, null, null, null, "blue", null, 0));
    }

    @Test
    @DisplayName("停用后不再参与登录页解析")
    void shouldNotBeEnabledAfterDisable() {
        BrandConfigAggregate brand = BrandConfigAggregate.create(BrandConfigAggregate.TARGET_TENANT, 1L,
                "auth.example.com", "BlueWhale", null, null, null, null, null, null, 10);

        brand.disable();

        assertThat(brand.isEnabled()).isFalse();
        assertThat(brand.getStatus()).isEqualTo(BrandConfigAggregate.STATUS_DISABLED);
    }

    @Test
    @DisplayName("编辑主题时校验品牌名称不能为空")
    void shouldRejectBlankBrandNameOnUpdate() {
        BrandConfigAggregate brand = BrandConfigAggregate.create(BrandConfigAggregate.TARGET_TENANT, 1L,
                "", "BlueWhale", null, null, null, null, null, null, 0);

        assertThrows(DomainException.class, () -> brand.updateTheme(" ",
                null, null, null, null, null, null, 0));
    }
}
