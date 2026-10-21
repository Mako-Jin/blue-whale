package com.bluewhale.agent.support.facts;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Git 演进事实采集器：{@code git log --numstat} → {@code COMMIT} 事实。
 *
 * <p>每条提交记录 hash、时间、标题、Wave 标记与变更文件列表，供「为什么这样设计」
 * 「某文件最近改动」类问题追溯。Git 不可用时返回空列表，不影响其它事实。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class GitCommitFactCollector implements FactCollector {

    /** 采集条数上限 */
    private static final int MAX_COMMITS = 2000;

    /** 单条提交记录的文件列表上限 */
    private static final int MAX_FILES_PER_COMMIT = 100;

    /** 提交分隔标记 */
    private static final String MARKER = "__COMMIT__";

    /** Wave 标记 */
    private static final Pattern WAVE = Pattern.compile("(?i)wave\\s*([0-9]+(?:\\.[0-9]+)?)");

    @Override
    public List<FactRecord> collect(Path repoRoot) {
        List<FactRecord> facts = new ArrayList<>();
        List<String> lines = readLog(repoRoot);
        if (lines.isEmpty()) {
            return facts;
        }
        String hash = null;
        String date = null;
        String subject = null;
        List<String> files = new ArrayList<>();
        for (String line : lines) {
            if (line.startsWith(MARKER)) {
                if (hash != null) {
                    facts.add(toFact(hash, date, subject, files));
                }
                String[] header = line.substring(MARKER.length()).split("\\|", 3);
                hash = header.length > 0 ? header[0] : null;
                date = header.length > 1 ? header[1] : "";
                subject = header.length > 2 ? header[2] : "";
                files = new ArrayList<>();
                continue;
            }
            if (hash != null && !line.isBlank() && files.size() < MAX_FILES_PER_COMMIT) {
                String[] parts = line.split("\\t", 3);
                if (parts.length == 3) {
                    files.add(parts[2].trim());
                }
            }
        }
        if (hash != null) {
            facts.add(toFact(hash, date, subject, files));
        }
        return facts;
    }

    /**
     * 构造 COMMIT 事实。
     *
     * @param hash    提交哈希
     * @param date    提交时间
     * @param subject 提交标题
     * @param files   变更文件
     * @return 事实记录
     */
    private FactRecord toFact(String hash, String date, String subject, List<String> files) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("hash", hash);
        value.put("date", date);
        value.put("subject", subject);
        value.put("wave", waveOf(subject));
        value.put("files", files);
        return new FactRecord("COMMIT", hash, value, "git:" + hash);
    }

    /**
     * 提取 Wave 标记。
     *
     * @param subject 提交标题
     * @return Wave 号；无则空串
     */
    private static String waveOf(String subject) {
        Matcher matcher = WAVE.matcher(subject == null ? "" : subject);
        return matcher.find() ? matcher.group(1) : "";
    }

    /**
     * 执行 {@code git log} 并读取输出。
     *
     * @param repoRoot 仓库根
     * @return 输出行；失败返回空列表
     */
    private List<String> readLog(Path repoRoot) {
        ProcessBuilder builder = new ProcessBuilder("git", "log", "-n", String.valueOf(MAX_COMMITS),
                "--numstat", "--pretty=format:" + MARKER + "%H|%aI|%s");
        builder.directory(repoRoot.toFile());
        builder.redirectErrorStream(false);
        try {
            Process process = builder.start();
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            if (!process.waitFor(60, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("git log 采集超时，跳过 COMMIT 事实");
                return List.of();
            }
            return process.exitValue() == 0 ? lines : List.of();
        } catch (IOException e) {
            log.warn("git log 不可用，跳过 COMMIT 事实: {}", e.getMessage());
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }
}
