package com.bluewhale.doc.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageLink;
import com.bluewhale.doc.infrastructure.persistence.query.DocBacklinkRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 页面引用 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface DocPageLinkMapper extends BaseMapper<DocPageLink> {

    /**
     * 查询指向指定页面的反向链接（仅返回当前用户可读的引用方页面）。
     *
     * @param tenantId 租户ID
     * @param userId   当前用户ID
     * @param pageId   目标页面ID
     * @param limit    最大条数
     * @return 反向链接行
     */
    @Select("""
            SELECT p.id AS page_id, p.title, p.space_id, s.name AS space_name, p.slug,
                   MAX(l.link_text) AS link_text, p.update_time
            FROM doc_page_link l
            JOIN doc_page p
              ON p.id = l.source_page_id AND p.tenant_id = l.tenant_id AND p.deleted = 0
            JOIN doc_space s
              ON s.id = p.space_id AND s.tenant_id = p.tenant_id AND s.deleted = 0 AND s.status = 1
            WHERE l.tenant_id = #{tenantId}
              AND l.target_page_id = #{pageId}
              AND l.deleted = 0
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
            GROUP BY p.id, p.title, p.space_id, s.name, p.slug, p.update_time
            ORDER BY p.update_time DESC
            LIMIT #{limit}
            """)
    List<DocBacklinkRow> selectBacklinks(@Param("tenantId") Long tenantId,
                                         @Param("userId") Long userId,
                                         @Param("pageId") Long pageId,
                                         @Param("limit") int limit);

    /**
     * 物理删除页面的全部引用关系（重新解析正文后重建）。
     *
     * @param tenantId 租户ID
     * @param pageId   引用方页面ID
     * @return 删除行数
     */
    @Delete("DELETE FROM doc_page_link WHERE tenant_id = #{tenantId} AND source_page_id = #{pageId}")
    int deleteBySource(@Param("tenantId") Long tenantId, @Param("pageId") Long pageId);

    /**
     * 物理删除与被清除页面相关的全部引用（作为引用方或被引用方）。
     *
     * @param tenantId 租户ID
     * @param pageIds  页面ID集合
     * @return 删除行数
     */
    @Delete("""
            <script>
            DELETE FROM doc_page_link
            WHERE tenant_id = #{tenantId}
              AND (source_page_id IN
                   <foreach collection="pageIds" item="id" open="(" separator="," close=")">#{id}</foreach>
                   OR target_page_id IN
                   <foreach collection="pageIds" item="id" open="(" separator="," close=")">#{id}</foreach>)
            </script>
            """)
    int deleteByPageIds(@Param("tenantId") Long tenantId, @Param("pageIds") List<Long> pageIds);
}
