package com.bluewhale.video.infrastructure.persistence.mapper;

import com.bluewhale.video.infrastructure.persistence.query.ContentTagRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 内容-标签关系 Mapper（复合主键表，使用注解 SQL）。
 *
 * <p>{@code tenant_id} 由 starter 的 {@code TenantLineInnerInterceptor} 自动追加，
 * 此处不显式写租户列。</p>
 *
 * @author BlueWhale
 */
public interface ContentTagMapper {

    /**
     * 新增一条内容标签关系。
     *
     * @param contentId 内容 ID
     * @param tagId 标签 ID
     * @return 影响行数
     */
    @Insert("INSERT INTO vd_content_tag (content_id, tag_id) VALUES (#{contentId}, #{tagId})")
    int insertOne(@Param("contentId") Long contentId, @Param("tagId") Long tagId);

    /**
     * 删除内容的全部标签关系。
     *
     * @param contentId 内容 ID
     * @return 影响行数
     */
    @Delete("DELETE FROM vd_content_tag WHERE content_id = #{contentId}")
    int deleteByContentId(@Param("contentId") Long contentId);

    /**
     * 查询内容的标签 ID。
     *
     * @param contentId 内容 ID
     * @return 标签 ID 列表
     */
    @Select("SELECT tag_id FROM vd_content_tag WHERE content_id = #{contentId}")
    List<Long> selectTagIdsByContentId(@Param("contentId") Long contentId);

    /**
     * 按标签查询内容 ID。
     *
     * @param tagId 标签 ID
     * @return 内容 ID 列表
     */
    @Select("SELECT content_id FROM vd_content_tag WHERE tag_id = #{tagId}")
    List<Long> selectContentIdsByTagId(@Param("tagId") Long tagId);

    /**
     * 批量查询内容标签关系。
     *
     * @param contentIds 内容 ID 集合
     * @return 关系行列表
     */
    @Select("<script>SELECT content_id AS contentId, tag_id AS tagId FROM vd_content_tag "
            + "WHERE content_id IN <foreach collection='contentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<ContentTagRow> selectRowsByContentIds(@Param("contentIds") Collection<Long> contentIds);
}
