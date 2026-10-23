package com.bluewhale.file.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 分片上传初始化结果视图对象。
 *
 * @author BlueWhale
 */
@Data
public class ChunkUploadInitVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 上传会话 ID（UUID） */
    private String uploadId;

    /** 分片大小（字节） */
    private Long chunkSize;

    /** 已上传分片索引（升序；新建会话时为空列表） */
    private List<Integer> uploadedIndexes;
}
