package com.bluewhale.forum.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.forum.dto.TagStatVO;
import com.bluewhale.forum.entity.ForumThreadTag;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 帖子标签 Mapper。
 *
 * <p>写入一律 {@code INSERT IGNORE}：唯一键 {@code (tenant_id, thread_id, tag)} 是幂等兜底，
 * 重复提交同一组标签不该让发帖失败。聚合与筛选都显式写死租户条件并关掉租户插件——
 * 插件对带子查询/别名的聚合语句追加条件的位置不可控。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface ForumThreadTagMapper extends BaseMapper<ForumThreadTag> {

    /**
     * 批量写入标签行（重复标签静默忽略）。
     *
     * @param rows 标签行（调用方保证非空）
     * @return 实际写入行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Insert("<script>"
            + "INSERT IGNORE INTO fm_thread_tag (id, tenant_id, thread_id, tag, create_time) VALUES "
            + "<foreach collection='rows' item='row' separator=','>"
            + "(#{row.id}, #{row.tenantId}, #{row.threadId}, #{row.tag}, NOW())"
            + "</foreach>"
            + "</script>")
    int insertIgnoreBatch(@Param("rows") List<ForumThreadTag> rows);

    /**
     * 标签聚合：只统计可见版块内已发布帖子，按帖子数降序、标签名升序。
     *
     * <p>必须联 {@code fm_thread} 再过滤：软删帖与待审/驳回帖的标签行还在，
     * 直接对 {@code fm_thread_tag} 分组会把已删帖子的标签也展示出来。</p>
     *
     * @param tenantId 租户ID
     * @param boardIds 可见版块ID（不可为空，空集合由调用方提前返回）
     * @param limit 取前 N 个标签
     * @return 标签聚合
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            <script>
            SELECT tt.tag AS tag, COUNT(*) AS thread_count
            FROM fm_thread_tag tt
            INNER JOIN fm_thread t ON t.id = tt.thread_id AND t.tenant_id = tt.tenant_id
            WHERE tt.tenant_id = #{tenantId} AND t.deleted = 0
              AND t.status IN ('PUBLISHED', 'SOLVED', 'CLOSED')
              AND t.board_id IN
              <foreach collection="boardIds" item="bid" open="(" separator="," close=")">#{bid}</foreach>
            GROUP BY tt.tag
            ORDER BY COUNT(*) DESC, tt.tag ASC
            LIMIT #{limit}
            </script>
            """)
    List<TagStatVO> selectTagStats(@Param("tenantId") Long tenantId,
                                   @Param("boardIds") List<Long> boardIds,
                                   @Param("limit") int limit);
}
