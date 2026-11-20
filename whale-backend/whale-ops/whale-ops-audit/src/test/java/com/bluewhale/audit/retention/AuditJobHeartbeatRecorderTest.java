package com.bluewhale.audit.retention;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bluewhale.audit.retention.mapper.AuditJobHeartbeatMapper;
import com.bluewhale.audit.retention.po.AuditJobHeartbeatPo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 审计作业心跳记录器单测。
 *
 * <p>验证的是「库里留下什么痕迹」，不是「打印了什么日志」——陈旧度告警读的是
 * {@code last_success_at}，这一列写错就等于心跳失效。</p>
 *
 * <p>断言只认列名与 {@code setSql} 原文：{@code UpdateWrapper.set(column, value)} 的值一律
 * 参数化成 {@code #{ew.paramNameValuePairs.MPGENVAL*}}，字面量不会出现在 SET 子句里。</p>
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AuditJobHeartbeatRecorderTest {

    /** 测试用任务编码 */
    private static final String JOB_CODE = "audit.log.retention";

    /** {@code last_message} 列宽 */
    private static final int MESSAGE_MAX_LENGTH = 512;

    @Mock
    private AuditJobHeartbeatMapper heartbeatMapper;

    @InjectMocks
    private AuditJobHeartbeatRecorder recorder;

    @Test
    @DisplayName("首次执行：update 未命中则插入一行，runCount=1、状态 RUNNING")
    void shouldInsertRowWhenHeartbeatAbsent() {
        doReturn(0).when(heartbeatMapper).update(any(), any());
        doReturn(1).when(heartbeatMapper).insert(any(AuditJobHeartbeatPo.class));

        recorder.recordStart(JOB_CODE);

        ArgumentCaptor<AuditJobHeartbeatPo> captor = ArgumentCaptor.forClass(AuditJobHeartbeatPo.class);
        verify(heartbeatMapper).insert(captor.capture());
        AuditJobHeartbeatPo saved = captor.getValue();
        assertEquals(JOB_CODE, saved.getJobCode());
        assertEquals(AuditJobHeartbeatRecorder.STATUS_RUNNING, saved.getLastStatus());
        assertEquals(1L, saved.getRunCount());
        assertEquals(0L, saved.getFailCount());
        assertNull(saved.getLastSuccessAt(), "开始执行不能推进成功时间");
    }

    @Test
    @DisplayName("已存在心跳行：只更新，不重复插入")
    void shouldOnlyUpdateWhenHeartbeatExists() {
        doReturn(1).when(heartbeatMapper).update(any(), any());

        recorder.recordStart(JOB_CODE);

        verify(heartbeatMapper, never()).insert(any(AuditJobHeartbeatPo.class));
    }

    @Test
    @DisplayName("开始执行：run_count 原子自增、写 last_start_at 与 RUNNING")
    void shouldIncrementRunCountOnStart() {
        doReturn(1).when(heartbeatMapper).update(any(), any());

        recorder.recordStart(JOB_CODE);

        String sqlSet = captureSqlSet();
        assertTrue(sqlSet.contains("run_count = run_count + 1"), sqlSet);
        assertTrue(sqlSet.contains("last_start_at"), sqlSet);
        assertTrue(sqlSet.contains("last_status"), sqlSet);
    }

    @Test
    @DisplayName("成功：推进 last_success_at，且不自增失败计数")
    void shouldAdvanceSuccessTimeOnSuccess() {
        doReturn(1).when(heartbeatMapper).update(any(), any());

        recorder.recordSuccess(JOB_CODE, "clean=false, oldestAuditDays=200");

        String sqlSet = captureSqlSet();
        assertTrue(sqlSet.contains("last_success_at"), "成功必须推进 last_success_at: " + sqlSet);
        assertTrue(sqlSet.contains("last_end_at"), sqlSet);
        assertTrue(sqlSet.contains("last_status"), sqlSet);
        assertTrue(!sqlSet.contains("fail_count"), "成功不得自增失败计数: " + sqlSet);
    }

    @Test
    @DisplayName("失败：自增 fail_count，且绝不推进 last_success_at")
    void shouldNotAdvanceSuccessTimeOnFailure() {
        doReturn(1).when(heartbeatMapper).update(any(), any());

        recorder.recordFailure(JOB_CODE, "RuntimeException: boom");

        String sqlSet = captureSqlSet();
        assertTrue(sqlSet.contains("fail_count = fail_count + 1"), sqlSet);
        assertTrue(sqlSet.contains("last_status"), sqlSet);
        assertTrue(!sqlSet.contains("last_success_at"), "失败不得推进 last_success_at: " + sqlSet);
    }

    @Test
    @DisplayName("首次即失败（无开始记录）：补插一行并记 1 次失败")
    void shouldInsertFailureRowWhenNoStartRecord() {
        doReturn(0).when(heartbeatMapper).update(any(), any());
        doReturn(1).when(heartbeatMapper).insert(any(AuditJobHeartbeatPo.class));

        recorder.recordFailure(JOB_CODE, "boom");

        ArgumentCaptor<AuditJobHeartbeatPo> captor = ArgumentCaptor.forClass(AuditJobHeartbeatPo.class);
        verify(heartbeatMapper).insert(captor.capture());
        AuditJobHeartbeatPo saved = captor.getValue();
        assertEquals(AuditJobHeartbeatRecorder.STATUS_FAILURE, saved.getLastStatus());
        assertEquals(1L, saved.getFailCount());
        assertNull(saved.getLastSuccessAt());
    }

    @Test
    @DisplayName("库不可用：心跳写失败不得抛出，不能把一次正常执行翻成失败")
    void shouldSwallowExceptionWhenDatabaseUnavailable() {
        doThrow(new IllegalStateException("db down")).when(heartbeatMapper).update(any(), any());

        assertDoesNotThrow(() -> recorder.recordStart(JOB_CODE));
        assertDoesNotThrow(() -> recorder.recordSuccess(JOB_CODE, "ok"));
        assertDoesNotThrow(() -> recorder.recordFailure(JOB_CODE, "boom"));
    }

    @Test
    @DisplayName("超长摘要按列宽截断到 512 字符")
    void shouldTruncateLongMessage() {
        doReturn(1).when(heartbeatMapper).update(any(), any());

        recorder.recordSuccess(JOB_CODE, "x".repeat(1000));

        String stored = captureStringParams().stream()
                .filter(value -> value.startsWith("x"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未写入摘要参数"));
        assertEquals(MESSAGE_MAX_LENGTH, stored.length(), "摘要必须截断到 512 字符");
    }

    /**
     * 取最近一次 update 调用传入的 SET 子句
     *
     * @return SET 子句文本（值均为占位符）
     */
    @SuppressWarnings("unchecked")
    private String captureSqlSet() {
        ArgumentCaptor<UpdateWrapper<AuditJobHeartbeatPo>> captor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(heartbeatMapper).update(any(), captor.capture());
        return captor.getValue().getSqlSet();
    }

    /**
     * 取最近一次 update 调用绑定的字符串型参数值
     *
     * @return 参数值集合（非字符串已过滤）
     */
    @SuppressWarnings("unchecked")
    private Collection<String> captureStringParams() {
        ArgumentCaptor<UpdateWrapper<AuditJobHeartbeatPo>> captor =
                ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(heartbeatMapper).update(any(), captor.capture());
        return captor.getValue().getParamNameValuePairs().values().stream()
                .filter(Objects::nonNull)
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }
}
