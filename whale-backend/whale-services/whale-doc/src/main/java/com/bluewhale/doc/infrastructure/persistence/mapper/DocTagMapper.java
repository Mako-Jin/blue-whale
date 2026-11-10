package com.bluewhale.doc.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.doc.infrastructure.persistence.entity.DocTag;
import com.bluewhale.doc.infrastructure.persistence.query.DocTagRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文档标签 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface DocTagMapper extends BaseMapper<DocTag> {

    /**
     * 查询空间标签及当前用户可读页面数量。
     *
     * @param tenantId 租户ID
     * @param userId   当前用户ID
     * @param spaceId  空间ID
     * @return 标签行
     */
    @Select("""
            SELECT t.id AS tag_id, t.name, t.color,
                   COUNT(CASE WHEN p.id IS NOT NULL AND s.id IS NOT NULL
                       AND ((s.visibility = 'PUBLIC' AND p.status = 'PUBLISHED')
                            OR EXISTS (SELECT 1 FROM doc_space_member m
                                       WHERE m.tenant_id = #{tenantId} AND m.space_id = p.space_id
                                         AND m.user_id = #{userId} AND m.deleted = 0))
                       AND (p.is_restricted = 0
                            OR EXISTS (SELECT 1 FROM doc_page_acl a
                                       WHERE a.tenant_id = #{tenantId} AND a.page_id = p.id
                                         AND a.user_id = #{userId} AND a.deleted = 0)
                            OR EXISTS (SELECT 1 FROM doc_space_member m2
                                       WHERE m2.tenant_id = #{tenantId} AND m2.space_id = p.space_id
                                         AND m2.user_id = #{userId} AND m2.deleted = 0
                                         AND m2.member_role IN ('OWNER', 'ADMIN')))
                     THEN 1 END) AS page_count
            FROM doc_tag t
            LEFT JOIN doc_page_tag pt
                   ON pt.tag_id = t.id AND pt.tenant_id = t.tenant_id AND pt.deleted = 0
            LEFT JOIN doc_page p
                   ON p.id = pt.page_id AND p.tenant_id = t.tenant_id AND p.deleted = 0
            LEFT JOIN doc_space s
                   ON s.id = p.space_id AND s.tenant_id = p.tenant_id AND s.deleted = 0 AND s.status = 1
            WHERE t.tenant_id = #{tenantId}
              AND t.space_id = #{spaceId}
              AND t.deleted = 0
            GROUP BY t.id, t.name, t.color
            ORDER BY t.name ASC
            """)
    List<DocTagRow> selectWithPageCount(@Param("tenantId") Long tenantId,
                                        @Param("userId") Long userId,
                                        @Param("spaceId") Long spaceId);

    /**
     * 物理删除标签（配合页面标签物理删除，避免标签名唯一键与逻辑删除冲突）。
     *
     * @param tenantId 租户ID
     * @param tagId    标签ID
     * @return 删除行数
     */
    @Delete("DELETE FROM doc_tag WHERE tenant_id = #{tenantId} AND id = #{tagId}")
    int deletePhysical(@Param("tenantId") Long tenantId, @Param("tagId") Long tagId);
}
