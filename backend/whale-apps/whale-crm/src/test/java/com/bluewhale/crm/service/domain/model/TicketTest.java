package com.bluewhale.crm.service.domain.model;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.crm.common.CrmErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link Ticket} 状态机单元测试。
 *
 * @author BlueWhale
 */
class TicketTest {

    /** 固定操作时间，避免依赖当前时间 */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 6, 9, 0);

    @Test
    @DisplayName("分派：新工单分派后进入已分派，处理人必填")
    void shouldAssignNewTicket() {
        Ticket ticket = ticket(TicketStatus.NEW);

        ticket.assign(1001L, " 张三 ", NOW);
        assertEquals(TicketStatus.ASSIGNED, ticket.getStatus());
        assertEquals("张三", ticket.getAssigneeName());

        BizException exception = assertThrows(BizException.class, () -> ticket.assign(null, null, NOW));
        assertEquals(CrmErrorCode.TICKET_ASSIGNEE_INVALID, exception.getCode());
    }

    @Test
    @DisplayName("分派：已解决工单不允许分派")
    void shouldRejectAssignResolvedTicket() {
        Ticket ticket = ticket(TicketStatus.RESOLVED);
        ticket.setResolvedAt(NOW);

        BizException exception = assertThrows(BizException.class, () -> ticket.assign(1001L, "张三", NOW));

        assertEquals(CrmErrorCode.TICKET_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("处理：内容必填，首次处理写入首次响应时间")
    void shouldProcessAndMarkFirstResponse() {
        Ticket ticket = ticket(TicketStatus.ASSIGNED);

        ticket.process(" 已联系客户排查 ", NOW);

        assertEquals(TicketStatus.PROCESSING, ticket.getStatus());
        assertEquals(NOW, ticket.getFirstRespondedAt());

        BizException blank = assertThrows(BizException.class, () -> ticket.process(" ", NOW));
        assertEquals(CrmErrorCode.PARAM_INVALID, blank.getCode());
    }

    @Test
    @DisplayName("处理：等待客户期间必须先恢复，已关闭工单不允许处理")
    void shouldRejectProcessInInvalidStatus() {
        Ticket waiting = ticket(TicketStatus.WAITING_CUSTOMER);
        BizException waitingError = assertThrows(BizException.class, () -> waiting.process("继续处理", NOW));
        assertEquals(CrmErrorCode.TICKET_STATE_CONFLICT, waitingError.getCode());

        Ticket closed = ticket(TicketStatus.CLOSED);
        BizException closedError = assertThrows(BizException.class, () -> closed.process("继续处理", NOW));
        assertEquals(CrmErrorCode.TICKET_STATE_CONFLICT, closedError.getCode());
    }

    @Test
    @DisplayName("备注：不改变状态，但记录首次响应")
    void shouldCommentWithoutChangingStatus() {
        Ticket ticket = ticket(TicketStatus.NEW);

        ticket.comment("客户补充了截图", NOW);

        assertEquals(TicketStatus.NEW, ticket.getStatus());
        assertEquals(NOW, ticket.getFirstRespondedAt());
    }

    @Test
    @DisplayName("暂停与恢复：暂停进入等待客户，恢复累计暂停秒数并回到处理中")
    void shouldPauseAndResume() {
        Ticket ticket = ticket(TicketStatus.PROCESSING);
        LocalDateTime pauseAt = LocalDateTime.of(2026, 11, 6, 10, 0);

        ticket.pause("等待客户提供日志", pauseAt);
        assertEquals(TicketStatus.WAITING_CUSTOMER, ticket.getStatus());
        assertEquals(pauseAt, ticket.getPausedAt());

        long paused = ticket.resume(pauseAt.plusMinutes(30));
        assertEquals(1800L, paused);
        assertEquals(1800L, ticket.getPausedSeconds());
        assertEquals(TicketStatus.PROCESSING, ticket.getStatus());
        assertNull(ticket.getPausedAt());

        BizException again = assertThrows(BizException.class, () -> ticket.resume(NOW));
        assertEquals(CrmErrorCode.TICKET_STATE_CONFLICT, again.getCode());
    }

    @Test
    @DisplayName("恢复：非等待客户状态拒绝")
    void shouldRejectResumeInInvalidStatus() {
        Ticket ticket = ticket(TicketStatus.PROCESSING);

        BizException exception = assertThrows(BizException.class, () -> ticket.resume(NOW));

        assertEquals(CrmErrorCode.TICKET_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("暂停：原因必填，已解决工单不允许暂停")
    void shouldRejectPauseInInvalidCase() {
        Ticket ticket = ticket(TicketStatus.PROCESSING);
        BizException blank = assertThrows(BizException.class, () -> ticket.pause(" ", NOW));
        assertEquals(CrmErrorCode.PARAM_INVALID, blank.getCode());

        Ticket resolved = ticket(TicketStatus.RESOLVED);
        BizException resolvedError = assertThrows(BizException.class, () -> resolved.pause("等待客户", NOW));
        assertEquals(CrmErrorCode.TICKET_STATE_CONFLICT, resolvedError.getCode());
    }

    @Test
    @DisplayName("升级：P3 → P2 → P1，P1 保持最高")
    void shouldEscalateToP1Only() {
        Ticket ticket = ticket(TicketStatus.PROCESSING);
        ticket.setPriority(TicketPriority.P3);

        assertEquals(TicketPriority.P2, ticket.escalate());
        assertEquals(TicketPriority.P1, ticket.escalate());
        assertEquals(TicketPriority.P1, ticket.escalate());
    }

    @Test
    @DisplayName("解决：必须填写解决方案，重复解决拒绝")
    void shouldResolveOnce() {
        Ticket ticket = ticket(TicketStatus.PROCESSING);

        ticket.resolve("更换主板并升级固件", NOW);

        assertEquals(TicketStatus.RESOLVED, ticket.getStatus());
        assertEquals("更换主板并升级固件", ticket.getSolution());
        assertEquals(NOW, ticket.getResolvedAt());
        BizException exception = assertThrows(BizException.class, () -> ticket.resolve("再次解决", NOW));
        assertEquals(CrmErrorCode.TICKET_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("关闭：仅已解决工单允许关闭，且必须填写解决方案")
    void shouldCloseOnlyResolved() {
        Ticket processing = ticket(TicketStatus.PROCESSING);
        BizException conflict = assertThrows(BizException.class, () -> processing.close("关闭", NOW));
        assertEquals(CrmErrorCode.TICKET_STATE_CONFLICT, conflict.getCode());

        Ticket resolved = ticket(TicketStatus.RESOLVED);
        BizException blank = assertThrows(BizException.class, () -> resolved.close(" ", NOW));
        assertEquals(CrmErrorCode.PARAM_INVALID, blank.getCode());

        resolved.close("已更换主板", NOW);
        assertEquals(TicketStatus.CLOSED, resolved.getStatus());
        assertEquals(NOW, resolved.getClosedAt());
    }

    @Test
    @DisplayName("重开：仅已解决或已关闭允许，重开次数累加并清空解决 / 关闭时间")
    void shouldReopenResolvedAndClosed() {
        Ticket ticket = ticket(TicketStatus.CLOSED);
        ticket.setResolvedAt(NOW);
        ticket.setClosedAt(NOW.plusHours(1));

        ticket.reopen("问题复现", NOW.plusHours(2));

        assertEquals(TicketStatus.REOPENED, ticket.getStatus());
        assertEquals(1, ticket.getReopenCount());
        assertNull(ticket.getResolvedAt());
        assertNull(ticket.getClosedAt());

        BizException exception = assertThrows(BizException.class, () -> ticket.reopen("重复重开", NOW));
        assertEquals(CrmErrorCode.TICKET_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("取消：已关闭不允许取消，取消原因必填")
    void shouldCancelOpenTicket() {
        Ticket closed = ticket(TicketStatus.CLOSED);
        BizException conflict = assertThrows(BizException.class, () -> closed.cancel("取消", NOW));
        assertEquals(CrmErrorCode.TICKET_STATE_CONFLICT, conflict.getCode());

        Ticket newTicket = ticket(TicketStatus.NEW);
        BizException blank = assertThrows(BizException.class, () -> newTicket.cancel(" ", NOW));
        assertEquals(CrmErrorCode.PARAM_INVALID, blank.getCode());

        newTicket.cancel("客户撤回", NOW);
        assertEquals(TicketStatus.CANCELLED, newTicket.getStatus());
        assertEquals(NOW, newTicket.getClosedAt());
    }

    @Test
    @DisplayName("顺延：暂停恢复后同时顺延响应与解决截止时间")
    void shouldShiftDeadlines() {
        Ticket ticket = ticket(TicketStatus.PROCESSING);
        ticket.applyDeadlines(NOW, NOW.plusHours(8));

        ticket.shiftDeadlines(1800L);

        assertEquals(NOW.plusMinutes(30), ticket.getResponseDeadline());
        assertEquals(NOW.plusHours(8).plusMinutes(30), ticket.getResolveDeadline());
    }

    /**
     * 构造指定状态的工单。
     *
     * @param status 状态
     * @return 工单
     */
    private static Ticket ticket(TicketStatus status) {
        Ticket ticket = new Ticket();
        ticket.setId(1L);
        ticket.setTicketNo("TICKET-20261106-0001");
        ticket.setCustomerId(10L);
        ticket.setPriority(TicketPriority.P2);
        ticket.setTitle("设备离线");
        ticket.setStatus(status);
        ticket.setPausedSeconds(0L);
        ticket.setReopenCount(0);
        ticket.setLockVersion(0);
        return ticket;
    }
}
