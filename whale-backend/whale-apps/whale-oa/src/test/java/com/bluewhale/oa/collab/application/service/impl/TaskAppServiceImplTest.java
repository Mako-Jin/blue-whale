package com.bluewhale.oa.collab.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.oa.collab.application.command.CollabCommands;
import com.bluewhale.oa.collab.domain.model.OaTask;
import com.bluewhale.oa.collab.domain.repository.CollabRepository;
import com.bluewhale.oa.common.OaErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 个人任务应用服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class TaskAppServiceImplTest {

    @Mock
    private CollabRepository repository;

    private TaskAppServiceImpl taskAppService;

    @BeforeEach
    void setUp() {
        taskAppService = new TaskAppServiceImpl(repository);
    }

    @Test
    @DisplayName("create：默认优先级与来源")
    void shouldCreateWithDefaultPriorityAndSource() {
        taskAppService.createTask(taskCommand());

        ArgumentCaptor<OaTask> captor = ArgumentCaptor.forClass(OaTask.class);
        verify(repository).saveTask(captor.capture());
        assertEquals("NORMAL", captor.getValue().getPriority());
        assertEquals("PERSONAL", captor.getValue().getSource());
        assertEquals("TODO", captor.getValue().getStatus());
        assertEquals(101L, captor.getValue().getOwnerId());
    }

    @Test
    @DisplayName("status：完成后写入完成时间")
    void shouldSetFinishTimeWhenDone() {
        when(repository.findTask(1L)).thenReturn(task("TODO"));

        boolean changed = taskAppService.changeStatus(1L, "DONE");

        assertEquals(true, changed);
        ArgumentCaptor<OaTask> captor = ArgumentCaptor.forClass(OaTask.class);
        verify(repository).updateTask(captor.capture());
        assertEquals("DONE", captor.getValue().getStatus());
        assertNotNull(captor.getValue().getFinishTime());
    }

    @Test
    @DisplayName("status：终态不可回流")
    void shouldRejectTerminalTransition() {
        when(repository.findTask(1L)).thenReturn(task("DONE"));

        BizException exception = assertThrows(BizException.class,
                () -> taskAppService.changeStatus(1L, "DOING"));

        assertEquals(OaErrorCode.TASK_STATUS_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("update：终态任务不可编辑")
    void shouldRejectEditTerminalTask() {
        when(repository.findTask(1L)).thenReturn(task("CANCELLED"));

        BizException exception = assertThrows(BizException.class,
                () -> taskAppService.updateTask(1L, taskCommand()));

        assertEquals(OaErrorCode.TASK_STATUS_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("comment：任务不存在时拒绝")
    void shouldRejectCommentForMissingTask() {
        when(repository.findTask(9L)).thenReturn(null);
        CollabCommands.CommentSaveCommand command = new CollabCommands.CommentSaveCommand();
        command.setContent("进展如何");

        BizException exception = assertThrows(BizException.class,
                () -> taskAppService.addComment(9L, command));

        assertEquals(OaErrorCode.TASK_NOT_FOUND, exception.getCode());
    }

    private static CollabCommands.TaskSaveCommand taskCommand() {
        CollabCommands.TaskSaveCommand command = new CollabCommands.TaskSaveCommand();
        command.setTitle("整理会议纪要");
        command.setOwnerId(101L);
        command.setOwnerName("Bob");
        command.setDueDate(LocalDate.of(2026, 11, 12));
        return command;
    }

    private static OaTask task(String status) {
        OaTask task = new OaTask();
        task.setId(1L);
        task.setTitle("整理会议纪要");
        task.setOwnerId(101L);
        task.setPriority("NORMAL");
        task.setStatus(status);
        task.setSource("PERSONAL");
        return task;
    }
}
