package com.bluewhale.file.domain.storage;

import java.io.InputStream;
import java.util.List;

/**
 * 分片上传暂存端口：定义会话初始化、分片暂存、进度查询、合并与清理能力。
 *
 * <p>应用层只依赖本端口，不感知临时目录、磁盘路径或暂存介质；默认实现为服务本机磁盘，
 * 后续可替换为共享暂存或对象存储分片协议适配。</p>
 *
 * @author BlueWhale
 */
public interface ChunkUploadStore {

    /**
     * 初始化上传会话：同一会话重复初始化（续传）时保留已暂存分片
     *
     * @param sessionId 会话标识
     * @param metadata 会话元数据（文件名、MIME 类型、总字节数）
     */
    void initialize(ChunkUploadSessionId sessionId, ChunkUploadMetadata metadata);

    /**
     * 判断会话是否仍存在
     *
     * @param sessionId 会话标识
     * @return 会话存在时返回 {@code true}
     */
    boolean exists(ChunkUploadSessionId sessionId);

    /**
     * 暂存单个分片，同索引重复上传覆盖旧分片
     *
     * @param sessionId 会话标识
     * @param index 分片索引（从 0 开始）
     * @param content 分片内容流，由调用方负责关闭
     * @param size 分片字节数
     */
    void storePart(ChunkUploadSessionId sessionId, int index, InputStream content, long size);

    /**
     * 查询已暂存的分片索引
     *
     * @param sessionId 会话标识
     * @return 已暂存分片索引（升序）；会话不存在时返回空列表
     */
    List<Integer> uploadedPartIndexes(ChunkUploadSessionId sessionId);

    /**
     * 按索引顺序合并分片，并计算合并结果的字节数与 MD5 摘要
     *
     * @param sessionId 会话标识
     * @param partCount 期望的分片数量
     * @return 合并结果（内容流由调用方关闭）
     * @throws com.bluewhale.common.domain.exception.DomainException 会话不存在或分片缺失时抛出 {@code PARAM_ERROR}
     */
    MergedChunkUpload merge(ChunkUploadSessionId sessionId, int partCount);

    /**
     * 清理会话暂存数据（含分片与合并文件），不存在时静默返回
     *
     * @param sessionId 会话标识
     */
    void discard(ChunkUploadSessionId sessionId);
}
