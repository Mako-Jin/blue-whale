package com.bluewhale.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.file.po.FileInfoPo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件信息 Mapper。
 * <p>无需 {@code @Mapper} 注解：由 {@code MybatisPlusConfig} 的
 * {@code @MapperScan("com.bluewhale.**.mapper")} 统一扫描注册。</p>
 *
 * <p>变更流查询使用自定义 SQL：知识库采集需要感知逻辑删除（{@code deleted=1}）的文件，
 * 以便及时移除对应索引；MyBatis-Plus 的通用查询会自动过滤逻辑删除行，无法满足该语义。
 * 变更详情同理，使用 {@link #selectByIdIncludingDeleted(Long)} 绕过逻辑删除过滤。</p>
 *
 * <p><b>维护约定</b>：以下自定义 SQL 显式列出列清单（避免 {@code SELECT *} 带来的隐性耦合），
 * {@code file_info} 新增映射字段时需同步更新这些 SQL，否则该字段在变更流/详情中读不到值。</p>
 *
 * @author BlueWhale
 */
public interface FileInfoMapper extends BaseMapper<FileInfoPo> {

    /**
     * 查询文件变更（含逻辑删除行）。
     *
     * @param since 游标（不含）；{@code null} 表示全量
     * @param offset 偏移量
     * @param size 每页条数
     * @return 变更记录（按更新时间、ID 升序）
     */
    @Select("<script>SELECT id, file_name, original_name, bucket, storage_type, object_key, file_size, content_type, md5, "
            + "status, uploader_id, parent_id, version, create_time, update_time, deleted, delete_time FROM file_info "
            + "<where><if test='since != null'>update_time &gt; #{since}</if></where> "
            + "ORDER BY update_time ASC, id ASC LIMIT #{size} OFFSET #{offset}</script>")
    List<FileInfoPo> selectChanges(@Param("since") LocalDateTime since,
                                   @Param("offset") long offset,
                                   @Param("size") long size);

    /**
     * 统计文件变更条数（含逻辑删除行）。
     *
     * @param since 游标（不含）；{@code null} 表示全量
     * @return 条数
     */
    @Select("<script>SELECT COUNT(*) FROM file_info "
            + "<where><if test='since != null'>update_time &gt; #{since}</if></where></script>")
    long countChanges(@Param("since") LocalDateTime since);

    /**
     * 按主键查询文件（含逻辑删除行）。
     *
     * <p>变更详情需要读取已逻辑删除的文件，供知识库判定是否移除索引；MyBatis-Plus 的
     * {@code selectById} 会自动过滤逻辑删除行，故使用自定义 SQL。</p>
     *
     * @param id 文件ID
     * @return 文件记录（含已逻辑删除行）；不存在时返回 {@code null}
     */
    @Select("SELECT id, file_name, original_name, bucket, storage_type, object_key, file_size, content_type, md5, "
            + "status, uploader_id, parent_id, version, create_time, update_time, deleted, delete_time FROM file_info WHERE id = #{id}")
    FileInfoPo selectByIdIncludingDeleted(@Param("id") Long id);

    /**
     * 查询版本组内版本号最大的一行（含逻辑删除行）。
     *
     * <p>包含逻辑删除行是为了保证版本号单调递增：已删除版本占用的序号不再复用，
     * 避免版本列表出现重复版本号。</p>
     *
     * @param rootId 版本组根文件ID
     * @return 版本号最大的文件记录；不存在时返回 {@code null}
     */
    @Select("SELECT id, file_name, original_name, bucket, storage_type, object_key, file_size, content_type, md5, "
            + "status, uploader_id, parent_id, version, create_time, update_time, deleted, delete_time FROM file_info "
            + "WHERE (id = #{rootId} OR parent_id = #{rootId}) ORDER BY version DESC, id DESC LIMIT 1")
    FileInfoPo selectLatestVersion(@Param("rootId") Long rootId);

    /**
     * 查询版本组内全部未删除版本（含根版本）。
     *
     * @param rootId 版本组根文件ID
     * @return 版本记录（按版本号、ID 倒序）
     */
    @Select("SELECT id, file_name, original_name, bucket, storage_type, object_key, file_size, content_type, md5, "
            + "status, uploader_id, parent_id, version, create_time, update_time, deleted, delete_time FROM file_info "
            + "WHERE deleted = 0 AND (id = #{rootId} OR parent_id = #{rootId}) "
            + "ORDER BY version DESC, id DESC")
    List<FileInfoPo> selectVersions(@Param("rootId") Long rootId);

    /**
     * 查询版本组内已逻辑删除的版本行（不含根文件）。
     *
     * @param rootId 版本组根文件ID
     * @return 已删除版本记录（按版本号、ID 倒序）
     */
    @Select("SELECT id, file_name, original_name, bucket, storage_type, object_key, file_size, content_type, md5, "
            + "status, uploader_id, parent_id, version, create_time, update_time, deleted, delete_time FROM file_info "
            + "WHERE deleted = 1 AND parent_id = #{rootId} ORDER BY version DESC, id DESC")
    List<FileInfoPo> selectDeletedVersions(@Param("rootId") Long rootId);

    /**
     * 统计版本组内待恢复（已逻辑删除）的字节数合计。
     *
     * @param rootId 版本组根文件ID
     * @return 待恢复字节数；无记录时返回 0
     */
    @Select("SELECT COALESCE(SUM(file_size), 0) FROM file_info "
            + "WHERE deleted = 1 AND (id = #{rootId} OR parent_id = #{rootId})")
    long sumDeletedSizeOfGroup(@Param("rootId") Long rootId);

    /**
     * 级联逻辑删除版本组的全部版本行。
     *
     * @param rootId 版本组根文件ID
     * @return 受影响行数
     */
    @Update("UPDATE file_info SET deleted = 1, delete_time = NOW(), update_time = NOW() "
            + "WHERE parent_id = #{rootId} AND deleted = 0")
    int markDeletedByRootId(@Param("rootId") Long rootId);

    /**
     * 级联恢复版本组的全部版本行。
     *
     * @param rootId 版本组根文件ID
     * @return 受影响行数
     */
    @Update("UPDATE file_info SET deleted = 0, delete_time = NULL, update_time = NOW() "
            + "WHERE parent_id = #{rootId} AND deleted = 1")
    int restoreVersionsByRootId(@Param("rootId") Long rootId);

    /**
     * 物理删除版本行（版本保留策略清理使用）。
     *
     * <p>条件带 {@code parent_id IS NOT NULL}，即使传入根文件ID也不会删除根版本。</p>
     *
     * @param id 版本行ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM file_info WHERE id = #{id} AND parent_id IS NOT NULL")
    int purgeVersionById(@Param("id") Long id);

    /**
     * 查询版本数超出允许值的根文件ID（版本保留定时清理使用）。
     *
     * <p>按根文件ID升序游标扫描，只统计未删除的版本行；{@code allowedVersionRows} 为非根版本
     * 允许保留的条数（{@code max-versions - 1}）。</p>
     *
     * @param allowedVersionRows 非根版本允许保留的条数
     * @param afterId 游标（不含）
     * @param limit 单批最大根文件数
     * @return 超出上限的根文件ID
     */
    @Select("SELECT parent_id FROM file_info WHERE parent_id IS NOT NULL AND deleted = 0 "
            + "AND parent_id > #{afterId} GROUP BY parent_id HAVING COUNT(*) > #{allowedVersionRows} "
            + "ORDER BY parent_id ASC LIMIT #{limit}")
    List<Long> selectVersionRootIdsWithExcess(@Param("allowedVersionRows") int allowedVersionRows,
                                              @Param("afterId") long afterId, @Param("limit") long limit);

    /**
     * 分页查询回收站文件（含逻辑删除行，仅 {@code deleted = 1}）。
     *
     * @param originalName 原始文件名模糊条件，可为 {@code null}
     * @param bucket 桶名称等值条件，可为 {@code null}
     * @param uploaderId 上传人ID等值条件，可为 {@code null}
     * @param offset 偏移量
     * @param size 每页条数
     * @return 回收站记录（按删除时间、ID 倒序）
     */
    @Select("<script>SELECT id, file_name, original_name, bucket, storage_type, object_key, file_size, content_type, md5, "
            + "status, uploader_id, parent_id, version, create_time, update_time, deleted, delete_time FROM file_info "
            + "WHERE deleted = 1 AND parent_id IS NULL "
            + "<if test='originalName != null and originalName != \"\"'>"
            + "AND original_name LIKE CONCAT('%', #{originalName}, '%') </if>"
            + "<if test='bucket != null and bucket != \"\"'>AND bucket = #{bucket} </if>"
            + "<if test='uploaderId != null'>AND uploader_id = #{uploaderId} </if>"
            + "ORDER BY delete_time DESC, id DESC LIMIT #{size} OFFSET #{offset}</script>")
    List<FileInfoPo> selectRecyclePage(@Param("originalName") String originalName,
                                       @Param("bucket") String bucket,
                                       @Param("uploaderId") Long uploaderId,
                                       @Param("offset") long offset,
                                       @Param("size") long size);

    /**
     * 统计回收站文件条数（{@code deleted = 1}）。
     *
     * @param originalName 原始文件名模糊条件，可为 {@code null}
     * @param bucket 桶名称等值条件，可为 {@code null}
     * @param uploaderId 上传人ID等值条件，可为 {@code null}
     * @return 条数
     */
    @Select("<script>SELECT COUNT(*) FROM file_info WHERE deleted = 1 AND parent_id IS NULL "
            + "<if test='originalName != null and originalName != \"\"'>"
            + "AND original_name LIKE CONCAT('%', #{originalName}, '%') </if>"
            + "<if test='bucket != null and bucket != \"\"'>AND bucket = #{bucket} </if>"
            + "<if test='uploaderId != null'>AND uploader_id = #{uploaderId} </if>"
            + "</script>")
    long countRecycle(@Param("originalName") String originalName,
                      @Param("bucket") String bucket,
                      @Param("uploaderId") Long uploaderId);

    /**
     * 恢复逻辑删除的文件（{@code deleted} 置 0 并清空删除时间）。
     *
     * @param id 文件ID
     * @return 受影响行数（已恢复或不存在时为 0）
     */
    @Update("UPDATE file_info SET deleted = 0, delete_time = NULL, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 1")
    int restoreById(@Param("id") Long id);

    /**
     * 逻辑删除文件并记录删除时间。
     *
     * <p>条件带 {@code deleted = 0}，重复删除不产生额外变更（幂等）。</p>
     *
     * @param id 文件ID
     * @return 受影响行数（已删除或不存在时为 0）
     */
    @Update("UPDATE file_info SET deleted = 1, delete_time = NOW(), update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0")
    int markDeletedById(@Param("id") Long id);

    /**
     * 更新原始文件名（不改变对象键、存储文件名）。
     *
     * @param id 文件ID
     * @param originalName 新的原始文件名
     * @return 受影响行数
     */
    @Update("UPDATE file_info SET original_name = #{originalName}, update_time = NOW() "
            + "WHERE id = #{id} AND deleted = 0")
    int updateOriginalName(@Param("id") Long id, @Param("originalName") String originalName);

    /**
     * 更新桶名称（物理对象已迁到目标桶）。
     *
     * @param id 文件ID
     * @param bucket 目标桶名称
     * @return 受影响行数
     */
    @Update("UPDATE file_info SET bucket = #{bucket}, update_time = NOW() WHERE id = #{id} AND deleted = 0")
    int updateBucket(@Param("id") Long id, @Param("bucket") String bucket);

    /**
     * 查询删除时间早于截止时间的回收站记录（到期物理清理）。
     *
     * @param cutoff 截止时间（不含）
     * @param size 单批最大条数
     * @return 到期记录（按删除时间、ID 升序）
     */
    @Select("SELECT id, file_name, original_name, bucket, storage_type, object_key, file_size, content_type, md5, "
            + "status, uploader_id, parent_id, version, create_time, update_time, deleted, delete_time FROM file_info "
            + "WHERE deleted = 1 AND delete_time IS NOT NULL AND delete_time < #{cutoff} "
            + "ORDER BY delete_time ASC, id ASC LIMIT #{size}")
    List<FileInfoPo> selectExpiredRecycle(@Param("cutoff") LocalDateTime cutoff, @Param("size") long size);

    /**
     * 物理删除已逻辑删除的元数据行（到期清理）。
     *
     * <p>条件带 {@code deleted = 1}：并发恢复后自动失效，不会误删被用户找回的文件。</p>
     *
     * @param id 文件ID
     * @return 受影响行数
     */
    @Delete("DELETE FROM file_info WHERE id = #{id} AND deleted = 1")
    int purgeById(@Param("id") Long id);

    /**
     * 查询全部元数据对象引用（包含逻辑删除行）。
     *
     * @return 仅含存储类型、桶名、对象键的文件记录
     */
    @Select("SELECT storage_type, bucket, object_key FROM file_info")
    List<FileInfoPo> selectObjectRefs();

    /**
     * 按落盘存储类型查询待迁移文件。
     *
     * <p>按主键升序取前 {@code limit} 条，便于分批迁移与重复执行；租户条件由拦截器追加。</p>
     *
     * @param storageType 源落盘类型
     * @param limit 单批最大条数
     * @return 待迁移文件记录
     */
    @Select("SELECT id, file_name, original_name, bucket, storage_type, object_key, file_size, content_type, md5, "
            + "status, uploader_id, parent_id, version, create_time, update_time, deleted, delete_time FROM file_info "
            + "WHERE storage_type = #{storageType} ORDER BY id ASC LIMIT #{limit}")
    List<FileInfoPo> selectByStorageType(@Param("storageType") String storageType, @Param("limit") long limit);

    /**
     * 更新文件的落盘存储类型（迁移成功后调用）。
     *
     * @param id 文件ID
     * @param storageType 目标存储类型
     * @return 受影响行数
     */
    @Update("UPDATE file_info SET storage_type = #{storageType}, update_time = NOW() WHERE id = #{id}")
    int updateStorageType(@Param("id") Long id, @Param("storageType") String storageType);

    /**
     * 回填历史文件的落盘存储插件类型（仅处理空值行）。
     *
     * <p>启动期无租户上下文，租户插件按既有约定跳过追加租户条件，可覆盖全部存量行；
     * 不修改 {@code update_time}，避免变更流产生无意义的增量。</p>
     *
     * @param storageType 当前活动插件类型
     * @return 回填行数
     */
    @Update("UPDATE file_info SET storage_type = #{storageType} WHERE storage_type IS NULL")
    int backfillStorageType(@Param("storageType") String storageType);

    /**
     * 获取 MySQL 命名锁（行级配额临界区锚点）。
     *
     * <p>命名锁与会话（连接）绑定，须与配额校验、写入处于同一事务连接；释放统一在事务完成回调中执行。</p>
     *
     * @param lockName 锁名称
     * @param timeoutSeconds 获取锁的最长等待秒数
     * @return 1 获取成功；0 超时；{@code null} 表示出错
     */
    @Select("SELECT GET_LOCK(#{lockName}, #{timeoutSeconds})")
    Integer acquireNamedLock(@Param("lockName") String lockName,
                             @Param("timeoutSeconds") int timeoutSeconds);

    /**
     * 释放 MySQL 命名锁。
     *
     * @param lockName 锁名称
     * @return 1 释放成功；0 当前会话未持有；{@code null} 锁不存在
     */
    @Select("SELECT RELEASE_LOCK(#{lockName})")
    Integer releaseNamedLock(@Param("lockName") String lockName);
}
