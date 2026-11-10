package com.bluewhale.agent.support.ingest;

import java.util.List;

/**
 * 文件切分器契约：按文件类型把源文件切分为可检索知识块。
 *
 * <p>实现按 {@link #supports(String)} 声明可处理的扩展名，由 {@link ChunkerRegistry}
 * 统一路由；新增文件类型只需新增实现并注册为 Spring Bean。</p>
 *
 * @author BlueWhale
 */
public interface DocumentChunker {

    /**
     * 判断是否支持该路径。
     *
     * @param path 仓库相对路径
     * @return 支持返回 {@code true}
     */
    boolean supports(String path);

    /**
     * 切分文件内容。
     *
     * @param path 仓库相对路径
     * @param module 所属模块（可为空串）
     * @param content 文件全文
     * @return 知识块列表；无有效内容时返回空列表
     */
    List<Chunk> chunk(String path, String module, String content);
}
