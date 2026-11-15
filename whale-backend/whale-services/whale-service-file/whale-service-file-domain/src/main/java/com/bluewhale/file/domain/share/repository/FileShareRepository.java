package com.bluewhale.file.domain.share.repository;

import com.bluewhale.file.domain.share.aggregate.FileShareAggregate;
import com.bluewhale.file.domain.share.model.FileSharePage;
import com.bluewhale.file.domain.share.model.FileShareQuery;

import java.util.Optional;

/**
 * 文件分享仓储端口。
 *
 * <p>下载次数占用必须是存储层原子操作，避免「先查后改」在并发下超发；
 * 撤销同样以条件更新实现幂等。</p>
 *
 * @author BlueWhale
 */
public interface FileShareRepository {

    /**
     * 保存分享：无主键时插入
     *
     * @param share 分享聚合根
     * @return 回填主键后的分享聚合根
     */
    FileShareAggregate save(FileShareAggregate share);

    /**
     * 按主键查询分享（租户条件由持久化层自动追加）
     *
     * @param id 分享ID
     * @return 分享聚合根
     */
    Optional<FileShareAggregate> findById(Long id);

    /**
     * 按令牌查询分享（租户条件由持久化层自动追加）
     *
     * @param token 分享令牌
     * @return 分享聚合根
     */
    Optional<FileShareAggregate> findByToken(String token);

    /**
     * 撤销分享：仅有效分享置为已撤销
     *
     * @param id 分享ID
     * @return 是否发生了状态变更（已撤销或不存在时为 {@code false}）
     */
    boolean revoke(Long id);

    /**
     * 原子占用一次下载次数：状态、有效期与次数上限在同一 SQL 内校验
     *
     * @param id 分享ID
     * @return 是否占用成功（失效或已达上限时为 {@code false}）
     */
    boolean consumeDownload(Long id);

    /**
     * 分页查询分享
     *
     * @param query 分页与创建人过滤条件
     * @return 分享分页结果
     */
    FileSharePage page(FileShareQuery query);
}
