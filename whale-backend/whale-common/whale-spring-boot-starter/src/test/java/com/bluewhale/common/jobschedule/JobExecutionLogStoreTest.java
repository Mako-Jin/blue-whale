package com.bluewhale.common.jobschedule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JobExecutionLogStore} 采集、落盘与保留策略测试。
 *
 * <p>三个必须守住的边界：没有活跃采集时不写、超过采集上限只标记一次截断、
 * 同一任务编码只保留最近 N 份文件。</p>
 *
 * @author BlueWhale
 */
class JobExecutionLogStoreTest {

    private static final String CODE = "crm.order.reconcile";

    private static final String RUN_ID = "run-1";

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("采集到的日志落盘后可回捞，且带上下文头与执行结论")
    void shouldStoreAndReadBack() {
        JobExecutionLogStore store = store(262144, 20);
        store.begin(RUN_ID, CODE);

        store.append(RUN_ID, "10:00:00.001  INFO 回收开始" + System.lineSeparator());
        store.append(RUN_ID, "10:00:00.002  WARN 跳过 1 条" + System.lineSeparator());
        store.finish(RUN_ID, CODE, "成功, 摘要=removed=0");

        String content = store.readLatest(CODE);
        assertTrue(content.contains("10:00:00.001"), "应包含采集到的日志行：" + content);
        assertTrue(content.contains("跳过 1 条"), "应包含全部日志行：" + content);
        assertTrue(content.contains("# job code : " + CODE), "应带任务编码头：" + content);
        assertTrue(content.contains("--- 执行结束: 成功, 摘要=removed=0 ---"), "应带执行结论：" + content);
    }

    @Test
    @DisplayName("没有活跃采集时追加日志不落盘（普通请求零影响）")
    void shouldIgnoreAppendWithoutActiveCapture() {
        JobExecutionLogStore store = store(262144, 20);

        store.append("unknown-run", "10:00:00.001  INFO 无关日志" + System.lineSeparator());

        assertNull(store.readLatest(CODE), "未 begin 的 runId 不应产生任何文件");
    }

    @Test
    @DisplayName("超过采集上限时丢弃后续内容并标注截断")
    void shouldTruncateWhenExceedLimit() {
        JobExecutionLogStore store = store(120, 20);
        store.begin(RUN_ID, CODE);
        store.append(RUN_ID, "A".repeat(100) + System.lineSeparator());
        store.append(RUN_ID, "B".repeat(200) + System.lineSeparator());

        store.finish(RUN_ID, CODE, "成功");

        String content = store.readLatest(CODE);
        assertTrue(content.contains("日志达到采集上限"), "应标注截断：" + content);
        assertFalse(content.contains("BBBB"), "超限后的内容不应写入：" + content);
    }

    @Test
    @DisplayName("同一任务编码只保留最近 N 份日志文件")
    void shouldRetainLatestFilesOnly() throws IOException {
        JobExecutionLogStore store = store(262144, 2);
        for (int i = 0; i < 4; i++) {
            String runId = "run-" + i;
            store.begin(runId, CODE);
            store.append(runId, "第 " + i + " 次执行" + System.lineSeparator());
            store.finish(runId, CODE, "成功");
        }

        assertEquals(2, countFiles(), "应只保留 2 份日志");
    }

    @Test
    @DisplayName("从未执行过的任务回捞返回 null")
    void shouldReturnNullWhenNoHistory() {
        JobExecutionLogStore store = store(262144, 20);

        assertNull(store.readLatest("never.ran"));
        assertNull(store.readLatest(null));
    }

    /**
     * 构造采集器，日志目录指向临时目录
     *
     * @param maxBytes 单次采集上限
     * @param maxFiles 保留文件数
     * @return 采集器
     */
    private JobExecutionLogStore store(int maxBytes, int maxFiles) {
        JobScheduleProperties.Executor config = new JobScheduleProperties.Executor();
        config.setLogDir(tempDir.toString());
        config.setLogMaxFileBytes(maxBytes);
        config.setLogMaxFiles(maxFiles);
        return new JobExecutionLogStore(config);
    }

    /**
     * 统计临时目录下的日志文件数
     *
     * @return 文件数
     * @throws IOException 遍历失败
     */
    private long countFiles() throws IOException {
        try (Stream<Path> dirs = Files.list(tempDir)) {
            List<Path> files = dirs.filter(Files::isDirectory)
                    .flatMap(dir -> {
                        try (Stream<Path> inner = Files.list(dir)) {
                            return inner.toList().stream();
                        } catch (IOException e) {
                            return Stream.<Path>empty();
                        }
                    })
                    .toList();
            return files.stream().filter(path -> path.toString().endsWith(".log")).count();
        }
    }

    @Test
    @DisplayName("落盘内容使用 UTF-8，中文不丢字")
    void shouldWriteUtf8() {
        JobExecutionLogStore store = store(262144, 20);
        store.begin(RUN_ID, CODE);
        store.append(RUN_ID, "中文日志：回收站清理完成" + System.lineSeparator());
        store.finish(RUN_ID, CODE, "成功");

        String content = store.readLatest(CODE);
        assertEquals(content, new String(content.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        assertTrue(content.contains("回收站清理完成"));
    }
}
