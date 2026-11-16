package com.bluewhale.file.infrastructure.lock;

import com.bluewhale.file.domain.lock.ScheduledTaskLock;
import com.bluewhale.file.mapper.FileInfoMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MybatisScheduledTaskLock} 抢锁、归属校验与释放单元测试。
 *
 * @author BlueWhale
 */
class MybatisScheduledTaskLockTest {

    private final FileInfoMapper fileInfoMapper = mock(FileInfoMapper.class);

    private final MybatisScheduledTaskLock lock = new MybatisScheduledTaskLock(fileInfoMapper);

    @Test
    @DisplayName("抢锁成功：返回句柄，关闭时按锁名与属主释放")
    void shouldAcquireAndRelease() {
        when(fileInfoMapper.countTaskLockOwner(anyString(), anyString())).thenReturn(1);

        Optional<ScheduledTaskLock.Handle> handle = lock.tryAcquire("file:test", Duration.ofMinutes(30));

        assertTrue(handle.isPresent());
        ArgumentCaptor<String> owner = ArgumentCaptor.forClass(String.class);
        verify(fileInfoMapper).tryAcquireTaskLock(eq("file:test"), owner.capture(), eq(1800));

        handle.get().close();

        verify(fileInfoMapper).releaseTaskLock("file:test", owner.getValue());
    }

    @Test
    @DisplayName("归属校验失败：视为未抢到锁")
    void shouldReturnEmptyWhenNotOwner() {
        when(fileInfoMapper.countTaskLockOwner(anyString(), anyString())).thenReturn(0);

        assertTrue(lock.tryAcquire("file:test", Duration.ofMinutes(5)).isEmpty());
    }

    @Test
    @DisplayName("租约非正：直接返回空且不访问数据库")
    void shouldRejectNonPositiveTtl() {
        assertTrue(lock.tryAcquire("file:test", Duration.ZERO).isEmpty());

        verify(fileInfoMapper, never()).tryAcquireTaskLock(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("释放失败仅告警，不向调用方抛出")
    void shouldIgnoreReleaseFailure() {
        when(fileInfoMapper.countTaskLockOwner(anyString(), anyString())).thenReturn(1);
        when(fileInfoMapper.releaseTaskLock(anyString(), anyString()))
                .thenThrow(new IllegalStateException("db down"));

        ScheduledTaskLock.Handle handle = lock.tryAcquire("file:test", Duration.ofSeconds(1)).orElseThrow();

        handle.close();

        verify(fileInfoMapper).releaseTaskLock(eq("file:test"), anyString());
    }

    @Test
    @DisplayName("租约不足 1 秒时按 1 秒登记")
    void shouldFloorTtlToOneSecond() {
        when(fileInfoMapper.countTaskLockOwner(anyString(), anyString())).thenReturn(1);

        lock.tryAcquire("file:test", Duration.ofMillis(200));

        verify(fileInfoMapper).tryAcquireTaskLock(eq("file:test"), anyString(), eq(1));
    }

    @Test
    @DisplayName("锁名为空：直接返回空且不访问数据库")
    void shouldRejectBlankName() {
        assertEquals(Optional.empty(), lock.tryAcquire(" ", Duration.ofMinutes(1)));

        verify(fileInfoMapper, never()).tryAcquireTaskLock(anyString(), anyString(), anyInt());
    }
}
