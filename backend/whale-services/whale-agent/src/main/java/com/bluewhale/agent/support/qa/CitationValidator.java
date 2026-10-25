package com.bluewhale.agent.support.qa;

import com.bluewhale.agent.config.AgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 引用校验：丢弃路径不存在的引用，并把行号裁剪到文件实际范围。
 *
 * <p>{@code git:} / {@code runtime:} 为虚拟来源（来自 git 与 Nacos 快照），不做文件存在性校验。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CitationValidator {

    /** 虚拟来源前缀 */
    private static final String VIRTUAL_GIT = "git:";

    /** 虚拟来源前缀 */
    private static final String VIRTUAL_RUNTIME = "runtime:";

    private final AgentProperties properties;

    /**
     * 校验并裁剪引用。
     *
     * @param citations 原始引用
     * @return 有效引用（保持原顺序）
     */
    public List<Citation> validate(List<Citation> citations) {
        List<Citation> result = new ArrayList<>(citations.size());
        Path repoRoot = properties.resolveRepoRoot();
        for (Citation citation : citations) {
            String path = citation.path();
            if (path == null || path.isBlank()) {
                continue;
            }
            if (path.startsWith(VIRTUAL_GIT) || path.startsWith(VIRTUAL_RUNTIME)) {
                result.add(citation);
                continue;
            }
            Path file = repoRoot.resolve(path).normalize();
            if (!file.startsWith(repoRoot) || !Files.isRegularFile(file)) {
                log.debug("丢弃路径不存在的引用: {} [{}]", path, citation.sourceId());
                continue;
            }
            if ("chunk".equals(citation.kind()) && citation.startLine() != null) {
                long total = lineCount(file);
                if (total <= 0 || citation.startLine() > total) {
                    continue;
                }
                int end = citation.endLine() == null ? (int) total : Math.min(citation.endLine(), (int) total);
                result.add(new Citation(citation.sourceId(), citation.kind(), path, citation.title(),
                        citation.startLine(), end, citation.snippet(), citation.factType(),
                        citation.factKey(), citation.score(), citation.fileId()));
            } else {
                result.add(citation);
            }
        }
        return result;
    }

    /**
     * 统计文件行数。
     *
     * @param file 文件
     * @return 行数；读取失败返回 0
     */
    private static long lineCount(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return lines.count();
        } catch (IOException e) {
            return 0;
        }
    }
}
