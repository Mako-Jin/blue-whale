package com.bluewhale.doc.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageStar;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 页面收藏 Mapper。
 *
 * <p>取消收藏为逻辑删除，重新收藏必须复用原行以避开唯一键。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface DocPageStarMapper extends BaseMapper<DocPageStar> {

    /**
     * 查询收藏行（包含逻辑删除）。
     *
     * @param tenantId 租户ID
     * @param userId   用户ID
     * @param pageId   页面ID
     * @return 收藏行；不存在返回 {@code null}
     */
    @Select("""
            SELECT * FROM doc_page_star
            WHERE tenant_id = #{tenantId} AND user_id = #{userId} AND page_id = #{pageId}
            LIMIT 1
            """)
    DocPageStar selectAny(@Param("tenantId") Long tenantId,
                          @Param("userId") Long userId,
                          @Param("pageId") Long pageId);

    /**
     * 复用已取消收藏的行。
     *
     * @param tenantId 租户ID
     * @param id       收藏行ID
     * @return 受影响行数
     */
    @Update("UPDATE doc_page_star SET deleted = 0, update_time = NOW() WHERE tenant_id = #{tenantId} AND id = #{id}")
    int reactivate(@Param("tenantId") Long tenantId, @Param("id") Long id);

    /**
     * 取消收藏（逻辑删除）。
     *
     * @param tenantId 租户ID
     * @param id       收藏行ID
     * @return 受影响行数
     */
    @Update("UPDATE doc_page_star SET deleted = 1, update_time = NOW() WHERE tenant_id = #{tenantId} AND id = #{id}")
    int deactivate(@Param("tenantId") Long tenantId, @Param("id") Long id);

    /**
     * 彻底删除页面收藏记录，供回收站物理清除。
     *
     * @param tenantId 租户ID
     * @param pageIds  页面ID集合
     * @return 删除行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("""
            <script>
            DELETE FROM doc_page_star
            WHERE tenant_id = #{tenantId} AND page_id IN
            <foreach collection="pageIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int deleteByPageIds(@Param("tenantId") Long tenantId, @Param("pageIds") List<Long> pageIds);
}
