package com.bluewhale.pm.domain.model;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link ProjectRole} 与 {@link StatusCategory} 枚举解析单元测试。
 *
 * @author BlueWhale
 */
class PmEnumTest {

    @Test
    @DisplayName("ProjectRole：大小写不敏感解析")
    void shouldParseProjectRoleIgnoreCase() {
        assertEquals(ProjectRole.ADMIN, ProjectRole.from(" admin "));
        assertEquals(ProjectRole.VIEWER, ProjectRole.from("viewer"));
    }

    @Test
    @DisplayName("ProjectRole：非法值拒绝")
    void shouldRejectIllegalProjectRole() {
        assertThrows(BizException.class, () -> ProjectRole.from("OWNER"));
        assertThrows(BizException.class, () -> ProjectRole.from(null));
    }

    @Test
    @DisplayName("StatusCategory：大小写不敏感解析")
    void shouldParseStatusCategoryIgnoreCase() {
        assertEquals(StatusCategory.IN_PROGRESS, StatusCategory.from("in_progress"));
    }

    @Test
    @DisplayName("StatusCategory：非法值拒绝")
    void shouldRejectIllegalStatusCategory() {
        assertThrows(BizException.class, () -> StatusCategory.from("BLOCKED"));
    }
}
