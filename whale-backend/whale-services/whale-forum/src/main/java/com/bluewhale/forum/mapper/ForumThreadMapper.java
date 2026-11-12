package com.bluewhale.forum.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.bluewhale.forum.dto.HotCandidate;
import com.bluewhale.forum.dto.IndexStat;
import com.bluewhale.forum.dto.ThreadCountDrift;
import com.bluewhale.forum.entity.ForumThread;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

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
     * 回复计数 -1（删楼层/驳回待审楼层时用，下限 0）。
     *
     * @param threadId 帖子ID
     * @return 影响行数
     */
    @Update("UPDATE fm_thread SET reply_count = GREATEST(reply_count - 1, 0), update_time = NOW() "
            + "WHERE id = #{threadId} AND deleted = 0")
    int decreaseReplyCount(@Param("threadId") Long threadId);

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
     * 浏览数 +1（24h 去重由 {@code fm_thread_view} 行判定，见 {@link ForumThreadViewMapper}）。
     *
     * @param threadId 帖子ID
     * @return 影响行数
     */
    @Update("UPDATE fm_thread SET view_count = view_count + 1 WHERE id = #{threadId} AND deleted = 0")
    int increaseViewCount(@Param("threadId") Long threadId);

    /**
     * 投花数增减（{@code delta} 为 ±1，下限 0）。
     *
     * <p>不做「读出来改再写回」：并发投花会互相覆盖计数，出现「点了 10 次只加了 3」。</p>
     *
     * @param threadId 帖子ID
     * @param delta 增量（+1 / -1）
     * @return 影响行数
     */
    @Update("UPDATE fm_thread SET like_count = GREATEST(COALESCE(like_count, 0) + #{delta}, 0), "
            + "update_time = NOW() WHERE id = #{threadId} AND deleted = 0")
    int changeLikeCount(@Param("threadId") Long threadId, @Param("delta") int delta);

    /**
     * 收藏数增减（{@code delta} 为 ±1，下限 0）。
     *
     * @param threadId 帖子ID
     * @param delta 增量（+1 / -1）
     * @return 影响行数
     */
    @Update("UPDATE fm_thread SET collect_count = GREATEST(COALESCE(collect_count, 0) + #{delta}, 0), "
            + "update_time = NOW() WHERE id = #{threadId} AND deleted = 0")
    int changeCollectCount(@Param("threadId") Long threadId, @Param("delta") int delta);

    /**
     * 热榜候选：窗口内已发布帖子按热度分排序（支持 24h / 7d 双档）。
     *
     * <p>打分 = (回复×权重 + 投花×权重 + 浏览×权重 + 精华×加成 − 处置扣分×权重)
     * × 衰减系数。处置扣分取窗口内 {@code REMOVE} / {@code AUDIT_REJECT} 的轨迹条数——
     * 被删过的帖子即使后来恢复，热度也不该和干净帖子一样，否则「先违规冲量再申诉」
     * 就是刷榜捷径。</p>
     *
     * <p>精华加成（P1）：题金榜的帖子额外 +{@code essenceBonus}，好内容值得被看见。
     * 衰减只在 24h 档启用（{@code decayHours} &gt; 0 时按
     * {@code EXP(-发帖小时数/decayHours)} 衰减），7d 档传 0 即不衰减。</p>
     *
     * <p>租户条件在本 SQL 内显式写出，并关掉租户插件：插件对带子查询的聚合语句
     * 追加条件容易落到错误的表上，这里宁可自己写死。</p>
     *
     * @param tenantId 租户ID
     * @param from 窗口起点
     * @param replyWeight 回复权重
     * @param likeWeight 投花权重
     * @param viewWeight 浏览权重
     * @param essenceBonus 精华加成分
     * @param penaltyWeight 处置扣分权重
     * @param decayHours 衰减常数 τ（小时，0 = 不衰减）
     * @param limit 取前 N 条
     * @return 候选列表（含 score）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("<script>"
            + "SELECT t.id, t.board_id, t.title, t.summary, t.author_id, t.anonymous, "
            + "t.reply_count, t.like_count, t.view_count, "
            + "(t.reply_count * #{replyWeight} + t.like_count * #{likeWeight} "
            + " + t.view_count * #{viewWeight} + t.essence * #{essenceBonus} "
            + " - COALESCE(p.penalty, 0) * #{penaltyWeight}) "
            + " * <choose>"
            + "   <when test='decayHours > 0'>"
            + "     EXP(-GREATEST(TIMESTAMPDIFF(HOUR, t.publish_time, NOW()), 0) / #{decayHours})"
            + "   </when>"
            + "   <otherwise>1</otherwise>"
            + " </choose> AS score "
            + "FROM fm_thread t "
            + "LEFT JOIN (SELECT target_id, COUNT(*) AS penalty FROM fm_moderation_record "
            + "  WHERE tenant_id = #{tenantId} AND target_type = 'THREAD' "
            + "    AND action IN ('REMOVE', 'AUDIT_REJECT') AND create_time >= #{from} "
            + "  GROUP BY target_id) p ON p.target_id = t.id "
            + "WHERE t.tenant_id = #{tenantId} AND t.deleted = 0 "
            + "  AND t.status IN ('PUBLISHED', 'SOLVED', 'CLOSED') "
            + "  AND t.publish_time IS NOT NULL AND t.publish_time >= #{from} "
            + "ORDER BY score DESC, t.publish_time DESC LIMIT #{limit}"
            + "</script>")
    List<HotCandidate> selectHotCandidates(@Param("tenantId") Long tenantId,
                                           @Param("from") LocalDateTime from,
                                           @Param("replyWeight") double replyWeight,
                                           @Param("likeWeight") double likeWeight,
                                           @Param("viewWeight") double viewWeight,
                                           @Param("essenceBonus") double essenceBonus,
                                           @Param("penaltyWeight") double penaltyWeight,
                                           @Param("decayHours") int decayHours,
                                           @Param("limit") int limit);

    /**
     * 我的收藏分页（帖子 × 收藏记录联查，按收藏时间倒序）。
     *
     * <p>不用「先查互动表拿 ID 再查帖子」：两步之间帖子可能被删，
     * 会出现 total 与实际条数对不上，前端翻到最后一页是空白。</p>
     *
     * @param page 分页参数
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @param reactionType 互动类型（COLLECT）
     * @return 帖子分页
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT t.* FROM fm_thread t INNER JOIN fm_reaction r ON r.target_id = t.id "
            + "AND r.target_type = 'THREAD' AND r.tenant_id = #{tenantId} "
            + "AND r.user_id = #{userId} AND r.reaction_type = #{reactionType} "
            + "WHERE t.tenant_id = #{tenantId} AND t.deleted = 0 "
            + "AND t.status IN ('PUBLISHED', 'SOLVED', 'CLOSED') "
            + "ORDER BY r.create_time DESC")
    IPage<ForumThread> selectCollectedPage(IPage<ForumThread> page,
                                           @Param("tenantId") Long tenantId,
                                           @Param("userId") Long userId,
                                           @Param("reactionType") String reactionType);

    /**
     * 有帖子的租户列表（热榜定时刷新用：定时线程没有租户上下文，必须自己遍历）。
     *
     * @return 租户ID列表
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT DISTINCT tenant_id FROM fm_thread WHERE deleted = 0")
    List<Long> selectActiveTenantIds();

    /**
     * 检索命中行（ngram 全文索引，标题命中优先）。
     *
     * <p>排序分两段：先按「标题整串命中」降序，再按全文相关度降序——ngram 的相关度
     * 把标题与正文的词频混在一起算，短标题帖容易被长正文帖压下去；用户在列表页
     * 搜标题词时，期望的是标题命中的排前面。</p>
     *
     * <p>租户与可见状态写死在 SQL 内并关掉租户插件：带 {@code MATCH} 的语句里
     * 插件追加条件的位置依赖解析结果，宁可自己写。</p>
     *
     * @param tenantId 租户ID
     * @param boardIds 可见版块ID（不可为空，空集合由调用方提前返回）
     * @param keyword 关键词
     * @param essenceOnly 只看精华
     * @param useFulltext 是否走全文索引（关键词长度达到 ngram_token_size 时为 true）
     * @param offset 偏移量
     * @param size 每页条数
     * @return 命中行
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            <script>
            SELECT t.* FROM fm_thread t
            WHERE t.tenant_id = #{tenantId} AND t.deleted = 0
              AND t.status IN ('PUBLISHED', 'SOLVED', 'CLOSED')
              AND t.board_id IN
              <foreach collection="boardIds" item="bid" open="(" separator="," close=")">#{bid}</foreach>
              <if test="essenceOnly"> AND t.essence = 1 </if>
              AND (
                <choose>
                  <when test="useFulltext">
                    MATCH(t.title, t.content_text) AGAINST (#{keyword} IN NATURAL LANGUAGE MODE)
                  </when>
                  <otherwise>
                    (t.title LIKE CONCAT('%', #{keyword}, '%')
                     OR t.content_text LIKE CONCAT('%', #{keyword}, '%'))
                  </otherwise>
                </choose>
              )
            ORDER BY (t.title LIKE CONCAT('%', #{keyword}, '%')) DESC,
              <if test="useFulltext">
                MATCH(t.title, t.content_text) AGAINST (#{keyword} IN NATURAL LANGUAGE MODE) DESC,
              </if>
              t.publish_time DESC, t.id DESC
            LIMIT #{offset}, #{size}
            </script>
            """)
    List<ForumThread> selectSearchRows(@Param("tenantId") Long tenantId,
                                       @Param("boardIds") List<Long> boardIds,
                                       @Param("keyword") String keyword,
                                       @Param("essenceOnly") boolean essenceOnly,
                                       @Param("useFulltext") boolean useFulltext,
                                       @Param("offset") long offset,
                                       @Param("size") long size);

    /**
     * 检索命中总数（与 {@link #selectSearchRows} 同谓词，改一处必须改两处）。
     *
     * @param tenantId 租户ID
     * @param boardIds 可见版块ID
     * @param keyword 关键词
     * @param essenceOnly 只看精华
     * @param useFulltext 是否走全文索引
     * @return 命中总数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            <script>
            SELECT COUNT(*) FROM fm_thread t
            WHERE t.tenant_id = #{tenantId} AND t.deleted = 0
              AND t.status IN ('PUBLISHED', 'SOLVED', 'CLOSED')
              AND t.board_id IN
              <foreach collection="boardIds" item="bid" open="(" separator="," close=")">#{bid}</foreach>
              <if test="essenceOnly"> AND t.essence = 1 </if>
              AND (
                <choose>
                  <when test="useFulltext">
                    MATCH(t.title, t.content_text) AGAINST (#{keyword} IN NATURAL LANGUAGE MODE)
                  </when>
                  <otherwise>
                    (t.title LIKE CONCAT('%', #{keyword}, '%')
                     OR t.content_text LIKE CONCAT('%', #{keyword}, '%'))
                  </otherwise>
                </choose>
              )
            </script>
            """)
    long countSearchRows(@Param("tenantId") Long tenantId,
                         @Param("boardIds") List<Long> boardIds,
                         @Param("keyword") String keyword,
                         @Param("essenceOnly") boolean essenceOnly,
                         @Param("useFulltext") boolean useFulltext);

    /**
     * 待建索引的帖子（{@code content_text} 未回填），供检索重建分批处理。
     *
     * @param tenantId 租户ID
     * @param limit 单批条数
     * @return 帖子（只取 id 与 content）
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT id, content FROM fm_thread "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0 AND content_text IS NULL "
            + "ORDER BY id LIMIT #{limit}")
    List<ForumThread> selectPendingIndexRows(@Param("tenantId") Long tenantId,
                                             @Param("limit") int limit);

    /**
     * 写入检索纯文本。
     *
     * @param id 帖子ID
     * @param contentText 纯文本
     * @return 影响行数
     */
    @Update("UPDATE fm_thread SET content_text = #{contentText} WHERE id = #{id}")
    int updateContentText(@Param("id") Long id, @Param("contentText") String contentText);

    /**
     * 索引覆盖情况（总数 / 已回填数）。
     *
     * @param tenantId 租户ID
     * @return 统计行
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT COUNT(*) AS total, "
            + "COALESCE(SUM(CASE WHEN content_text IS NOT NULL THEN 1 ELSE 0 END), 0) AS indexed "
            + "FROM fm_thread WHERE tenant_id = #{tenantId} AND deleted = 0")
    IndexStat selectIndexStat(@Param("tenantId") Long tenantId);

    /**
     * 读取 ngram 分词长度（MySQL 内置变量，MariaDB 等无此变量时由调用方兜底为 2）。
     *
     * @return 分词长度
     */
    @Select("SELECT @@ngram_token_size")
    Integer selectNgramTokenSize();

    /**
     * 计数漂移行：展示值与明细 {@code COUNT} 不一致的帖子。
     *
     * <p>只核对可从明细重算的三项：{@code reply_count}（已发布楼层）、
     * {@code like_count} / {@code collect_count}（互动表有效行）。浏览数没有明细可重算——
     * {@code fm_thread_view} 是去重窗口的判定行、不是逐次浏览流水，硬对账会把
     * 「同一人 24h 内看了 5 次」算成 1，越对越错，故不纳入。</p>
     *
     * @param tenantId 租户ID
     * @param limit 单批条数
     * @return 漂移行
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("""
            SELECT t.id AS thread_id, t.reply_count, t.like_count, t.collect_count,
                   (SELECT COUNT(*) FROM fm_post p
                     WHERE p.thread_id = t.id AND p.deleted = 0 AND p.floor IS NOT NULL
                       AND p.status = 'PUBLISHED') AS actual_reply,
                   (SELECT COUNT(*) FROM fm_reaction r
                     WHERE r.tenant_id = t.tenant_id AND r.target_type = 'THREAD'
                       AND r.target_id = t.id AND r.reaction_type = 'LIKE') AS actual_like,
                   (SELECT COUNT(*) FROM fm_reaction r
                     WHERE r.tenant_id = t.tenant_id AND r.target_type = 'THREAD'
                       AND r.target_id = t.id AND r.reaction_type = 'COLLECT') AS actual_collect
            FROM fm_thread t
            WHERE t.tenant_id = #{tenantId} AND t.deleted = 0
            HAVING t.reply_count <> actual_reply
                OR t.like_count <> actual_like
                OR t.collect_count <> actual_collect
            ORDER BY t.id
            LIMIT #{limit}
            """)
    List<ThreadCountDrift> selectCountDrift(@Param("tenantId") Long tenantId,
                                            @Param("limit") int limit);

    /**
     * 按真实值修正三项计数。
     *
     * @param threadId 帖子ID
     * @param tenantId 租户ID
     * @param replyCount 真实回复数
     * @param likeCount 真实投花数
     * @param collectCount 真实收藏数
     * @return 影响行数
     */
    @Update("UPDATE fm_thread SET reply_count = #{replyCount}, like_count = #{likeCount}, "
            + "collect_count = #{collectCount}, update_time = NOW() "
            + "WHERE id = #{threadId} AND tenant_id = #{tenantId} AND deleted = 0")
    int fixCounts(@Param("threadId") Long threadId,
                  @Param("tenantId") Long tenantId,
                  @Param("replyCount") int replyCount,
                  @Param("likeCount") int likeCount,
                  @Param("collectCount") int collectCount);
}
