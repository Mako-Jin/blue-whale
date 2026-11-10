package com.bluewhale.doc.support.collab;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DocPresencePalette} 稳定配色断言。
 *
 * @author BlueWhale
 */
class DocPresencePaletteTest {

    @Test
    void colorShouldBeStableForSameUser() {
        assertThat(DocPresencePalette.color(1L)).isEqualTo(DocPresencePalette.color(1L));
    }

    @Test
    void colorShouldTreatNullUserAsZero() {
        assertThat(DocPresencePalette.color(null)).isEqualTo("#2563eb");
        assertThat(DocPresencePalette.color(0L)).isEqualTo("#2563eb");
    }

    @Test
    void colorShouldWrapAroundPalette() {
        assertThat(DocPresencePalette.color(1L)).isEqualTo("#7c3aed");
        assertThat(DocPresencePalette.color(8L)).isEqualTo("#2563eb");
        assertThat(DocPresencePalette.color(9L)).isEqualTo("#7c3aed");
    }

    @Test
    void colorShouldHandleNegativeUsers() {
        assertThat(DocPresencePalette.color(-1L)).isEqualTo("#dc2626");
        assertThat(DocPresencePalette.color(-8L)).isEqualTo("#2563eb");
    }
}
