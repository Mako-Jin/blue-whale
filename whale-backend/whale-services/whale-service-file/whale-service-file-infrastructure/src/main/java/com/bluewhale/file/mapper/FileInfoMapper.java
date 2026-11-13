package com.bluewhale.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.file.po.FileInfoPo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
    @Select("<script>SELECT id, file_name, original_name, bucket, object_key, file_size, content_type, md5, "
            + "status, uploader_id, create_time, update_time, deleted FROM file_info "
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
    @Select("SELECT id, file_name, original_name, bucket, object_key, file_size, content_type, md5, "
            + "status, uploader_id, create_time, update_time, deleted FROM file_info WHERE id = #{id}")
    FileInfoPo selectByIdIncludingDeleted(@Param("id") Long id);

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
