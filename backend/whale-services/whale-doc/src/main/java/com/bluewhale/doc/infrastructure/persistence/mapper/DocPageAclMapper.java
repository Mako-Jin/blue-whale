package com.bluewhale.doc.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageAcl;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 页面级权限 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface DocPageAclMapper extends BaseMapper<DocPageAcl> {

    /**
     * 物理删除页面全部 ACL（全量替换时使用，避免唯一键与逻辑删除冲突）。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @return 删除行数
     */
    @Delete("DELETE FROM doc_page_acl WHERE tenant_id = #{tenantId} AND page_id = #{pageId}")
    int deleteByPage(@Param("tenantId") Long tenantId, @Param("pageId") Long pageId);

    /**
     * 物理删除多个页面的 ACL（供回收站彻底清除）。
     *
     * @param tenantId 租户ID
     * @param pageIds  页面ID集合
     * @return 删除行数
     */
    @Delete("""
            <script>
            DELETE FROM doc_page_acl
            WHERE tenant_id = #{tenantId} AND page_id IN
            <foreach collection="pageIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int deleteByPageIds(@Param("tenantId") Long tenantId, @Param("pageIds") List<Long> pageIds);
}
