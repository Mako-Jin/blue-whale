package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.auth.domain.model.entity.UserCredential;
import com.bluewhale.auth.domain.model.entity.UserProfileContact;
import com.bluewhale.common.domain.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link UserProfileAggregate} 核心不变量单元测试（权限中心扩展 M3）。
 *
 * @author BlueWhale
 */
class UserProfileAggregateTest {

    private static final String CIPHER_TEXT = "AESGCM-BASE64-CIPHER";

    @Test
    @DisplayName("新建档案：归属租户与用户，子集合为空")
    void shouldCreateEmptyProfile() {
        UserProfileAggregate profile = UserProfileAggregate.create(1L, 100L);

        assertThat(profile.getId()).isNull();
        assertThat(profile.getTenantId()).isEqualTo(1L);
        assertThat(profile.getUserId()).isEqualTo(100L);
        assertThat(profile.getContacts()).isEmpty();
        assertThat(profile.getCredentials()).isEmpty();
    }

    @Test
    @DisplayName("缺少租户或用户归属时拒绝创建")
    void shouldRejectWithoutTenantOrUser() {
        assertThrows(DomainException.class, () -> UserProfileAggregate.create(null, 100L));
        assertThrows(DomainException.class, () -> UserProfileAggregate.create(1L, null));
    }

    @Test
    @DisplayName("整体替换联系方式：同类型主联系方式重复时拒绝")
    void shouldRejectDuplicatePrimaryContact() {
        UserProfileAggregate profile = UserProfileAggregate.create(1L, 100L);
        List<UserProfileContact> contacts = List.of(
                UserProfileContact.create("MOBILE", "13800000001", true, false, 0),
                UserProfileContact.create("MOBILE", "13800000002", true, false, 1));

        assertThrows(DomainException.class, () -> profile.replaceContacts(contacts));
    }

    @Test
    @DisplayName("整体替换联系方式：不同类型的主联系方式允许并存")
    void shouldAllowPrimaryContactsOfDifferentTypes() {
        UserProfileAggregate profile = UserProfileAggregate.create(1L, 100L);
        List<UserProfileContact> contacts = List.of(
                UserProfileContact.create("MOBILE", "13800000001", true, false, 0),
                UserProfileContact.create("EMAIL", "a@b.com", true, false, 1));

        assertThat(profile.replaceContacts(contacts)).hasSize(2);
    }

    @Test
    @DisplayName("证件号密文为空时拒绝创建证件实体")
    void shouldRejectBlankCredentialNo() {
        assertThrows(DomainException.class,
                () -> UserCredential.create("ID_CARD", " ", "张三", true, null, null));
    }

    @Test
    @DisplayName("整体替换证件：同类型主证件重复时拒绝")
    void shouldRejectDuplicatePrimaryCredential() {
        UserProfileAggregate profile = UserProfileAggregate.create(1L, 100L);
        List<UserCredential> credentials = List.of(
                UserCredential.create("ID_CARD", CIPHER_TEXT, "张三", true, null, null),
                UserCredential.create("ID_CARD", CIPHER_TEXT, "张三", true, null, null));

        assertThrows(DomainException.class, () -> profile.replaceCredentials(credentials));
    }

    @Test
    @DisplayName("子集合超过单类数量上限时拒绝")
    void shouldRejectChildrenBeyondLimit() {
        UserProfileAggregate profile = UserProfileAggregate.create(1L, 100L);
        List<UserProfileContact> oversized = new ArrayList<>();
        for (int i = 0; i <= UserProfileAggregate.MAX_CHILDREN_PER_TYPE; i++) {
            oversized.add(UserProfileContact.create("OTHER", "v-" + i, false, false, i));
        }

        assertThrows(DomainException.class, () -> profile.replaceContacts(oversized));
    }

    @Test
    @DisplayName("子集合传 null 时拒绝")
    void shouldRejectNullChildrenList() {
        UserProfileAggregate profile = UserProfileAggregate.create(1L, 100L);

        assertThrows(DomainException.class, () -> profile.replaceContacts(null));
        assertThrows(DomainException.class, () -> profile.replaceCredentials(null));
    }

    @Test
    @DisplayName("标记联系方式已验证：目标存在时生效，不存在时拒绝")
    void shouldMarkContactVerified() {
        UserProfileAggregate profile = UserProfileAggregate.create(1L, 100L);
        UserProfileContact contact = UserProfileContact.reconstitute(
                9L, "MOBILE", "13800000001", false, false, 0,
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        profile.replaceContacts(List.of(contact));

        profile.markContactVerified(9L);

        assertThat(profile.getContacts().get(0).isVerified()).isTrue();
        assertThrows(DomainException.class, () -> profile.markContactVerified(999L));
    }

    @Test
    @DisplayName("更新基本信息与归属判断")
    void shouldUpdateBasicAndCheckOwnership() {
        UserProfileAggregate profile = UserProfileAggregate.create(1L, 100L);

        profile.updateBasic("M", java.time.LocalDate.of(1990, 1, 1), "杭州", "西湖区", "汉族",
                "群众", "UNMARRIED", "备注");

        assertThat(profile.getGender()).isEqualTo("M");
        assertThat(profile.belongsTo(100L)).isTrue();
        assertThat(profile.belongsTo(200L)).isFalse();
    }
}
