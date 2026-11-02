package com.bluewhale.doc.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DocAclRole} 层级与解析断言。
 *
 * @author BlueWhale
 */
class DocAclRoleTest {

    @Test
    void editShouldReachRead() {
        assertThat(DocAclRole.EDIT.atLeast(DocAclRole.READ)).isTrue();
        assertThat(DocAclRole.EDIT.atLeast(DocAclRole.EDIT)).isTrue();
        assertThat(DocAclRole.READ.atLeast(DocAclRole.EDIT)).isFalse();
        assertThat(DocAclRole.READ.atLeast(DocAclRole.READ)).isTrue();
    }

    @Test
    void atLeastShouldRejectNullRequirement() {
        assertThat(DocAclRole.EDIT.atLeast(null)).isFalse();
        assertThat(DocAclRole.READ.atLeast(null)).isFalse();
    }

    @Test
    void parseShouldReturnMatchingRole() {
        assertThat(DocAclRole.parse("READ")).isEqualTo(DocAclRole.READ);
        assertThat(DocAclRole.parse("EDIT")).isEqualTo(DocAclRole.EDIT);
    }

    @Test
    void parseShouldReturnNullForNullOrUnknown() {
        assertThat(DocAclRole.parse(null)).isNull();
        assertThat(DocAclRole.parse("OWNER")).isNull();
        assertThat(DocAclRole.parse("read")).isNull();
    }
}
