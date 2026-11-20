package com.bluewhale.monitor.overview;

import com.bluewhale.common.context.RequestContextHolder;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.common.web.UserContext;
import com.bluewhale.monitor.vo.HubHallDetailVO;
import com.bluewhale.monitor.vo.HubHallRowVO;
import com.bluewhale.overview.OverviewContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * 数枢殿总控的屏风页接口：逐殿列表与单殿详情。
 *
 * <p>路径挂在概览端点下面（{@code /monitor/overview/halls}）而不是另起一段：
 * 它们与 {@code GET /monitor/overview} 用的是<b>同一次聚合</b>，
 * 挂在同一个前缀下，读代码的人一眼就知道"表格和牌上的数是同一个来源"。</p>
 *
 * <p><b>分页与筛选为什么在服务端做。</b>屏风页是通用渲染器，它只负责把
 * {@code {records,total}} 摊成表；若这里返回全量再让前端切页，
 * 就会出现"表里只有 6 行、却写着第 1/2 页"的假分页。筛选同理：
 * 关键字要在服务端生效，否则输入框看着能用、实际没过滤。</p>
 *
 * <p><b>与概览端点一样不设 {@code @RequirePermission}</b>：总控的裁剪在每座殿内部完成，
 * 调用者没有的那座殿只会得到 {@code denied} 行（不带任何数值），
 * 这里再加一道总控权限，只会把"没权限"和"没这条接口"混成同一个 403。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("${whale.overview.base-path:/monitor/overview}")
@RequiredArgsConstructor
public class HubOverviewController {

    /** 每页上限：屏风页一屏放不下更多，也没人会去翻第 9 页 */
    private static final int MAX_PAGE_SIZE = 50;

    /** 聚合器 */
    private final HubOverviewAggregator aggregator;

    /**
     * 逐殿列表（屏风页的表格）
     *
     * @param keyword 关键字（殿名或节点 key，忽略大小写），可空
     * @param state 状态筛选（live/stale/degraded/denied），可空
     * @param current 页码，从 1 开始
     * @param size 每页条数
     * @param request 当前请求（取身份与权限码头）
     * @return 分页后的每殿一行
     */
    @GetMapping("/halls")
    public Result<PageResult<HubHallRowVO>> halls(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "current", defaultValue = "1") long current,
            @RequestParam(value = "size", defaultValue = "6") long size,
            HttpServletRequest request) {
        List<HubHallRowVO> matched = aggregator.aggregate(contextOf(request)).rows().stream()
                .filter(row -> matchesKeyword(row, keyword))
                .filter(row -> matchesState(row, state))
                .toList();
        long page = Math.max(1L, current);
        long pageSize = Math.max(1L, Math.min(MAX_PAGE_SIZE, size));
        int from = (int) Math.min(matched.size(), (page - 1) * pageSize);
        int to = (int) Math.min(matched.size(), from + pageSize);
        return Result.ok(PageResult.of(matched.subList(from, to), matched.size(), page, pageSize));
    }

    /**
     * 单殿详情（屏风页点行之后）
     *
     * @param nodeKey 节点 key
     * @param request 当前请求
     * @return 该殿详情
     * @throws BizException 节点不在册
     */
    @GetMapping("/halls/{nodeKey}")
    public Result<HubHallDetailVO> hall(@PathVariable String nodeKey, HttpServletRequest request) {
        HubHallDetailVO detail = aggregator.aggregate(contextOf(request)).detail(nodeKey);
        if (detail == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "未知殿：" + nodeKey);
        }
        return Result.ok(detail);
    }

    /**
     * 关键字匹配：殿名或节点 key 包含即命中（忽略大小写）
     *
     * @param row 行
     * @param keyword 关键字，可空
     * @return 命中返回 {@code true}
     */
    private boolean matchesKeyword(HubHallRowVO row, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String needle = keyword.trim().toLowerCase(Locale.ROOT);
        return row.name().toLowerCase(Locale.ROOT).contains(needle)
                || row.nodeKey().toLowerCase(Locale.ROOT).contains(needle);
    }

    /**
     * 状态匹配
     *
     * @param row 行
     * @param state 目标状态，可空
     * @return 命中返回 {@code true}
     */
    private boolean matchesState(HubHallRowVO row, String state) {
        if (state == null || state.isBlank()) {
            return true;
        }
        return state.trim().equalsIgnoreCase(row.state());
    }

    /**
     * 解析调用者上下文（与概览端点同一口径）
     *
     * @param request 当前请求
     * @return 上下文
     */
    private OverviewContext contextOf(HttpServletRequest request) {
        return OverviewContext.of(UserContext.findUserId(request), RequestContextHolder.getTenantId(),
                UserContext.permissionCodes(request));
    }
}
