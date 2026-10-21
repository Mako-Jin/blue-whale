package com.bluewhale.agent.support.ingest;

import lombok.Getter;

import java.nio.file.Path;

/**
 * 扫描结果：一个待索引文件的相对路径、绝对路径与内容哈希。
 *
 * @author BlueWhale
 */
@Getter
public class ScannedFile {

    /** 仓库相对路径（/ 分隔） */
    private final String relativePath;

    /** 绝对路径 */
    private final Path absolutePath;

    /** 内容 SHA-256（十六进制） */
    private final String hash;

    /** 文件字节数 */
    private final long size;

    /**
     * 构造扫描结果。
     *
     * @param relativePath 仓库相对路径
     * @param absolutePath 绝对路径
     * @param hash 内容哈希
     * @param size 文件字节数
     */
    public ScannedFile(String relativePath, Path absolutePath, String hash, long size) {
        this.relativePath = relativePath;
        this.absolutePath = absolutePath;
        this.hash = hash;
        this.size = size;
    }
}
