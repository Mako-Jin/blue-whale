package com.bluewhale.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.file.entity.FileInfo;
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
 * 以便及时移除对应索引；MyBatis-Plus 的通用查询会自动过滤逻辑删除行，无法满足该语义。</p>
 *
 * @author BlueWhale
 */
public interface FileInfoMapper extends BaseMapper<FileInfo> {

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
    List<FileInfo> selectChanges(@Param("since") LocalDateTime since,
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
}
