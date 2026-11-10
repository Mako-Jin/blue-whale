package com.bluewhale.doc.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageTag;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 页面标签关联 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface DocPageTagMapper extends BaseMapper<DocPageTag> {

    /**
     * 物理删除页面的全部标签关联（全量替换时使用，避免唯一键与逻辑删除冲突）。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @return 删除行数
     */
    @Delete("DELETE FROM doc_page_tag WHERE tenant_id = #{tenantId} AND page_id = #{pageId}")
    int deleteByPage(@Param("tenantId") Long tenantId, @Param("pageId") Long pageId);

    /**
     * 物理删除标签的全部页面关联。
     *
     * @param tenantId 租户ID
     * @param tagId    标签ID
     * @return 删除行数
     */
    @Delete("DELETE FROM doc_page_tag WHERE tenant_id = #{tenantId} AND tag_id = #{tagId}")
    int deleteByTag(@Param("tenantId") Long tenantId, @Param("tagId") Long tagId);

    /**
     * 物理删除多个页面的标签关联（供回收站彻底清除）。
     *
     * @param tenantId 租户ID
     * @param pageIds  页面ID集合
     * @return 删除行数
     */
    @Delete("""
            <script>
            DELETE FROM doc_page_tag
            WHERE tenant_id = #{tenantId} AND page_id IN
            <foreach collection="pageIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int deleteByPageIds(@Param("tenantId") Long tenantId, @Param("pageIds") List<Long> pageIds);
}
