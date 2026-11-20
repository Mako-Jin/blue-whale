package com.bluewhale.overview;

import com.bluewhale.overview.model.OverviewSnapshot;

/**
 * 概览提供者：每个服务实现一个（需要多个厅就实现多个）。
 *
 * <p>实现约定：</p>
 * <ol>
 *   <li><b>只读</b>。概览会被前端按 30s 轮询，任何写库都会变成"每 30 秒写一次"。</li>
 *   <li><b>不吞异常</b>。取不到数就让它抛，SDK 负责降级成 stale/degraded；
 *       provider 自己 catch 后返回空数组，会让"接口挂了"看起来像"今天没有业务"。</li>
 *   <li><b>块级裁剪自己做</b>。用 {@link OverviewContext#hasPermission(String)} 决定整块是否加入，
 *       不要下发再让前端隐藏。</li>
 *   <li><b>不要自己算 generatedAt</b>。SDK 会统一填，保证"快照时刻"与缓存时刻一致。</li>
 * </ol>
 *
 * @author BlueWhale
 */
public interface OverviewProvider {

    /**
     * 服务一级前缀，与网关路由一致
     *
     * @return 例如 {@code erp}
     */
    String service();

    /**
     * 城内节点 key，与 {@code city-layout.json} 对齐
     *
     * @return 例如 {@code hubu-hall}
     */
    String nodeKey();

    /**
     * 厅/牌主标题
     *
     * @return 例如 {@code 户部}
     */
    String title();

    /**
     * 副标题：口径与统计期间
     *
     * <p>由服务端拼好，避免前端把"本月""近 30 天"这类口径硬编码在渲染层。</p>
     *
     * @return 副标题，可空
     */
    default String subtitle() {
        return null;
    }

    /**
     * 该概览要求的权限码
     *
     * <p>缺失时直接 403，而不是返回空 KPI：空 KPI 会让用户以为"业务真的没有数"，
     * 而 403 会被前端明确渲染成"无凭据"。</p>
     *
     * @return 权限码，可空表示不设限
     */
    default String permissionCode() {
        return null;
    }

    /**
     * 构建快照
     *
     * @param context 调用者身份与权限码
     * @return 快照；{@code state} 由 SDK 决定，provider 填 {@code live} 即可
     */
    OverviewSnapshot build(OverviewContext context);
}
