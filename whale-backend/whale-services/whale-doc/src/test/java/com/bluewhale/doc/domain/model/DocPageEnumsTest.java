package com.bluewhale.doc.domain.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 页面状态/类型/可见性枚举边界断言。
 *
 * @author BlueWhale
 */
class DocPageEnumsTest {

    @Test
    void pageStatusShouldExposeAllDeclaredStates() {
        assertThat(DocPageStatus.values()).containsExactly(
                DocPageStatus.DRAFT, DocPageStatus.PUBLISHED,
                DocPageStatus.ARCHIVED, DocPageStatus.PENDING_APPROVAL);
        assertThat(DocPageStatus.valueOf("PUBLISHED")).isEqualTo(DocPageStatus.PUBLISHED);
    }

    @Test
    void pageTypeShouldExposePageAndFolder() {
        assertThat(DocPageType.values()).containsExactly(DocPageType.PAGE, DocPageType.FOLDER);
        assertThat(DocPageType.valueOf("FOLDER")).isEqualTo(DocPageType.FOLDER);
    }

    @Test
    void visibilityShouldExposePublicAndMember() {
        assertThat(DocVisibility.values()).containsExactly(DocVisibility.PUBLIC, DocVisibility.MEMBER);
        assertThat(DocVisibility.PUBLIC.name()).isEqualTo("PUBLIC");
    }
}
