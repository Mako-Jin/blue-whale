package com.bluewhale.file.model.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分片上传会话响应。
 *
 * @author BlueWhale
 */
@Data
public class ChunkUploadInitResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String uploadId;
    private Long chunkSize;
    private List<Integer> uploadedIndexes;
}
