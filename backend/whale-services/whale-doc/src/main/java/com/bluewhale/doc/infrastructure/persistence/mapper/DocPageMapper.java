package com.bluewhale.doc.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.query.DocPageChangeRow;
import com.bluewhale.doc.infrastructure.persistence.query.DocSearchRow;
import com.bluewhale.doc.infrastructure.persistence.query.DocTrashPageRow;
import com.bluewhale.doc.infrastructure.persistence.query.DocTrashRootRow;
import com.bluewhale.doc.interfaces.vo.DocInternalPageVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档页面 Mapper。
 *
 * <p>{@link #changes} / {@link #countChanges} 为知识源增量同步专用：绕过逻辑删除，
 * 返回全部页面（含草稿/撤销发布/归档/删除/空间转私有）作为墓碑，由采集方结合
 * {@code spaceVisibility}/{@code spaceActive} 判定是否下线索引。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface DocPageMapper extends BaseMapper<DocPage> {

    /**
     * 查询租户内全部页面的增量变更（含墓碑）。
     *
     * <p>空间用 LEFT JOIN 且不过滤状态，保证页面转私有、空间停用或删除时
     * 仍能通过 {@code space_visibility}/{@code space_status}/{@code space_deleted}
     * 通知采集方下线索引。</p>
     *
     * <p>游标使用闭区间（{@code >=}）：数据库 {@code update_time} 只精确到秒，
     * 同一秒内的发布/归档/恢复会产生相同时间戳，开区间会漏掉同秒变更。采集方
     * 依据 revision 与状态判重，重复拉取不会重复写入索引。</p>
     *
     * @param tenantId 租户ID
     * @param since    起始更新时间（含；null 表示全量）
     * @param offset   偏移量
     * @param size     每页条数
     * @return 变更行
     */
    @Select("""
            <script>
            SELECT p.id AS page_id, p.space_id AS space_id, p.title, p.slug,
                   p.status, p.revision, p.is_restricted, p.deleted, p.update_time,
                   s.visibility, s.status AS space_status, s.deleted AS space_deleted,
                   s.name AS space_name
            FROM doc_page p
            LEFT JOIN doc_space s ON s.id = p.space_id
            WHERE p.tenant_id = #{tenantId}
              AND (#{since} IS NULL OR p.update_time &gt;= #{since})
            ORDER BY p.update_time ASC, p.id ASC
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<DocPageChangeRow> changes(@Param("tenantId") Long tenantId,
                                   @Param("since") LocalDateTime since,
                                   @Param("offset") long offset,
                                   @Param("size") int size);

    /**
     * 统计租户内页面增量变更总数（游标闭区间，与 {@link #changes} 保持一致）。
     *
     * @param tenantId 租户ID
     * @param since    起始更新时间（含；null 表示全量）
     * @return 总数
     */
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM doc_page p
            WHERE p.tenant_id = #{tenantId}
              AND (#{since} IS NULL OR p.update_time &gt;= #{since})
            </script>
            """)
    long countChanges(@Param("tenantId") Long tenantId,
                      @Param("since") LocalDateTime since);

    /**
     * 统计空间内 slug 占用数量（包含逻辑删除行）。
     *
     * @param tenantId  租户ID
     * @param spaceId   空间ID
     * @param slug      页面路径
     * @param excludeId 排除的页面ID，新增传 0
     * @return 占用数量
     */
    @Select("""
            SELECT COUNT(*) FROM doc_page
            WHERE tenant_id = #{tenantId} AND space_id = #{spaceId} AND slug = #{slug} AND id <> #{excludeId}
            """)
    long countSlugIncludeDeleted(@Param("tenantId") Long tenantId,
                                 @Param("spaceId") Long spaceId,
                                 @Param("slug") String slug,
                                 @Param("excludeId") Long excludeId);

    /**
     * 搜索已发布正文页面：仅公开空间或当前用户已加入的空间。
     *
     * <p>M2：页面级权限（{@code is_restricted=1}）只对 ACL 授权用户与空间
     * OWNER/ADMIN 可见；{@code tagId} 用于标签筛选；关键词长度达到 ngram
     * 最小长度（2）时走 {@code FULLTEXT ... WITH PARSER ngram}，否则回退 LIKE。</p>
     *
     * @param tenantId   租户ID
     * @param userId     当前用户ID
     * @param keyword    关键词
     * @param spaceId    限定空间，null 表示全部
     * @param tagId      限定标签，null 表示不限
     * @param useFulltext 是否使用全文索引（策略由应用层按关键词长度决定）
     * @param offset     偏移量
     * @param size       每页条数
     * @return 命中行（含纯文本投影供摘要）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            <script>
            SELECT p.id AS page_id, p.space_id AS space_id, s.name AS space_name, p.title, p.slug,
                   p.status, p.revision, p.content_text, p.update_time
            FROM doc_page p
            JOIN doc_space s ON s.id = p.space_id AND s.deleted = 0 AND s.status = 1
            WHERE p.tenant_id = #{tenantId}
              AND s.tenant_id = #{tenantId}
              AND p.deleted = 0
              AND p.page_type = 'PAGE'
              AND p.status = 'PUBLISHED'
              AND (#{spaceId} IS NULL OR p.space_id = #{spaceId})
              AND (
                    (p.is_restricted = 0
                     AND (s.visibility = 'PUBLIC'
                          OR EXISTS (SELECT 1 FROM doc_space_member m
                                     WHERE m.tenant_id = #{tenantId} AND m.space_id = p.space_id
                                       AND m.user_id = #{userId} AND m.deleted = 0)))
                 OR (p.is_restricted = 1
                     AND (EXISTS (SELECT 1 FROM doc_page_acl a
                                  WHERE a.tenant_id = #{tenantId} AND a.page_id = p.id
                                    AND a.user_id = #{userId} AND a.deleted = 0)
                          OR EXISTS (SELECT 1 FROM doc_space_member m2
                                     WHERE m2.tenant_id = #{tenantId} AND m2.space_id = p.space_id
                                       AND m2.user_id = #{userId} AND m2.deleted = 0
                                       AND m2.member_role IN ('OWNER', 'ADMIN')))
              ))
              AND (#{tagId} IS NULL OR EXISTS (SELECT 1 FROM doc_page_tag pt
                                               WHERE pt.tenant_id = #{tenantId} AND pt.page_id = p.id
                                                 AND pt.tag_id = #{tagId} AND pt.deleted = 0))
              AND (
                <if test="useFulltext">
                    MATCH(p.title, p.content_text) AGAINST (#{keyword} IN NATURAL LANGUAGE MODE)
                </if>
                <if test="!useFulltext">
                    (p.title LIKE CONCAT('%', #{keyword}, '%')
                     OR p.content_text LIKE CONCAT('%', #{keyword}, '%'))
                </if>
              )
            ORDER BY
              <if test="useFulltext">
                MATCH(p.title, p.content_text) AGAINST (#{keyword} IN NATURAL LANGUAGE MODE) DESC,
              </if>
              p.update_time DESC, p.id DESC
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<DocSearchRow> search(@Param("tenantId") Long tenantId,
                              @Param("userId") Long userId,
                              @Param("keyword") String keyword,
                              @Param("spaceId") Long spaceId,
                              @Param("tagId") Long tagId,
                              @Param("useFulltext") boolean useFulltext,
                              @Param("offset") long offset,
                              @Param("size") int size);

    /**
     * 统计搜索命中总数（过滤条件与 {@link #search} 保持一致）。
     *
     * @param tenantId    租户ID
     * @param userId      当前用户ID
     * @param keyword     关键词
     * @param spaceId     限定空间，null 表示全部
     * @param tagId       限定标签，null 表示不限
     * @param useFulltext 是否使用全文索引
     * @return 命中总数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            <script>
            SELECT COUNT(*)
            FROM doc_page p
            JOIN doc_space s ON s.id = p.space_id AND s.deleted = 0 AND s.status = 1
            WHERE p.tenant_id = #{tenantId}
              AND s.tenant_id = #{tenantId}
              AND p.deleted = 0
              AND p.page_type = 'PAGE'
              AND p.status = 'PUBLISHED'
              AND (#{spaceId} IS NULL OR p.space_id = #{spaceId})
              AND (
                    (p.is_restricted = 0
                     AND (s.visibility = 'PUBLIC'
                          OR EXISTS (SELECT 1 FROM doc_space_member m
                                     WHERE m.tenant_id = #{tenantId} AND m.space_id = p.space_id
                                       AND m.user_id = #{userId} AND m.deleted = 0)))
                 OR (p.is_restricted = 1
                     AND (EXISTS (SELECT 1 FROM doc_page_acl a
                                  WHERE a.tenant_id = #{tenantId} AND a.page_id = p.id
                                    AND a.user_id = #{userId} AND a.deleted = 0)
                          OR EXISTS (SELECT 1 FROM doc_space_member m2
                                     WHERE m2.tenant_id = #{tenantId} AND m2.space_id = p.space_id
                                       AND m2.user_id = #{userId} AND m2.deleted = 0
                                       AND m2.member_role IN ('OWNER', 'ADMIN')))
              ))
              AND (#{tagId} IS NULL OR EXISTS (SELECT 1 FROM doc_page_tag pt
                                               WHERE pt.tenant_id = #{tenantId} AND pt.page_id = p.id
                                                 AND pt.tag_id = #{tagId} AND pt.deleted = 0))
              AND (
                <if test="useFulltext">
                    MATCH(p.title, p.content_text) AGAINST (#{keyword} IN NATURAL LANGUAGE MODE)
                </if>
                <if test="!useFulltext">
                    (p.title LIKE CONCAT('%', #{keyword}, '%')
                     OR p.content_text LIKE CONCAT('%', #{keyword}, '%'))
                </if>
              )
            </script>
            """)
    long countSearch(@Param("tenantId") Long tenantId,
                     @Param("userId") Long userId,
                     @Param("keyword") String keyword,
                     @Param("spaceId") Long spaceId,
                     @Param("tagId") Long tagId,
                     @Param("useFulltext") boolean useFulltext);

    /**
     * 读取内网页面快照：仅公开且启用空间中的已发布正文页面。
     *
     * <p>{@code page_type = 'PAGE'} 用于排除目录节点，避免把 FOLDER 的空白正文写入知识索引。
     * 快照同时返回空间可见性与启用标记，供采集方对单页重建复用同一套可采集判定。</p>
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @return 页面快照；不可见返回 {@code null}
     */
    @Select("""
            SELECT p.id AS page_id, p.space_id AS space_id, s.name AS space_name, p.title, p.slug,
                   p.status, p.revision, p.content_text, p.update_time,
                   s.visibility AS space_visibility,
                   CASE WHEN s.status = 1 AND s.deleted = 0 THEN 1 ELSE 0 END AS space_active
            FROM doc_page p
            JOIN doc_space s ON s.id = p.space_id AND s.deleted = 0 AND s.status = 1
            WHERE p.tenant_id = #{tenantId}
              AND p.id = #{pageId}
              AND p.deleted = 0
              AND p.page_type = 'PAGE'
              AND p.status = 'PUBLISHED'
              AND p.is_restricted = 0
              AND s.visibility = 'PUBLIC'
            """)
    DocInternalPageVO selectInternalPage(@Param("tenantId") Long tenantId, @Param("pageId") Long pageId);

    /**
     * 查询未删除页面子树（含根自身），用于「整棵子树移入回收站」。
     *
     * @param tenantId 租户ID
     * @param rootId   子树根页面ID
     * @return 子树页面轻量行
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            WITH RECURSIVE subtree AS (
                SELECT id, parent_id, title, slug, page_type, space_id
                FROM doc_page
                WHERE tenant_id = #{tenantId} AND id = #{rootId} AND deleted = 0
                UNION ALL
                SELECT p.id, p.parent_id, p.title, p.slug, p.page_type, p.space_id
                FROM doc_page p
                JOIN subtree s ON p.parent_id = s.id
                WHERE p.tenant_id = #{tenantId} AND p.deleted = 0
            )
            SELECT id AS page_id, parent_id, title, slug, page_type, space_id
            FROM subtree
            """)
    List<DocTrashPageRow> selectSubtree(@Param("tenantId") Long tenantId,
                                        @Param("rootId") Long rootId);

    /**
     * 查询某次回收站删除产生的子树（含根自身）。
     *
     * @param tenantId 租户ID
     * @param rootId   回收站根页面ID
     * @return 子树页面轻量行
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT id AS page_id, parent_id, title, slug, page_type, space_id
            FROM doc_page
            WHERE tenant_id = #{tenantId} AND deleted = 1 AND trash_root_id = #{rootId}
            """)
    List<DocTrashPageRow> selectTrashSubtree(@Param("tenantId") Long tenantId,
                                             @Param("rootId") Long rootId);

    /**
     * 读取页面（包含逻辑删除行），供回收站恢复/清除定位。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @return 页面；不存在返回 {@code null}
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT * FROM doc_page WHERE tenant_id = #{tenantId} AND id = #{pageId}
            """)
    DocPage selectByIdIncludeDeleted(@Param("tenantId") Long tenantId,
                                     @Param("pageId") Long pageId);

    /**
     * 分页查询空间回收站根节点。
     *
     * @param tenantId 租户ID
     * @param spaceId  空间ID
     * @param offset   偏移量
     * @param size     每页条数
     * @return 回收站条目行
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT p.id AS page_id, p.title, p.page_type, p.parent_id, p.deleted_by, p.deleted_at,
                   (SELECT COUNT(*) FROM doc_page c
                    WHERE c.tenant_id = p.tenant_id AND c.trash_root_id = p.id
                      AND c.id <> p.id AND c.deleted = 1) AS child_count,
                   (SELECT pp.title FROM doc_page pp
                    WHERE pp.tenant_id = p.tenant_id AND pp.id = p.parent_id AND pp.deleted = 0
                    LIMIT 1) AS parent_title
            FROM doc_page p
            WHERE p.tenant_id = #{tenantId} AND p.space_id = #{spaceId}
              AND p.deleted = 1 AND p.trash_root_id = p.id
            ORDER BY p.deleted_at DESC, p.id DESC
            LIMIT #{offset}, #{size}
            """)
    List<DocTrashRootRow> selectTrashRoots(@Param("tenantId") Long tenantId,
                                           @Param("spaceId") Long spaceId,
                                           @Param("offset") long offset,
                                           @Param("size") int size);

    /**
     * 统计空间回收站根节点数量。
     *
     * @param tenantId 租户ID
     * @param spaceId  空间ID
     * @return 根节点数量
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT COUNT(*) FROM doc_page
            WHERE tenant_id = #{tenantId} AND space_id = #{spaceId}
              AND deleted = 1 AND trash_root_id = id
            """)
    long countTrashRoots(@Param("tenantId") Long tenantId, @Param("spaceId") Long spaceId);

    /**
     * 将单个页面移入回收站并改写 slug。
     *
     * @param tenantId    租户ID
     * @param pageId      页面ID
     * @param slug        改写后的回收站 slug
     * @param deletedBy   操作人ID
     * @param deletedAt   删除时间
     * @param trashRootId 回收站根页面ID
     * @return 受影响行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE doc_page
            SET deleted = 1, deleted_at = #{deletedAt}, deleted_by = #{deletedBy},
                trash_root_id = #{trashRootId}, slug = #{slug}, update_time = #{deletedAt}
            WHERE tenant_id = #{tenantId} AND id = #{pageId} AND deleted = 0
            """)
    int trashPage(@Param("tenantId") Long tenantId,
                  @Param("pageId") Long pageId,
                  @Param("slug") String slug,
                  @Param("deletedBy") Long deletedBy,
                  @Param("deletedAt") LocalDateTime deletedAt,
                  @Param("trashRootId") Long trashRootId);

    /**
     * 恢复单个页面并写入恢复后的 slug。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @param slug     恢复后的 slug
     * @param now      恢复时间
     * @return 受影响行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("""
            UPDATE doc_page
            SET deleted = 0, deleted_at = NULL, deleted_by = NULL, trash_root_id = NULL,
                slug = #{slug}, update_time = #{now}
            WHERE tenant_id = #{tenantId} AND id = #{pageId} AND deleted = 1
            """)
    int restorePage(@Param("tenantId") Long tenantId,
                    @Param("pageId") Long pageId,
                    @Param("slug") String slug,
                    @Param("now") LocalDateTime now);

    /**
     * 物理删除一次回收站删除产生的整棵子树。
     *
     * @param tenantId 租户ID
     * @param rootId   回收站根页面ID
     * @return 删除行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("""
            DELETE FROM doc_page
            WHERE tenant_id = #{tenantId} AND deleted = 1
              AND (trash_root_id = #{rootId} OR (id = #{rootId} AND trash_root_id = id))
            """)
    int purgeSubtree(@Param("tenantId") Long tenantId, @Param("rootId") Long rootId);

    /**
     * 跨租户查询已到期回收站根节点，供定时清理任务使用。
     *
     * @param cutoff 到期时间阈值
     * @param limit  单轮最大数量
     * @return 回收站根节点行（含租户ID）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT tenant_id, id AS page_id
            FROM doc_page
            WHERE deleted = 1 AND trash_root_id = id AND deleted_at IS NOT NULL AND deleted_at < #{cutoff}
            ORDER BY deleted_at ASC
            LIMIT #{limit}
            """)
    List<DocTrashRootRow> selectExpiredTrashRoots(@Param("cutoff") LocalDateTime cutoff,
                                                  @Param("limit") int limit);
}
