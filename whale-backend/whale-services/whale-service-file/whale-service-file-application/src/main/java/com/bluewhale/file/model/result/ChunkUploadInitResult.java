package com.bluewhale.file.model.result;

import java.util.List;

/**
 * 分片上传会话结果。
 *
 * @param uploadId 上传会话 ID
 * @param chunkSize 生效分片大小（字节）
 * @param uploadedIndexes 已上传分片索引（升序）
 * @author BlueWhale
 */
public record ChunkUploadInitResult(String uploadId, Long chunkSize, List<Integer> uploadedIndexes) {
}
