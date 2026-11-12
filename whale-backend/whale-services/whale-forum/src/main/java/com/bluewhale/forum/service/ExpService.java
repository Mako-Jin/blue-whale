package com.bluewhale.forum.service;

import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.dto.ExpSummaryVO;

/**
 * 论坛经验账本（F-07-04）。
 *
 * <p>口径：论坛经验与可兑换积分是<b>两套账</b>——可兑换积分的账本在
 * {@code whale-mall}，本服务只记论坛经验，不出域、不与商城对账。
 * 规则：发帖 +5、被投花 +2、题金榜 +200、7d 金榜第一名 +1000，
 * <b>匿名帖一律不计</b>（匿名刷分零成本，等于白送）。</p>
 *
 * @author BlueWhale
 */
public interface ExpService {

    /** 事件：发帖 */
    String EVENT_THREAD_PUBLISH = "THREAD_PUBLISH";

    /** 事件：被投花 */
    String EVENT_LIKE_GIVEN = "LIKE_GIVEN";

    /** 事件：题金榜 */
    String EVENT_ESSENCE_AWARD = "ESSENCE_AWARD";

    /** 事件：7d 金榜第一名 */
    String EVENT_HOT_TOP1 = "HOT_TOP1";

    /**
     * 记一笔经验（幂等：同租户同人同事件同帖只发一次）。
     *
     * <p>所有调用点都把经验当<b>附属能力</b>：这里永不抛异常，
     * 记账失败只留 warn——不能让「发帖 +5」把发帖本身拖死。</p>
     *
     * @param userId 获得经验的用户ID（为空跳过）
     * @param event 事件（THREAD_PUBLISH / LIKE_GIVEN / ESSENCE_AWARD / HOT_TOP1）
     * @param amount 经验值（&le;0 跳过）
     * @param threadId 关联帖子ID（幂等锚点）
     * @return 实际落库条数（0 = 已发过 / 跳过）
     */
    int award(Long userId, String event, int amount, Long threadId);

    /**
     * 用户经验总额（个人主页 / 鲸阶用）。
     *
     * @param userId 用户ID（为空返回 0）
     * @return 累计经验
     */
    long totalOf(Long userId);

    /**
     * 我的经验（总额 + 最近流水）。
     *
     * @param actor 当前操作者
     * @return 汇总
     */
    ExpSummaryVO myExp(ForumActor actor);
}
