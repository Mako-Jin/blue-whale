package com.bluewhale.doc.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DocSlugs} 规范化与回收站墓碑 slug 断言。
 *
 * @author BlueWhale
 */
class DocSlugsTest {

    @Test
    void normalizeShouldFallbackWhenBlank() {
        assertThat(DocSlugs.normalize(null, 7L)).isEqualTo("p-7");
        assertThat(DocSlugs.normalize("   ", 7L)).isEqualTo("p-7");
    }

    @Test
    void normalizeShouldTrimLowercaseAndKeepAllowedCharacters() {
        assertThat(DocSlugs.normalize("  My_Page-01  ", 7L)).isEqualTo("my_page-01");
    }

    @Test
    void normalizeShouldFallbackForIllegalCharacters() {
        assertThat(DocSlugs.normalize("hello world", 7L)).isEqualTo("p-7");
        assertThat(DocSlugs.normalize("中文", 7L)).isEqualTo("p-7");
        assertThat(DocSlugs.normalize("a/b", 7L)).isEqualTo("p-7");
    }

    @Test
    void normalizeShouldTruncateOverlongSlug() {
        String longSlug = "a".repeat(200);

        assertThat(DocSlugs.normalize(longSlug, 7L)).hasSize(128);
        assertThat(DocSlugs.normalize("b".repeat(128), 7L)).hasSize(128);
    }

    @Test
    void trashSlugShouldAppendPageSuffix() {
        assertThat(DocSlugs.trashSlug("page", 5L)).isEqualTo("page-trash-5");
        assertThat(DocSlugs.trashSlug(null, 5L)).isEqualTo("-trash-5");
        assertThat(DocSlugs.trashSlug("", 5L)).isEqualTo("-trash-5");
    }

    @Test
    void trashSlugShouldTruncateBaseToKeepWholeSuffix() {
        String slug = DocSlugs.trashSlug("a".repeat(200), 5L);

        assertThat(slug).endsWith("-trash-5").hasSize(128);
    }

    @Test
    void restoreBaseShouldFallbackWhenBlank() {
        assertThat(DocSlugs.restoreBase(null, 5L)).isEqualTo("p-5");
        assertThat(DocSlugs.restoreBase("  ", 5L)).isEqualTo("p-5");
    }

    @Test
    void restoreBaseShouldStripMatchingSuffix() {
        assertThat(DocSlugs.restoreBase("page-trash-5", 5L)).isEqualTo("page");
        assertThat(DocSlugs.restoreBase("my-slug-trash-5", 5L)).isEqualTo("my-slug");
    }

    @Test
    void restoreBaseShouldKeepSlugWithoutMatchingSuffix() {
        assertThat(DocSlugs.restoreBase("page", 5L)).isEqualTo("page");
        assertThat(DocSlugs.restoreBase("page-trash-6", 5L)).isEqualTo("page-trash-6");
    }

    @Test
    void restoreBaseShouldFallbackWhenBasePartBlank() {
        assertThat(DocSlugs.restoreBase("   -trash-5", 5L)).isEqualTo("p-5");
    }

    @Test
    void restoreBaseShouldHandlePureSuffix() {
        assertThat(DocSlugs.restoreBase("-trash-5", 5L)).isEqualTo("-trash-5");
    }
}
