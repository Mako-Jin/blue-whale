package com.bluewhale.oa.common.domain.repository;

import com.bluewhale.oa.common.domain.model.FileRef;

import java.util.List;
import java.util.Optional;

/**
 * 文件引用仓储。
 *
 * @author BlueWhale
 */
public interface FileRefRepository {

    /**
     * 查询业务单据下的文件引用。
     *
     * @param bizType 业务类型
     * @param bizId 业务单据ID
     * @return 文件引用列表
     */
    List<FileRef> listByBiz(String bizType, Long bizId);

    /**
     * 按业务与文件查询引用。
     *
     * @param bizType 业务类型
     * @param bizId 业务单据ID
     * @param fileId 文件ID
     * @return 文件引用
     */
    Optional<FileRef> findByBizAndFile(String bizType, Long bizId, Long fileId);

    /**
     * 按主键查询引用。
     *
     * @param id 主键
     * @return 文件引用
     */
    Optional<FileRef> findById(Long id);

    /**
     * 新增文件引用；唯一键冲突时返回已存在记录。
     *
     * @param fileRef 文件引用
     * @return 实际保存的文件引用
     */
    Optional<FileRef> insertIfAbsent(FileRef fileRef);

    /**
     * 逻辑删除文件引用。
     *
     * @param id 主键
     */
    void deleteById(Long id);
}
