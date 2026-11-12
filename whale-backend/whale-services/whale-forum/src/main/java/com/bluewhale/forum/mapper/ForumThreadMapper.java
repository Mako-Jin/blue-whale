package com.bluewhale.forum.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.forum.entity.ForumThread;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 帖子 Mapper。
 *
 * <p>楼层号走单语句原子分配（{@code LAST_INSERT_ID(expr)}），计数走 {@code SET x = x + 1}，
 * 都不做「先读后写」：先读后写在 50 并发下必然重号，重号又被唯一键挡住，
 * 最终表现为「回帖偶发失败」。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface ForumThreadMapper extends BaseMapper<ForumThread> {

    /**
     * 原子分配一个楼层号（无需重试）。
     *
     * <p><b>为什么不用「先读后 CAS」</b>：CAS 形态下 N 个并发请求每轮只有 1 个能成功，
     * 其余全部返回 0 进入重试，重试轮数随并发线性增长。实测 20 并发、10 次重试仍有 2 条失败，
     * 验收点「并发 50 条无重号无跳号」必然过不了。</p>
     *
     * <p>这里用 MySQL 的 {@code LAST_INSERT_ID(expr)} 把「取值 + 自增」压成一条语句：
     * 行锁串行化，每个请求拿到的都是互不相同的连续号，一次成功、零重试。
     * 取回的值是本连接上最后一次 {@code LAST_INSERT_ID(expr)} 的结果，
     * 必须在**同一事务**内调用 {@link #lastAllocatedFloor()} 读取。</p>
     *
     * @param threadId 帖子ID
     * @return 影响行数（0 表示帖子不存在或已删）
     */
    @Update("UPDATE fm_thread SET next_floor = LAST_INSERT_ID(COALESCE(next_floor, 1)) + 1, "
            + "update_time = NOW() WHERE id = #{threadId} AND deleted = 0")
    int allocateFloor(@Param("threadId") Long threadId);

    /**
     * 读取本连接上一次 {@link #allocateFloor(Long)} 分配的楼层号。
     *
     * @return 已分配的楼层号
     */
    @Select("SELECT LAST_INSERT_ID()")
    long lastAllocatedFloor();

    /**
     * 按现有楼层重算 {@code next_floor}（自愈：人工改数或历史数据漂移后唯一键会持续撞）。
     *
     * @param threadId 帖子ID
     * @return 影响行数
     */
    @Update("UPDATE fm_thread t SET t.next_floor = "
            + "(SELECT COALESCE(MAX(p.floor), 0) + 1 FROM fm_post p "
            + " WHERE p.thread_id = t.id AND p.deleted = 0 AND p.floor IS NOT NULL), "
            + "t.update_time = NOW() WHERE t.id = #{threadId}")
    int syncNextFloor(@Param("threadId") Long threadId);

    /**
     * 回复计数 +1，并刷新最后回复信息（DB 为唯一事实源）。
     *
     * @param threadId 帖子ID
     * @param userId 回复人
     * @param repliedAt 回复时间
     * @return 影响行数
     */
    @Update("UPDATE fm_thread SET reply_count = reply_count + 1, last_reply_time = #{repliedAt}, "
            + "last_reply_user_id = #{userId}, update_time = NOW() "
            + "WHERE id = #{threadId} AND deleted = 0")
    int increaseReplyCount(@Param("threadId") Long threadId,
                           @Param("userId") Long userId,
                           @Param("repliedAt") LocalDateTime repliedAt);

    /**
     * 软删时改写帖子编号以释放唯一键（{@code uk_fm_thread_no}）。
     *
     * @param id 帖子ID
     * @param newNo 改写后的编号（{@code 原编号#D{id}}）
     * @return 影响行数
     */
    @Update("UPDATE fm_thread SET thread_no = #{newNo} WHERE id = #{id}")
    int renameNoForDelete(@Param("id") Long id, @Param("newNo") String newNo);

    /**
     * 浏览数 +1（不做 24h 去重，去重需 Redis，M3 接）。
     *
     * @param threadId 帖子ID
     * @return 影响行数
     */
    @Update("UPDATE fm_thread SET view_count = view_count + 1 WHERE id = #{threadId} AND deleted = 0")
    int increaseViewCount(@Param("threadId") Long threadId);
}
