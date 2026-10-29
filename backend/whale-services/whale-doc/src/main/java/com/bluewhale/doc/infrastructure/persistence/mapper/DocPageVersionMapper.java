package com.bluewhale.doc.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageVersion;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 页面版本 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface DocPageVersionMapper extends BaseMapper<DocPageVersion> {

    /**
     * 彻底删除页面版本快照（回收站物理清除）。
     *
     * @param tenantId 租户ID
     * @param pageIds  页面ID集合
     * @return 删除行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("""
            <script>
            DELETE FROM doc_page_version
            WHERE tenant_id = #{tenantId} AND page_id IN
            <foreach collection="pageIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int deleteByPageIds(@Param("tenantId") Long tenantId, @Param("pageIds") List<Long> pageIds);
}
