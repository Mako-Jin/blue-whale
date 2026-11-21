package com.bluewhale.file.mapper;

import com.bluewhale.file.po.stat.FileBucketCountPo;
import com.bluewhale.file.po.stat.FileDayCountPo;
import com.bluewhale.file.po.stat.FileLibraryStatPo;
import com.bluewhale.file.po.stat.FileRecycleStatPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文件库聚合统计 Mapper（藏书殿概览 T5.4）。
 *
 * <p>单独成一个 Mapper 而不是继续往 {@code FileInfoMapper} 里加：那张 Mapper 承载的是
 * 单实体读写（含变更流、版本、回收站的逐行操作），聚合查询只有四类、口径集中，
 * 混在一起会让"哪条 SQL 会拖慢上传链路"变得看不出来。</p>
 *
 * <p><b>租户列显式写入</b>，理由见 {@code FileLibraryStatQuery}：行级租户插件在上下文
 * 为空时直接放行，总控场景不能指望上下文一定在。</p>
 *
 * <p><b>时间条件用原始列做范围比较</b>（{@code >= / <}），只在输出上套 {@code DATE()}：
 * 把函数套在条件列上会让 {@code idx_tenant_create_time} 失效，7 天窗口在文件多的租户里
 * 会从毫秒级变成全表扫。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface FileStatMapper {

    /**
     * 库存总量：根文件数 + 全部行字节合计
     *
     * @param tenantId 租户 ID
     * @return 聚合行（无数据时各项为 0）
     */
    @Select("SELECT COALESCE(SUM(CASE WHEN parent_id IS NULL THEN 1 ELSE 0 END), 0) AS rootFiles, "
            + "COALESCE(SUM(file_size), 0) AS totalBytes "
            + "FROM file_info WHERE tenant_id = #{tenantId} AND deleted = 0")
    FileLibraryStatPo selectLibraryStat(@Param("tenantId") Long tenantId);

    /**
     * 时间窗内新增的根文件数
     *
     * @param tenantId 租户 ID
     * @param from 起始时刻（含）
     * @param to 截止时刻（不含）
     * @return 新增根文件数
     */
    @Select("SELECT COUNT(*) FROM file_info "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND parent_id IS NULL "
            + "AND create_time >= #{from} AND create_time < #{to}")
    long countCreated(@Param("tenantId") Long tenantId,
                      @Param("from") LocalDateTime from,
                      @Param("to") LocalDateTime to);

    /**
     * 按天统计新增根文件数
     *
     * @param tenantId 租户 ID
     * @param from 起始时刻（含）
     * @param to 截止时刻（不含）
     * @return 每天一行
     */
    @Select("SELECT DATE(create_time) AS day, COUNT(*) AS files FROM file_info "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND parent_id IS NULL "
            + "AND create_time >= #{from} AND create_time < #{to} "
            + "GROUP BY DATE(create_time) ORDER BY day")
    List<FileDayCountPo> countCreatedByDay(@Param("tenantId") Long tenantId,
                                           @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    /**
     * 回收站统计：总数、占用字节、其中超期未清理数
     *
     * @param tenantId 租户 ID
     * @param staleBefore 超期判定线（删除时间早于它即为超期）
     * @return 聚合行（无数据时各项为 0）
     */
    @Select("SELECT COALESCE(SUM(CASE WHEN parent_id IS NULL THEN 1 ELSE 0 END), 0) AS recycleFiles, "
            + "COALESCE(SUM(file_size), 0) AS recycleBytes, "
            + "COALESCE(SUM(CASE WHEN parent_id IS NULL AND delete_time IS NOT NULL "
            + "AND delete_time < #{staleBefore} THEN 1 ELSE 0 END), 0) AS staleFiles "
            + "FROM file_info WHERE tenant_id = #{tenantId} AND deleted = 1")
    FileRecycleStatPo selectRecycleStat(@Param("tenantId") Long tenantId,
                                        @Param("staleBefore") LocalDateTime staleBefore);

    /**
     * 按桶统计根文件数
     *
     * @param tenantId 租户 ID
     * @return 每个桶一行，按文件数倒序（桶数量是个位数量级，不需要分页）
     */
    @Select("SELECT bucket AS bucket, COUNT(*) AS files FROM file_info "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND parent_id IS NULL "
            + "GROUP BY bucket ORDER BY COUNT(*) DESC")
    List<FileBucketCountPo> countByBucket(@Param("tenantId") Long tenantId);
}
