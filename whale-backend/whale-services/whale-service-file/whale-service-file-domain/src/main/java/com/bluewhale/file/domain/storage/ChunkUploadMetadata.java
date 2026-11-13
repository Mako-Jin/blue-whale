package com.bluewhale.file.domain.storage;

/**
 * 分片上传会话元数据：初始化时登记，完成合并时回传给应用层作为文件元数据来源。
 *
 * <p>除文件信息外还登记初始化时生效的单文件上限与配额，complete 阶段可用它与当前上限
 * 取更严格者，防止 init / complete 两阶段绕过（G1 大文件上传策略）。</p>
 *
 * @param fileName 原始文件名
 * @param contentType MIME 类型，可为 {@code null}
 * @param size 文件总字节数
 * @param maxSizeBytes 初始化时生效的单文件上限；{@link #LIMITS_NOT_RECORDED} 表示未记录
 * @param quotaBytes 初始化时生效的个人配额；{@link #LIMITS_NOT_RECORDED} 表示未记录，
 *                   {@code <=0} 表示不限
 * @author BlueWhale
 */
public record ChunkUploadMetadata(String fileName, String contentType, long size,
                                  long maxSizeBytes, long quotaBytes) {

    /** 生效上限未记录的哨兵值：complete 阶段以当前请求解析出的上限为准 */
    public static final long LIMITS_NOT_RECORDED = -1L;

    /**
     * 构造不含生效上限的会话元数据（上限按 complete 阶段的当前策略解析）
     *
     * @param fileName 原始文件名
     * @param contentType MIME 类型，可为 {@code null}
     * @param size 文件总字节数
     */
    public ChunkUploadMetadata(String fileName, String contentType, long size) {
        this(fileName, contentType, size, LIMITS_NOT_RECORDED, LIMITS_NOT_RECORDED);
    }

    /**
     * 判断会话是否已完整登记生效上限
     *
     * <p>单文件上限与配额必须同时登记才视为有效：任一为 {@link #LIMITS_NOT_RECORDED} 时，
     * 视为未登记上限，complete 阶段整体回落当前策略，避免把哨兵值误当作“不限配额”。</p>
     *
     * <p>两者对 0 的处理不对称源于业务语义：单文件上限 {@code 0} 不是有效上限，故判 {@code > 0}；
     * 配额 {@code 0} 表示“不限配额”，是有效登记值，故只排除 {@link #LIMITS_NOT_RECORDED}。</p>
     *
     * @return 单文件上限与配额均已登记时返回 {@code true}
     */
    public boolean hasRecordedLimits() {
        return maxSizeBytes > 0 && quotaBytes != LIMITS_NOT_RECORDED;
    }
}
