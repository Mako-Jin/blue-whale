package com.bluewhale.forum.service;

import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.dto.HotItemVO;

import java.util.List;

/**
 * 热榜服务：24h / 7d 双档窗口打分 + 快照读写。
 *
 * <p>打分口径（规格 F-06-05）：回复×3 + 投花×5 + 浏览×0.2 − 处置×5 + 精华加成；
 * 24h 档再乘小时衰减 {@code EXP(-发帖小时数/τ)}——新帖才算「正在热」，老帖在 24h 榜上
 * 天然该让位；7d 档不衰减（7 天窗口本身已是长周期口径）。</p>
 *
 * @author BlueWhale
 */
public interface HotService {

    /** 窗口：24 小时档 */
    String WINDOW_24H = "24h";

    /** 窗口：7 天档 */
    String WINDOW_7D = "7d";

    /**
     * 读取热榜快照（按窗口与当前用户可见版块过滤）。
     *
     * <p>快照为空时用实时打分的头部数据兜底，避免「刚部署完热榜空白」。</p>
     *
     * @param window 窗口（24h / 7d，非法值按 7d）
     * @param limit 条数（为空或越界时取配置上限）
     * @param actor 当前操作者
     * @return 热榜列表
     */
    List<HotItemVO> list(String window, Integer limit, ForumActor actor);

    /**
     * 刷新当前租户热榜快照（24h 与 7d 两档各整表覆盖一次）。
     *
     * @return 写入总条数（两档合计）
     */
    int refresh();

    /**
     * 刷新所有有帖子的租户（定时任务入口：定时线程没有租户上下文）。
     *
     * @return 写入总条数
     */
    int refreshAllTenants();
}
