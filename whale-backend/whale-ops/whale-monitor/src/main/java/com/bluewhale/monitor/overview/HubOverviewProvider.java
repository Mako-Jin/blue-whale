package com.bluewhale.monitor.overview;

import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.OverviewProvider;
import com.bluewhale.overview.model.OverviewSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 数枢殿总控的概览 provider（{@code GET /monitor/overview}）。
 *
 * <p>本身不查任何东西，只是把 {@link HubOverviewAggregator} 的汇总结果交给 SDK：
 * 缓存、超时、降级与指标仍由 SDK 统一提供，这里连 {@code state} 都不填 ——
 * provider 说自己 live 不算数，状态由 SDK 按实际装载结果盖章。</p>
 *
 * <p><b>不设 {@code permissionCode}</b>：总控的可见范围就是"你在各殿能看到的范围"。
 * 给总控单独设一个权限码，会逼着每次新增殿都去补授权，而真正该拦的是各殿自己；
 * 缺码的殿在总控里显示为「无凭据」，看得见"有这个殿但你没凭据"，才不会让人以为殿不存在。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "whale.overview.aggregate", name = "enabled", matchIfMissing = true)
public class HubOverviewProvider implements OverviewProvider {

    /** 聚合器 */
    private final HubOverviewAggregator aggregator;

    @Override
    public String service() {
        return HubOverviewAggregator.SERVICE;
    }

    @Override
    public String nodeKey() {
        return HubOverviewAggregator.NODE_KEY;
    }

    @Override
    public String title() {
        return HubOverviewAggregator.TITLE;
    }

    @Override
    public String subtitle() {
        return HubOverviewAggregator.SUBTITLE;
    }

    @Override
    public OverviewSnapshot build(OverviewContext context) {
        return aggregator.aggregate(context).snapshot();
    }
}
