package com.bluewhale.agent.support.ingest;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 切分器注册表：按扩展名路由到对应的 {@link DocumentChunker}。
 *
 * @author BlueWhale
 */
@Component
public class ChunkerRegistry {

    private final List<DocumentChunker> chunkers;

    /**
     * 构造注册表（Spring 注入全部切分器实现）。
     *
     * @param chunkers 切分器列表
     */
    public ChunkerRegistry(List<DocumentChunker> chunkers) {
        this.chunkers = chunkers;
    }

    /**
     * 选取匹配的切分器。
     *
     * @param path 仓库相对路径
     * @return 匹配的切分器；无匹配返回 {@code null}
     */
    public DocumentChunker resolve(String path) {
        for (DocumentChunker chunker : chunkers) {
            if (chunker.supports(path)) {
                return chunker;
            }
        }
        return null;
    }

    /**
     * 提取小写扩展名（不含点）。
     *
     * @param path 文件路径
     * @return 扩展名；无扩展名返回空串
     */
    public static String extension(String path) {
        int dot = path.lastIndexOf('.');
        if (dot < 0 || dot == path.length() - 1) {
            return "";
        }
        return path.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
