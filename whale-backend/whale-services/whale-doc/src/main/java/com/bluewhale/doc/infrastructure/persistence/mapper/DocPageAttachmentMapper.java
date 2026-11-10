package com.bluewhale.doc.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageAttachment;
import com.bluewhale.doc.infrastructure.persistence.query.DocOrphanAttachmentRow;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 页面附件引用 Mapper。
 *
 * <p>解除引用为逻辑删除，同一文件再次挂到同一页面时复用原行。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface DocPageAttachmentMapper extends BaseMapper<DocPageAttachment> {

    /**
     * 查询附件引用行（包含逻辑删除）。
     *
     * @param tenantId 租户ID
     * @param pageId   页面ID
     * @param fileId   文件ID
     * @return 附件行；不存在返回 {@code null}
     */
    @Select("""
            SELECT * FROM doc_page_attachment
            WHERE tenant_id = #{tenantId} AND page_id = #{pageId} AND file_id = #{fileId}
            LIMIT 1
            """)
    DocPageAttachment selectAny(@Param("tenantId") Long tenantId,
                                @Param("pageId") Long pageId,
                                @Param("fileId") Long fileId);

    /**
     * 复用已解除引用的附件行并刷新元数据。
     *
     * @param tenantId     租户ID
     * @param id           附件行ID
     * @param originalName 原始文件名
     * @param contentType  MIME 类型
     * @param fileSize     文件字节数
     * @param usageType    用途
     * @return 受影响行数
     */
    @Update("""
            UPDATE doc_page_attachment
            SET deleted = 0, original_name = #{originalName}, content_type = #{contentType},
                file_size = #{fileSize}, usage_type = #{usageType}, update_time = NOW()
            WHERE tenant_id = #{tenantId} AND id = #{id}
            """)
    int reactivate(@Param("tenantId") Long tenantId,
                   @Param("id") Long id,
                   @Param("originalName") String originalName,
                   @Param("contentType") String contentType,
                   @Param("fileSize") Long fileSize,
                   @Param("usageType") String usageType);

    /**
     * 彻底删除页面附件引用（不删除 whale-file 中的文件本体）。
     *
     * @param tenantId 租户ID
     * @param pageIds  页面ID集合
     * @return 删除行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("""
            <script>
            DELETE FROM doc_page_attachment
            WHERE tenant_id = #{tenantId} AND page_id IN
            <foreach collection="pageIds" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int deleteByPageIds(@Param("tenantId") Long tenantId, @Param("pageIds") List<Long> pageIds);

    /**
     * 跨租户查询页面已不存在的附件引用（M5 孤儿清理）。
     *
     * <p>{@code update_time} 早于阈值的引用才会进入清理范围，避免与正在提交的
     * 页面清除事务产生竞争；查询不删除 whale-file 中的文件本体。</p>
     *
     * @param cutoff 引用更新时间阈值
     * @param limit  单轮最大数量
     * @return 无主附件引用行（含租户ID）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT a.tenant_id, a.id AS attachment_id, a.page_id
            FROM doc_page_attachment a
            LEFT JOIN doc_page p ON p.id = a.page_id AND p.tenant_id = a.tenant_id
            WHERE p.id IS NULL AND a.update_time < #{cutoff}
            ORDER BY a.update_time ASC
            LIMIT #{limit}
            """)
    List<DocOrphanAttachmentRow> selectOrphanAttachments(@Param("cutoff") LocalDateTime cutoff,
                                                         @Param("limit") int limit);

    /**
     * 按租户批量物理删除无主附件引用。
     *
     * @param tenantId 租户ID
     * @param ids      附件引用行ID集合
     * @return 删除行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("""
            <script>
            DELETE FROM doc_page_attachment
            WHERE tenant_id = #{tenantId} AND id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    int deleteOrphanByIds(@Param("tenantId") Long tenantId, @Param("ids") List<Long> ids);
}
