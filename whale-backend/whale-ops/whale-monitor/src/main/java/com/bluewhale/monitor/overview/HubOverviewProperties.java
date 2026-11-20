package com.bluewhale.monitor.overview;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 数枢殿总控配置（前缀 {@code whale.overview.aggregate}）。
 *
 * <p>总控不自己算业务数，只把各殿已经按契约暴露的 {@code <服务>/overview} 汇总成一张表。
 * 因此"这个总控看哪几座殿"是一件配置，而不是一段代码：T5.2–T5.9 每接入一座殿，
 * 在这里加一行 {@code targets} 即可，聚合逻辑与前端都不用动。</p>
 *
 * <p><b>为什么地址是绝对 URL 而不是服务名：</b>总控在容器里跑，容器之间靠容器名 +
 * 端口直连（compose 里 {@code bw-erp:9001}），本机开发则是 {@code 127.0.0.1:9001}；
 * 两者没有共同的"服务地址"概念，只能交给部署配置。走网关注册名会让总控额外依赖网关，
 * 而网关挂掉时"数枢殿自己也看不见"是最不该发生的事。</p>
 *
 * @author BlueWhale
 */
@Data
@ConfigurationProperties(prefix = "whale.overview.aggregate")
public class HubOverviewProperties {

    /** 是否启用总控；关掉后不注册 provider，数枢殿回到 S3 的旧口径 */
    private boolean enabled = true;

    /** 单个下游的取数超时；各殿是并行问的，所以总耗时约等于最慢的那个 */
    private Duration timeout = Duration.ofSeconds(2);

    /** 聚合结果缓存时长；与 SDK 的 TTL 同量级即可（前端 30s 轮询一次） */
    private Duration cacheTtl = Duration.ofSeconds(30);

    /** 在册的殿；未登记的服务不会出现在总控里 */
    private List<Target> targets = new ArrayList<>();

    /**
     * 一座殿的接入配置。
     *
     * @author BlueWhale
     */
    @Data
    public static class Target {

        /** 城内节点 key，与 {@code city-layout.json} 对齐；同时作为表格行主键 */
        private String nodeKey;

        /** 殿名，总控表格与告警文案里显示的名字 */
        private String name;

        /** 该殿概览的绝对地址，例如 {@code http://bw-erp:9001/erp/overview} */
        private String url;

        /**
         * 该殿概览要求的权限码。
         *
         * <p>总控自己不做鉴权（各殿已各自把关），这里配它是为了<b>省一次请求</b>：
         * 明知调用者没有这个码，就不要把 403 往返也打出去，还能给出更准确的"无此殿凭据"。</p>
         */
        private String permission;
    }
}
