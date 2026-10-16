package com.bluewhale.sts.log.clientlog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.sts.auth.context.AuthContextHolder;
import com.bluewhale.sts.auth.tenant.TenantService;
import com.bluewhale.sts.common.basic.enums.ResultCode;
import com.bluewhale.sts.common.basic.exception.BusinessException;
import com.bluewhale.sts.common.basic.model.PageResult;
import com.bluewhale.sts.common.db.query.FilterValue;
import com.bluewhale.sts.common.db.query.KeywordLike;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 客户端日志应用服务。
 *
 * <p><b>与审计日志的边界（重要）：</b>本服务处理的是**技术日志** —— 用于排查故障，
 * 特点是量大、短保留期、可采样、可丢弃。审计日志（谁在何时对哪个资源做了什么、
 * 成功还是拒绝）走另一条管道，且**权威来源必须是后端拦截器**：这些事实前端根本不知道
 * （前端不知道请求最终是否落库成功），而且客户端上报的内容不可信。
 * 详见 {@code docs/decisions/0004-前端日志上报架构.md}。
 *
 * <p><b>本服务承担租户隔离，而不是交给调用方（重要）：</b>
 * 写入时归属取**服务端会话**，读取时租户条件由本服务**强制**加上。
 * 调用方（Controller）没有任何办法关掉它 —— 没有"按调用方传来的租户查"这种入参。
 * 理由与审计域同：一旦隔离是可选的，早晚会有一个查询忘记传，
 * 而那种遗漏在单租户的测试里完全看不出来。见 {@code docs/decisions/0026}。
 *
 * <p><b>"当前可见租户是哪一个"这件事，{@code 0077} 已收到 {@link TenantService#currentReadTenant()}：</b>
 * 本类与事件域此前各有一份私有实现、而且**逐字相同**。收成一格之后有两点是签名说死的：
 * 那一格**不接受参数**（所以"租户条件恒定加上"不再只是一个约定 —— 调用点拿不到第二个值），
 * 且**没有平台级分支**（所以**平台级操作者也读不到别家的记录**）。
 * 后者不是"忘了加"：三个域的查询入参里从来没有"点名一个租户"这一格，
 * 而 {@code 0074} 的读侧模型是"默认落在自己租户，只有显式点名才跨租户" ——
 * 要开这个口子得先改接口形状，那是一次契约变化与产品决定。
 * 当前的面貌由用例钉着：{@code ClientLogApiTest} 的两条隔离用例用的正是**平台级**的种子管理员。
 */
@Service
public class ClientLogService {

    private static final Logger log = LoggerFactory.getLogger(ClientLogService.class);

    /** 消息正文入库上限。超出即截断 —— 见 {@link #truncate} 对"截断还是丢弃"的取舍说明 */
    private static final int MAX_MESSAGE_LENGTH = 4000;

    /** data / context 序列化后的长度上限 */
    private static final int MAX_JSON_LENGTH = 8000;

    /** 查询默认时间窗：7 天 */
    private static final long DEFAULT_WINDOW_MILLIS = 7L * 24L * 60L * 60L * 1000L;

    /**
     * 本域对外说的那一句"不存在"。
     *
     * <p>做成常量是因为它现在有**两个**去处（"真的查不到"与"不属于你的租户"），
     * 而那两支必须**逐字相同**（{@code 0059} 第二节 / {@code 0075}）：
     * 两句话的差别本身就是"这条记录存在，只是在别家"。
     */
    private static final String NOT_FOUND_MESSAGE = "日志不存在或已被清理";

    /**
     * 链路查询返回条数上限。
     *
     * <p>它不是"分页"而是**兜底**：链路要么整条读、要么不读 —— 翻页读同一条链路的
     * 第 4 页没有意义。一次请求产生的日志是几十条量级，200 条已远超正常。
     * 真正会超过这个数的情形只有一种：有人把 {@code traceId} 当成可以随意灌的键
     * （它是上报方自填的，见 {@code ClientLogReportRequest}）。
     */
    private static final int TRACE_MAX_ROWS = 200;

    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {
    };

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ClientLogMapper clientLogMapper;

    private final ObjectMapper objectMapper;

    private final TenantService tenantService;

    public ClientLogService(ClientLogMapper clientLogMapper, ObjectMapper objectMapper,
                            TenantService tenantService) {
        this.clientLogMapper = clientLogMapper;
        this.objectMapper = objectMapper;
        this.tenantService = tenantService;
    }

    /**
     * 批量接收上报。
     *
     * <p><b>逐条校验，而非整批拒绝：</b>日志上报是尽力而为的通道。若一条畸形数据能让整批 500 条
     * 全部被拒，那么客户端的日志会成片丢失，且丢的是"上报那一刻最需要的那批"。
     * 因此：信封级问题（app 为空、批次超限）由 Bean Validation 整体拒绝 ——
     * 那是调用方写错了代码，应当立刻暴露；条目级问题（级别非法、消息为空）逐条跳过并计数，
     * 由返回值告诉调用方丢了什么。
     */
    public ClientLogReportResultVO report(ClientLogReportRequest request, String ip) {
        long serverTime = System.currentTimeMillis();
        // 上报接口是公开的，因此这里**不能用 require()**：未登录时必须照常入库，
        // 只是归属记为空。用 currentTenantId()（未认证返回 null）而不是先判断
        // isAuthenticated() 再取 —— 两次读 ThreadLocal 之间没有任何东西能改变它，
        // 但写成两步就多了一个"以后有人在中间插入逻辑"的缝隙。
        Long tenantId = AuthContextHolder.currentTenantId();
        List<ClientLogEntity> entities = new ArrayList<>(request.getLogs().size());
        int rejected = 0;

        for (ClientLogItem item : request.getLogs()) {
            ClientLogLevel level = ClientLogLevel.parse(item.getLevel());
            if (level == null || !hasText(item.getMessage())) {
                rejected++;
                continue;
            }

            ClientLogEntity entity = new ClientLogEntity();
            entity.setTenantId(tenantId);
            // 会参与精确过滤的列（app / namespace / traceId / sessionId），写入时与过滤侧
            // 同源（decisions/0071）：只截断不 trim 会让"上报时多打了两个空格"变成一条
            // **永远搜不到**的记录 —— 读侧的宽容在写入侧不归一时是空转。
            // 顺序不能反：先 trim 再截断（反过来时，一长串空白会把后面的内容整个切掉）。
            entity.setApp(FilterValue.text(request.getApp()));
            entity.setLevel(level.wireName());
            entity.setNamespace(truncate(FilterValue.text(item.getNamespace()), 255));
            entity.setMessage(truncate(item.getMessage(), MAX_MESSAGE_LENGTH));
            entity.setDataJson(toJson(item.getData(), MAX_JSON_LENGTH));
            entity.setContextJson(toJson(item.getContext(), MAX_JSON_LENGTH));
            entity.setTraceId(truncate(FilterValue.text(item.getTraceId()), 64));
            // 条目级字段优先，缺省回落到信封级 —— 让"整批同源"这一常见情形不必逐条重复
            entity.setSessionId(truncate(
                    FilterValue.text(firstNonBlank(item.getSessionId(), request.getSessionId())), 64));
            entity.setRoute(truncate(item.getRoute(), 512));
            entity.setAppRelease(truncate(firstNonBlank(item.getRelease(), request.getRelease()), 64));
            entity.setClientTime(item.getTimestamp());
            entity.setServerTime(serverTime);
            entity.setIp(truncate(ip, 64));
            entities.add(entity);
        }

        if (!entities.isEmpty()) {
            clientLogMapper.insertBatch(entities);
        }
        if (rejected > 0) {
            // 记 WARN 而不是 ERROR：这是数据质量问题，不是故障。
            // 记 ERROR 会让"客户端上报了畸形日志"和"服务端挂了"进入同一个告警通道。
            log.warn("客户端日志上报存在被拒条目 | app={} | accepted={} | rejected={}",
                    request.getApp(), entities.size(), rejected);
        }
        return new ClientLogReportResultVO(entities.size(), rejected);
    }

    @Transactional(readOnly = true)
    public PageResult<ClientLogVO> page(ClientLogQuery query) {
        LambdaQueryWrapper<ClientLogEntity> wrapper =
                buildWrapper(query, tenantService.currentReadTenant());

        Page<ClientLogEntity> page = clientLogMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), wrapper);

        List<ClientLogVO> records = page.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(records, page.getTotal(), query.getPage(), query.getSize());
    }

    /**
     * 查询单条日志详情。
     *
     * <p><b>"不存在"与"不属于本租户"返回同一个 40400，不区分。</b>
     * 区分二者（比如后者返 403）等于用错误码回答了一个本不该回答的问题：
     * "这个 id 上真的有一条记录"。有一条记录与没有记录，
     * 本身就是调用方不该知道的信息 —— 与审计详情、资源目录的处置一致。
     *
     * <p><b>用的是公共码，不是权限域那一套。</b>日志 / 审计 / 事件三个域都没有自己的枚举
     * （公共 {@code ResultCode} 只占用每类别的 {@code 00} 号），"这条记录不在"对它们是同一件事。
     * 这里与两个 {@code @Operation} 的描述此前写着 {@code 40401} —— 那是权限域"用户不存在"的码，
     * 而实现抛的一直是 {@code 40400}；那句话已随接口描述生成进契约与前端类型
     * （见 {@code docs/decisions/0059}），前端照着它分支就会落在一条永远不成立的条件上。
     *
     * <p>先按主键查、再比对租户，而不是"带上租户条件去查"：
     * 两者最终行为一样（都是查不到），但前者能让"这条记录确实存在、只是不给你看"
     * 与"这条记录不存在"在**日志里**可区分（需要时可加一条 WARN），
     * 而后者会把越权尝试伪装成一次普通的查不到，事后无法回溯。代价是多一次比对。
     */
    @Transactional(readOnly = true)
    public ClientLogVO detail(long id) {
        ClientLogEntity entity = clientLogMapper.selectById(id);
        if (entity == null) {
            throw notFound();
        }
        // 租户那一半走 TenantService#assertRecordInCurrentTenant（decisions/0077）：
        // `tenant_id` 为空是"上报时未登录、不属于任何租户"，对它**任何人**都不可见 ——
        // 这正是它与 `bw_auth_resource`（空 = 平台级对象、对所有人可见）不能共用
        // assertVisible 的原因，两者的 null 是相反的两件事。
        tenantService.assertRecordInCurrentTenant(entity.getTenantId(), NOT_FOUND_MESSAGE);
        return toVO(entity);
    }

    /**
     * 按链路标识取整条链路。
     *
     * <p>这是日志服务最有价值的查询：一个用户操作会同时产生前端日志与后端日志，
     * 它们用同一个 {@code traceId} 串联。没有它，排查一次失败请求需要在两个系统里
     * 按时间戳手工对齐。
     *
     * <p>按时间**升序**返回（与其他接口相反）：链路的阅读顺序就是发生顺序，
     * 倒序会让调用方多写一次 reverse。
     *
     * <p><b>本接口此前既无条数上限、也无时间窗</b> —— 而 {@code traceId} 是
     * **上报方自己填的**普通字符串（{@code ClientLogReportRequest}），
     * 因此谁都能让一个 traceId 上攒下任意多条记录、再一次性拉走。
     * 同域的 {@code page} / {@code stats} 都有 7 天缺省窗与条数上限，只有它没有 ——
     * 是漏了，不是有意的差异。现在补齐：默认窗口 + 最多 {@value #TRACE_MAX_ROWS} 条。
     *
     * <p>次级排序键取 {@code id}：同一批上报的记录 {@code serverTime} 是**同一个值**
     * （见 {@link #report} 里它在循环外只算一次），只按时间排序时批内顺序由数据库
     * 任意决定，"链路的阅读顺序"于是会随执行计划漂移。id 自增，正好等于批内原始顺序。
     */
    @Transactional(readOnly = true)
    public List<ClientLogVO> listByTraceId(String traceId) {
        if (!hasText(traceId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "traceId 不能为空");
        }
        long endTime = System.currentTimeMillis();
        long startTime = endTime - DEFAULT_WINDOW_MILLIS;

        // 用分页插件而不是手写 LIMIT：本仓要跑 MySQL 与达梦两套方言，
        // 手写的 LIMIT 在达梦下未必成立，而插件会按当前方言生成分页 SQL。
        // searchCount=false：本接口不需要总数，省掉一次 count(*)。
        Page<ClientLogEntity> page = clientLogMapper.selectPage(
                new Page<>(1L, TRACE_MAX_ROWS, false),
                Wrappers.<ClientLogEntity>lambdaQuery()
                        .eq(ClientLogEntity::getTenantId, tenantService.currentReadTenant())
                        .eq(ClientLogEntity::getTraceId, traceId)
                        .ge(ClientLogEntity::getServerTime, startTime)
                        .le(ClientLogEntity::getServerTime, endTime)
                        .orderByAsc(ClientLogEntity::getServerTime)
                        .orderByAsc(ClientLogEntity::getId));
        return page.getRecords().stream().map(this::toVO).toList();
    }

    @Transactional(readOnly = true)
    public ClientLogStatsVO stats(ClientLogStatsQuery query) {
        long endTime = query.getEndTime() == null ? System.currentTimeMillis() : query.getEndTime();
        long startTime = query.getStartTime() == null ? endTime - DEFAULT_WINDOW_MILLIS : query.getStartTime();
        long tenantId = tenantService.currentReadTenant();
        // 过滤值先规范化再进 SQL（decisions/0069）。**下面手写 SQL 那一句必须用同一个变量** ——
        // 两边各算一遍就会出现"总数对、分布是全库的"（而这里各算一遍还会因判空口径不同
        // 出现"总数 3、各级别之和 0"）。见 FilterValueTrimApiTest#shouldKeepStatisticsInSyncWithPage。
        String app = FilterValue.text(query.getApp());

        LambdaQueryWrapper<ClientLogEntity> wrapper = Wrappers.<ClientLogEntity>lambdaQuery()
                .eq(ClientLogEntity::getTenantId, tenantId)
                .eq(app != null, ClientLogEntity::getApp, app)
                .ge(ClientLogEntity::getServerTime, startTime)
                .le(ClientLogEntity::getServerTime, endTime);

        long total = clientLogMapper.selectCount(wrapper);

        Map<String, Long> counted = new HashMap<>();
        // 聚合 SQL 的租户条件必须与上面这句 wrapper **写到同一处语义**：
        // 只改上面、忘了这个手写 SQL，症状是"总数对、分布图是全库的"。
        for (ClientLogLevelCount row : clientLogMapper.countByLevel(tenantId, app, startTime, endTime)) {
            if (row.getLevel() != null) {
                counted.put(row.getLevel().toLowerCase(Locale.ROOT),
                        row.getLevelCount() == null ? 0L : row.getLevelCount());
            }
        }

        // 补齐没有数据的级别：绘图方不该为了"某级别当天没有日志"写一遍补零逻辑，
        // 而这种补零逻辑一旦落在每个消费方手里，就会出现"这个图表少了一根柱子"的诡异现象。
        List<ClientLogLevelCount> levels = new ArrayList<>(ClientLogLevel.values().length);
        for (ClientLogLevel level : ClientLogLevel.values()) {
            ClientLogLevelCount item = new ClientLogLevelCount();
            item.setLevel(level.wireName());
            item.setLevelCount(counted.getOrDefault(level.wireName(), 0L));
            levels.add(item);
        }

        return new ClientLogStatsVO(total, startTime, endTime, levels);
    }

    /** 本域那一句"不存在"的两个去处之一 —— 另一处是 {@link TenantService#assertRecordInCurrentTenant} */
    private static BusinessException notFound() {
        return new BusinessException(ResultCode.NOT_FOUND, NOT_FOUND_MESSAGE);
    }

    private LambdaQueryWrapper<ClientLogEntity> buildWrapper(ClientLogQuery query, long tenantId) {
        long endTime = query.getEndTime() == null ? System.currentTimeMillis() : query.getEndTime();
        long startTime = query.getStartTime() == null ? endTime - DEFAULT_WINDOW_MILLIS : query.getStartTime();
        // 关键词先规范化（吃掉两端空白）再转义，见 KeywordLike（decisions/0067 · 0068）。
        String pattern = KeywordLike.pattern(query.getKeyword());
        // 过滤值先规范化再进 eq（decisions/0069）：`eq(条件, 列, 值)` 的"值"是**无条件求值**的，
        // 所以必须先取局部变量，不能写成 `query.getApp().trim()`（空值时会当场 NPE）。
        String app = FilterValue.text(query.getApp());
        // level 是枚举列，走 enumValue（decisions/0070）：ClientLogLevel.wireName() 就是小写，
        // 于是"归一成标准写法"这一条自动涵盖了原先手写的 toLowerCase(Locale.ROOT)。
        String level = FilterValue.enumValue(query.getLevel(),
                ClientLogLevel::parse, ClientLogLevel::wireName);
        String namespace = FilterValue.text(query.getNamespace());
        String traceId = FilterValue.text(query.getTraceId());
        String sessionId = FilterValue.text(query.getSessionId());

        return Wrappers.<ClientLogEntity>lambdaQuery()
                .eq(ClientLogEntity::getTenantId, tenantId)
                .eq(app != null, ClientLogEntity::getApp, app)
                .eq(level != null, ClientLogEntity::getLevel, level)
                .eq(namespace != null, ClientLogEntity::getNamespace, namespace)
                .eq(traceId != null, ClientLogEntity::getTraceId, traceId)
                .eq(sessionId != null, ClientLogEntity::getSessionId, sessionId)
                .apply(pattern != null, KeywordLike.condition("message"), pattern)
                .ge(ClientLogEntity::getServerTime, startTime)
                .le(ClientLogEntity::getServerTime, endTime)
                // 次级排序键用 id：同一毫秒内的多条记录若只按时间排序，翻页时顺序不稳定，
                // 会出现"某条日志在第 1 页和第 2 页都出现"或"哪一页都没有"。
                .orderByDesc(ClientLogEntity::getServerTime)
                .orderByDesc(ClientLogEntity::getId);
    }

    private ClientLogVO toVO(ClientLogEntity entity) {
        ClientLogVO vo = new ClientLogVO();
        vo.setId(entity.getId());
        vo.setApp(entity.getApp());
        vo.setLevel(entity.getLevel());
        vo.setNamespace(entity.getNamespace());
        vo.setMessage(entity.getMessage());
        vo.setData(readJson(entity.getDataJson(), LIST_TYPE, List.of()));
        vo.setContext(readJson(entity.getContextJson(), MAP_TYPE, Map.of()));
        vo.setTraceId(entity.getTraceId());
        vo.setSessionId(entity.getSessionId());
        vo.setRoute(entity.getRoute());
        // 实体属性叫 appRelease（列名 app_release），对外仍是 release，
        // 差异原因见 ClientLogEntity 的字段注释 —— 这里显式映射而不是靠属性名自动拷贝
        vo.setRelease(entity.getAppRelease());
        vo.setTimestamp(entity.getClientTime());
        vo.setServerTime(entity.getServerTime());
        vo.setIp(entity.getIp());
        return vo;
    }

    private String toJson(Object value, int maxLength) {
        if (value == null) {
            return null;
        }
        try {
            return truncate(objectMapper.writeValueAsString(value), maxLength);
        } catch (Exception ex) {
            // 结构化数据序列化失败不该影响这条日志落库：正文才是排查的主体。
            log.warn("日志附加数据序列化失败，已丢弃该字段 | message={}", ex.getMessage());
            return null;
        }
    }

    private <T> T readJson(String json, TypeReference<T> type, T fallback) {
        if (!hasText(json)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            log.warn("日志附加数据反序列化失败，已按空值返回 | message={}", ex.getMessage());
            return fallback;
        }
    }

    /**
     * 截断超长文本。
     *
     * <p><b>为什么截断而不是丢弃整条：</b>超长的通常是消息里夹带了整段响应体或堆栈。
     * 丢掉整条等于丢掉"这里出过事"这个事实，而截断至少保留了开头 ——
     * 也就是最可能有价值的那部分。截断处留标记，避免读者以为原始内容就这么长。
     */
    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "…(已截断)";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstNonBlank(String first, String second) {
        return hasText(first) ? first : second;
    }

}
