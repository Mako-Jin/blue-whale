package com.bluewhale.doc.domain.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DocMemberRole} 权限层级断言。
 *
 * @author BlueWhale
 */
class DocMemberRoleTest {

    @Test
    void ownerShouldReachEveryRole() {
        assertTrue(DocMemberRole.OWNER.atLeast(DocMemberRole.OWNER));
        assertTrue(DocMemberRole.OWNER.atLeast(DocMemberRole.ADMIN));
        assertTrue(DocMemberRole.OWNER.atLeast(DocMemberRole.EDITOR));
        assertTrue(DocMemberRole.OWNER.atLeast(DocMemberRole.READER));
    }

    @Test
    void readerShouldNotReachHigherRole() {
        assertFalse(DocMemberRole.READER.atLeast(DocMemberRole.EDITOR));
        assertFalse(DocMemberRole.READER.atLeast(DocMemberRole.ADMIN));
        assertFalse(DocMemberRole.READER.atLeast(DocMemberRole.OWNER));
    }

    @Test
    void editorShouldReachEditorAndReaderButNotAdmin() {
        assertTrue(DocMemberRole.EDITOR.atLeast(DocMemberRole.EDITOR));
        assertTrue(DocMemberRole.EDITOR.atLeast(DocMemberRole.READER));
        assertFalse(DocMemberRole.EDITOR.atLeast(DocMemberRole.ADMIN));
    }

    @Test
    void nullRequirementShouldBeDenied() {
        assertFalse(DocMemberRole.OWNER.atLeast(null));
    }
}
