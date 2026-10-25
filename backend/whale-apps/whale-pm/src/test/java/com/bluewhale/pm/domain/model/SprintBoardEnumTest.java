package com.bluewhale.pm.domain.model;

import com.bluewhale.common.core.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 迭代状态与看板类型枚举单元测试。
 *
 * @author BlueWhale
 */
class SprintBoardEnumTest {

    @Test
    @DisplayName("SprintStatus：大小写不敏感解析")
    void shouldParseSprintStatus() {
        assertEquals(SprintStatus.ACTIVE, SprintStatus.from(" active "));
    }

    @Test
    @DisplayName("SprintStatus：非法值拒绝")
    void shouldRejectIllegalSprintStatus() {
        assertThrows(BizException.class, () -> SprintStatus.from("RUNNING"));
        assertThrows(BizException.class, () -> SprintStatus.from(null));
    }

    @Test
    @DisplayName("BoardType：大小写不敏感解析")
    void shouldParseBoardType() {
        assertEquals(BoardType.KANBAN, BoardType.from("kanban"));
    }

    @Test
    @DisplayName("BoardType：非法值拒绝")
    void shouldRejectIllegalBoardType() {
        assertThrows(BizException.class, () -> BoardType.from("GANTT"));
        assertThrows(BizException.class, () -> BoardType.from(" "));
    }
}
