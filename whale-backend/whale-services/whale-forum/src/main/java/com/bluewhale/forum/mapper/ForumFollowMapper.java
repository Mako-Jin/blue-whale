package com.bluewhale.forum.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.forum.entity.ForumFollow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 关注关系 Mapper。
 *
 * <p>关注用 {@code INSERT IGNORE} 交给唯一键判重，不做「先查再插」：
 * 并发下先查再插必漏判，而且按钮连点本来就不该报错。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface ForumFollowMapper extends BaseMapper<ForumFollow> {

    /**
     * 幂等写入一条关注关系。
     *
     * @param follow 关注关系
     * @return 实际写入行数（重复关注为 0）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Insert("""
            INSERT IGNORE INTO fm_follow (id, tenant_id, user_id, target_type, target_id, create_time)
            VALUES (#{id}, #{tenantId}, #{userId}, #{targetType}, #{targetId}, #{createTime})
            """)
    int insertIgnore(ForumFollow follow);

    /**
     * 查询新帖需要通知的关注者：关注作者或关注版块的用户并集，排除作者本人。
     *
     * <p>一条 SQL 取并集而不是两次查询后内存合并：同样的人既关注作者又关注版块时，
     * 内存合并要自己判重，且两次查询的 limit 会各自生效，总数不可控。</p>
     *
     * @param tenantId 租户ID
     * @param authorId 作者ID
     * @param boardId 版块ID
     * @param limit 单帖通知上限
     * @return 接收人ID列表
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT DISTINCT f.user_id
            FROM fm_follow f
            WHERE f.tenant_id = #{tenantId}
              AND ((f.target_type = 'USER' AND f.target_id = #{authorId})
                OR (f.target_type = 'BOARD' AND f.target_id = #{boardId}))
              AND f.user_id <> #{authorId}
            ORDER BY f.user_id
            LIMIT #{limit}
            """)
    List<Long> selectFollowerIds(@Param("tenantId") Long tenantId,
                                 @Param("authorId") Long authorId,
                                 @Param("boardId") Long boardId,
                                 @Param("limit") int limit);

    /**
     * 只取版块关注者（匿名帖用：作者关注者不通知，否则等于把匿名帖关联回作者）。
     *
     * @param tenantId 租户ID
     * @param boardId 版块ID
     * @param limit 单帖通知上限
     * @return 接收人ID列表
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT DISTINCT f.user_id
            FROM fm_follow f
            WHERE f.tenant_id = #{tenantId}
              AND f.target_type = 'BOARD'
              AND f.target_id = #{boardId}
            ORDER BY f.user_id
            LIMIT #{limit}
            """)
    List<Long> selectBoardFollowerIds(@Param("tenantId") Long tenantId,
                                      @Param("boardId") Long boardId,
                                      @Param("limit") int limit);
}
