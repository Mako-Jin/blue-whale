package com.bluewhale.doc.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageRecent;
import com.bluewhale.doc.infrastructure.persistence.query.DocRecentRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 最近访问 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface DocPageRecentMapper extends BaseMapper<DocPageRecent> {

    /**
     * 查询用户可读的最近访问页面。
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param limit    最大条数
     * @return 最近访问行
     */
    @Select("""
            SELECT r.page_id, p.title, p.space_id, s.name AS space_name, p.slug,
                   r.visited_at, p.update_time
            FROM doc_page_recent r
            JOIN doc_page p
              ON p.id = r.page_id AND p.tenant_id = r.tenant_id AND p.deleted = 0
            JOIN doc_space s
              ON s.id = p.space_id AND s.tenant_id = p.tenant_id AND s.deleted = 0 AND s.status = 1
            WHERE r.tenant_id = #{tenantId}
              AND r.user_id = #{userId}
              AND r.deleted = 0
              AND (
                    (s.visibility = 'PUBLIC' AND p.status = 'PUBLISHED')
                    OR EXISTS (SELECT 1 FROM doc_space_member m
                               WHERE m.tenant_id = #{tenantId} AND m.space_id = p.space_id
                                 AND m.user_id = #{userId} AND m.deleted = 0)
                  )
              AND (p.is_restricted = 0
                   OR EXISTS (SELECT 1 FROM doc_page_acl a
                              WHERE a.tenant_id = #{tenantId} AND a.page_id = p.id
                                AND a.user_id = #{userId} AND a.deleted = 0)
                   OR EXISTS (SELECT 1 FROM doc_space_member m2
                              WHERE m2.tenant_id = #{tenantId} AND m2.space_id = p.space_id
                                AND m2.user_id = #{userId} AND m2.deleted = 0
                                AND m2.member_role IN ('OWNER', 'ADMIN')))
            ORDER BY r.visited_at DESC
            LIMIT #{limit}
            """)
    List<DocRecentRow> selectRecent(@Param("tenantId") Long tenantId,
                                    @Param("userId") Long userId,
                                    @Param("limit") int limit);

    /**
     * 物理删除多个页面的最近访问记录（供回收站彻底清除）。
     *
     * @param tenantId 租户ID
     * @param pageIds  页面ID集合
     * @return 删除行数
     */
    @Delete("""
            <script>
            DELETE FROM doc_page_recent
            WHERE tenant_id = #{tenantId} AND page_id IN
            <foreach collection="pageIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int deleteByPageIds(@Param("tenantId") Long tenantId, @Param("pageIds") List<Long> pageIds);
}
