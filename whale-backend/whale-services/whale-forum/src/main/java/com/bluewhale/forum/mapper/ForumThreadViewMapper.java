package com.bluewhale.forum.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.forum.entity.ForumThreadView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 浏览去重 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface ForumThreadViewMapper extends BaseMapper<ForumThreadView> {

    /**
     * 刷新去重行的计入时间（仅在超出窗口时才更新）。
     *
     * <p>返回 0 有两种含义：行不存在，或行存在但窗口未过。调用方据此决定
     * 「尝试插入」还是「直接放弃计数」——把判定压在一条条件更新上，
     * 不用先查后写，避免两个并发请求同时通过判定把浏览数加两次。</p>
     *
     * @param threadId 帖子ID
     * @param userId 浏览人
     * @param cutoff 窗口起点（早于此刻的行才算过期）
     * @return 影响行数（1 表示本次应计入浏览）
     */
    @Update("UPDATE fm_thread_view SET last_view_time = NOW() "
            + "WHERE thread_id = #{threadId} AND user_id = #{userId} AND last_view_time < #{cutoff}")
    int touchIfExpired(@Param("threadId") Long threadId,
                       @Param("userId") Long userId,
                       @Param("cutoff") LocalDateTime cutoff);

    /**
     * 清理过期去重行（保留 7 天，供运维脚本调用）。
     *
     * <p>忽略租户插件：清理是跨租户的运维动作，租户条件下推由调用方保证。</p>
     *
     * @param cutoff 早于此刻的行删除
     * @return 删除行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("DELETE FROM fm_thread_view WHERE last_view_time < #{cutoff}")
    int purgeBefore(@Param("cutoff") LocalDateTime cutoff);
}
