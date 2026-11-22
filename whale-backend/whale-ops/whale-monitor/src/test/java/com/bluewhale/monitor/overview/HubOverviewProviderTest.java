package com.bluewhale.monitor.overview;

import com.bluewhale.overview.OverviewContext;
import com.bluewhale.overview.model.OverviewSnapshot;
import com.bluewhale.overview.support.Overviews;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link HubOverviewProvider} 契约测试：provider 只暴露身份与委托聚合，不自己填状态。
 *
 * @author BlueWhale
 */
class HubOverviewProviderTest {

    private final HubOverviewAggregator aggregator = mock(HubOverviewAggregator.class);

    private final HubOverviewProvider provider = new HubOverviewProvider(aggregator);

    @Test
    @DisplayName("身份：service/nodeKey/title/subtitle 与聚合器常量一致")
    void shouldExposeHubIdentity() {
        assertThat(provider.service()).isEqualTo(HubOverviewAggregator.SERVICE);
        assertThat(provider.nodeKey()).isEqualTo(HubOverviewAggregator.NODE_KEY);
        assertThat(provider.title()).isEqualTo(HubOverviewAggregator.TITLE);
        assertThat(provider.subtitle()).isEqualTo(HubOverviewAggregator.SUBTITLE);
    }

    @Test
    @DisplayName("build：把聚合出的汇总快照原样交给 SDK，不改状态也不重复取数")
    void shouldDelegateBuildToAggregator() {
        OverviewSnapshot snapshot = Overviews.builder("monitor", "shushu-hall", "数枢殿")
                .subtitle("八方宫署 · 总控")
                .build();
        OverviewContext context = OverviewContext.of(11L, 7L, Set.of());
        when(aggregator.aggregate(context)).thenReturn(new HubAggregate(snapshot, List.of()));

        assertThat(provider.build(context)).isSameAs(snapshot);
    }
}
