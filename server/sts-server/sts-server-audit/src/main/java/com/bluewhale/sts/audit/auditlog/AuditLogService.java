package com.bluewhale.sts.audit.auditlog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.sts.auth.context.AuthContext;
import com.bluewhale.sts.auth.context.AuthContextHolder;
import com.bluewhale.sts.auth.datascope.DataScopeService;
import com.bluewhale.sts.common.basic.enums.ResultCode;
import com.bluewhale.sts.common.basic.exception.BusinessException;
import com.bluewhale.sts.common.basic.model.PageResult;
import com.bluewhale.sts.common.db.query.FilterValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 审计查询服务。
 *
 * <p><b>只有读方法，一个写方法都没有 —— 这是刻意的，也是本域与另两条管道最根本的差别。</b>
 * 日志域与事件域各有一个 {@code report(...)} 接收上报；审计域没有对应的东西。
 * 一旦提供"写入审计"的接口，就等于给"伪造一条审计"开了一扇门 ——
 * 而审计表的全部说服力建立在"里面每一条都是服务端自己在处理真实请求时写下的"之上。
 * 想让审计被攻破，只需要一个接受外部写入的接口。
 *
 * <p>反过来说，"不可篡改"这件事在这里**不靠权限保护，靠接口不存在**：
 * 详见 {@code docs/decisions/0019}。
 *
 * <p><b>而"谁有资格看"是另一回事，它必须由服务端决定，不能由请求方决定。</b>
 * 这是本类在 ADR-0025 里被修掉的问题：早先两条查询只按调用方**传入**的
 * {@code resource} / {@code operatorId} 过滤，既没有租户条件，也没有任何
 * "这个人应该看到多大范围"的判断 —— 于是持 {@code audit:read} 的账号
 * 只要把 {@code operatorId} 换成别人（甚至不带），就能读到**其他租户**的记录。
 * 而审计表正是最不该跨租户可见的一张：它的每一行都写着"谁在什么时候做了什么"。
 *
 * <p>现在可见性由 {@link #visibility()} 一处算出，三条查询路径（分页 / 详情 / 聚合）
 * 全部经过它。规则只有两条，且方向都是"收窄"：
 * <ol>
 *   <li><b>租户</b> —— 强制等于当前登录者的租户，不接受任何绕过；</li>
 *   <li><b>操作者</b> —— 仅当数据范围合并为 {@code ALL} 时才可由请求方指定，
 *       否则强制等于当前登录者。{@code DEPT} 系列与 {@code CUSTOM} 因为还算不出精确范围，
 *       同样落进"仅本人"这一支（见 {@link DataScopeService#currentRequestSelfOnly()}）。</li>
 * </ol>
 */
@Service
public class AuditLogService {

    /** 查询默认时间窗：7 天。与日志域、事件域保持一致 */
    private static final long DEFAULT_WINDOW_MILLIS = 7L * 24L * 60L * 60L * 1000L;

    private final AuditLogMapper auditLogMapper;
    private final DataScopeService dataScopeService;

    public AuditLogService(AuditLogMapper auditLogMapper, DataScopeService dataScopeService) {
        this.auditLogMapper = auditLogMapper;
        this.dataScopeService = dataScopeService;
    }

    @Transactional(readOnly = true)
    public PageResult<AuditLogVO> page(AuditLogQuery query) {
        Visibility visibility = visibility();
        Page<AuditLogEntity> page = auditLogMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), buildWrapper(query, visibility));

        List<AuditLogVO> records = page.getRecords().stream().map(AuditLogService::toVO).toList();
        return PageResult.of(records, page.getTotal(), query.getPage(), query.getSize());
    }

    @Transactional(readOnly = true)
    public AuditLogVO detail(long id) {
        AuditLogEntity entity = auditLogMapper.selectById(id);
        if (entity == null) {
            throw notFound();
        }
        // 归属校验返回 404 而不是 403：403 等于告诉调用方"这条记录存在，只是不给你看"，
        // 而对审计记录来说，连"存在"这件事本身都不该跨租户泄漏。
        Visibility visibility = visibility();
        if (!Objects.equals(entity.getTenantId(), visibility.tenantId())
                || (visibility.selfOnly()
                        && !Objects.equals(entity.getOperatorId(), visibility.currentUserId()))) {
            throw notFound();
        }
        return toVO(entity);
    }

    @Transactional(readOnly = true)
    public AuditStatsVO stats(AuditStatsQuery query) {
        Visibility visibility = visibility();
        long endTime = query.getEndTime() == null ? System.currentTimeMillis() : query.getEndTime();
        long startTime = query.getStartTime() == null ? endTime - DEFAULT_WINDOW_MILLIS : query.getStartTime();

        Long operator = visibility.operatorFilter(query.getOperatorId());
        // 过滤值先规范化（decisions/0069）：本方法里这个值要用三次（两条 wrapper + 一句手写 SQL），
        // 所以算一次往下传 —— 各算一遍就可能有的地方归一了、有的地方没有。
        String resource = FilterValue.text(query.getResource());

        long total = auditLogMapper.selectCount(baseWrapper(visibility.tenantId(), resource, operator)
                .ge(AuditLogEntity::getServerTime, startTime)
                .le(AuditLogEntity::getServerTime, endTime));

        // 失败数单独查一次而不是从下面的动作分布里加总：加总要求"分布查询没有做任何裁剪"，
        // 而将来一旦给分布加上 Top N 之类的限制，这个隐含前提就会静默失效。
        long failureTotal = auditLogMapper.selectCount(baseWrapper(visibility.tenantId(), resource, operator)
                .eq(AuditLogEntity::getResult, AuditResult.FAILURE.wireName())
                .ge(AuditLogEntity::getServerTime, startTime)
                .le(AuditLogEntity::getServerTime, endTime));

        List<AuditActionCount> actions = auditLogMapper.countByAction(
                visibility.tenantId(), resource, operator, startTime, endTime);

        return new AuditStatsVO(total, failureTotal, startTime, endTime, actions);
    }

    /**
     * 本次请求能看到什么 —— 算一次，三条路径共用。
     *
     * <p>提出来单独算，一是因为 {@code stats} 内部要拼三次条件，
     * 每次各算一遍会打出多次同一张表的数据范围查询；二是因为可见性规则
     * 一旦分散在三处，将来改一处漏两处的表现是"列表挡住了、详情没挡住"，
     * 而这种半开的门最容易被当成没问题。
     */
    private Visibility visibility() {
        AuthContext context = AuthContextHolder.require();
        return new Visibility(context.getTenantId(), context.getUserId(),
                dataScopeService.currentRequestSelfOnly());
    }

    /** 请求级可见范围。{@code selfOnly} 为真时，请求方指定的操作人一律被忽略 */
    private record Visibility(long tenantId, long currentUserId, boolean selfOnly) {

        /**
         * 请求方指定的 {@code operatorId} 只是**在可见范围内进一步收窄**的手段，
         * 不是扩大范围的手段 —— 所以"仅本人"档下它被整体忽略，
         * 而不是"不相等就报错"：后者会把一次越权尝试变成一个可观测的 403，
         * 而它本该安静地返回调用方自己有权看的那一份。
         *
         * <p><b>写成 {@code if} 而不是三目表达式，这不是风格问题。</b>
         * {@code selfOnly ? currentUserId : requested} 里 {@code currentUserId} 是原始
         * {@code long}，三目运算符的类型统一会**把 {@code requested} 拆箱**成 {@code long} ——
         * 于是"请求方没传 operatorId"（{@code null}）从正常路径变成一次
         * {@code NullPointerException}，症状是审计列表整个 500。
         * 拆箱由编译器插入、源码里看不见，而它恰好在最常见的输入上触发。
         */
        Long operatorFilter(Long requested) {
            if (selfOnly) {
                return currentUserId;
            }
            return requested;
        }
    }

    private LambdaQueryWrapper<AuditLogEntity> buildWrapper(AuditLogQuery query, Visibility visibility) {
        long endTime = query.getEndTime() == null ? System.currentTimeMillis() : query.getEndTime();
        long startTime = query.getStartTime() == null ? endTime - DEFAULT_WINDOW_MILLIS : query.getStartTime();

        // 过滤值先规范化再进 eq（decisions/0069）：`eq(条件, 列, 值)` 的"值"是**无条件求值**的，
        // 所以必须先取局部变量，不能写成 `query.getAction().trim()`（空值时会当场 NPE）。
        String resource = FilterValue.text(query.getResource());
        String action = FilterValue.text(query.getAction());
        String targetId = FilterValue.text(query.getTargetId());
        String operatorName = FilterValue.text(query.getOperatorName());
        String result = FilterValue.text(query.getResult());

        LambdaQueryWrapper<AuditLogEntity> wrapper =
                baseWrapper(visibility.tenantId(), resource,
                        visibility.operatorFilter(query.getOperatorId()))
                        .eq(action != null, AuditLogEntity::getAction, action)
                        .eq(targetId != null, AuditLogEntity::getTargetId, targetId)
                        .eq(operatorName != null, AuditLogEntity::getOperatorName, operatorName)
                        .eq(result != null, AuditLogEntity::getResult, result)
                        .ge(AuditLogEntity::getServerTime, startTime)
                        .le(AuditLogEntity::getServerTime, endTime);

        // 次级排序键用 id：同一毫秒内的多条记录若只按时间排序，翻页时顺序不稳定，
        // 会出现"某条记录在第 1 页和第 2 页都出现"或"哪一页都没有"。
        // 审计尤其怕这个 —— 翻页看到的重复/遗漏会被当成"记录被篡改了"。
        return wrapper.orderByDesc(AuditLogEntity::getServerTime).orderByDesc(AuditLogEntity::getId);
    }

    /**
     * 所有查询的公共条件。
     *
     * <p>租户条件是**无条件**的（不是 {@code eq(条件, ...)} 那种可关的写法）：
     * 审计记录没有"平台级"的概念，每一条都属于某个租户，
     * 而把它写成可选的，等于给"忘记传租户"留下一个静默全量查询的口子。
     */
    /**
     * 公共前缀：租户 + 资源 + 操作人。
     *
     * <p>{@code resource} 是**调用方规范化之后**传进来的（{@code FilterValue.text}，见 decisions/0069），
     * 所以这里判空只需判 {@code null} —— {@code stats} 里这个值要用三次，
     * 与其在每处各归一一次，不如在入口归一一次。
     */
    private static LambdaQueryWrapper<AuditLogEntity> baseWrapper(long tenantId, String resource, Long operatorId) {
        return Wrappers.<AuditLogEntity>lambdaQuery()
                .eq(AuditLogEntity::getTenantId, tenantId)
                .eq(resource != null, AuditLogEntity::getResource, resource)
                .eq(operatorId != null, AuditLogEntity::getOperatorId, operatorId);
    }

    private static BusinessException notFound() {
        return new BusinessException(ResultCode.NOT_FOUND, "审计记录不存在");
    }

    private static AuditLogVO toVO(AuditLogEntity entity) {
        AuditLogVO vo = new AuditLogVO();
        vo.setId(entity.getId());
        vo.setAction(entity.getAction());
        vo.setResource(entity.getResource());
        vo.setTargetId(entity.getTargetId());
        vo.setOperatorId(entity.getOperatorId());
        vo.setOperatorName(entity.getOperatorName());
        vo.setTenantId(entity.getTenantId());
        vo.setResult(entity.getResult());
        vo.setErrorCode(entity.getErrorCode());
        vo.setErrorMessage(entity.getErrorMessage());
        vo.setHttpMethod(entity.getHttpMethod());
        vo.setRequestUri(entity.getRequestUri());
        vo.setIp(entity.getIp());
        vo.setUserAgent(entity.getUserAgent());
        vo.setTraceId(entity.getTraceId());
        vo.setDurationMs(entity.getDurationMs());
        vo.setServerTime(entity.getServerTime());
        return vo;
    }

}
