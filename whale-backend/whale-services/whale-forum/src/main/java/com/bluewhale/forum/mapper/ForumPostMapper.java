package com.bluewhale.forum.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.forum.dto.MyPostVO;
import com.bluewhale.forum.entity.ForumPost;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 楼层 Mapper。
 *
 * <p>楼层号并发保护依赖唯一键 {@code uk_fm_post_floor}：插入撞键即 {@code DuplicateKeyException}，
 * 由 service 层重试，不做 {@code MAX(floor)+1} 预计算。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface ForumPostMapper extends BaseMapper<ForumPost> {

    /**
     * 我的回复分页（联帖子取标题，排除已删帖）。
     *
     * <p>先过滤再分页：若先按 {@code fm_post} 分页再过滤已删帖，
     * 会出现「每页条数越翻越少」甚至整页空白。</p>
     *
     * @param tenantId 租户ID
     * @param authorId 作者ID
     * @param offset 偏移量
     * @param size 每页条数
     * @return 回复条目（含正文原文，服务层转摘要后置空）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT p.id AS post_id, p.thread_id, t.title AS thread_title, p.floor,
                   p.content AS content, p.like_count, p.status, p.create_time
            FROM fm_post p
            INNER JOIN fm_thread t ON t.id = p.thread_id AND t.tenant_id = p.tenant_id AND t.deleted = 0
            WHERE p.tenant_id = #{tenantId} AND p.author_id = #{authorId} AND p.deleted = 0
            ORDER BY p.create_time DESC, p.id DESC
            LIMIT #{offset}, #{size}
            """)
    List<MyPostVO> selectMyPosts(@Param("tenantId") Long tenantId,
                                 @Param("authorId") Long authorId,
                                 @Param("offset") long offset,
                                 @Param("size") long size);

    /**
     * 我的回复总数（与 {@link #selectMyPosts} 同谓词，改一处必须改两处）。
     *
     * @param tenantId 租户ID
     * @param authorId 作者ID
     * @return 总数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT COUNT(*)
            FROM fm_post p
            INNER JOIN fm_thread t ON t.id = p.thread_id AND t.tenant_id = p.tenant_id AND t.deleted = 0
            WHERE p.tenant_id = #{tenantId} AND p.author_id = #{authorId} AND p.deleted = 0
            """)
    long countMyPosts(@Param("tenantId") Long tenantId, @Param("authorId") Long authorId);
}
