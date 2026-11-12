package com.bluewhale.forum.service;

import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.dto.HotItemVO;

import java.util.List;

/**
 * 热榜服务：7 天滚动窗口打分 + 快照读写。
 *
 * @author BlueWhale
 */
public interface HotService {

    /**
     * 读取热榜快照（按当前用户可见版块过滤）。
     *
     * <p>快照为空时用实时打分的头部数据兜底，避免「刚部署完热榜空白」。</p>
     *
     * @param limit 条数（为空或越界时取配置上限）
     * @param actor 当前操作者
     * @return 热榜列表
     */
    List<HotItemVO> list(Integer limit, ForumActor actor);

    /**
     * 刷新当前租户热榜快照（整表覆盖）。
     *
     * @return 写入条数
     */
    int refresh();

    /**
     * 刷新所有有帖子的租户（定时任务入口：定时线程没有租户上下文）。
     *
     * @return 写入总条数
     */
    int refreshAllTenants();
}
