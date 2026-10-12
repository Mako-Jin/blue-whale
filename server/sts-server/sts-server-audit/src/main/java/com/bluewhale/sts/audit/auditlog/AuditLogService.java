package com.bluewhale.sts.audit.auditlog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.sts.common.basic.enums.ResultCode;
import com.bluewhale.sts.common.basic.exception.BusinessException;
import com.bluewhale.sts.common.basic.model.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
 */
@Service
public class AuditLogService {

    /** 查询默认时间窗：7 天。与日志域、事件域保持一致 */
    private static final long DEFAULT_WINDOW_MILLIS = 7L * 24L * 60L * 60L * 1000L;

    private final AuditLogMapper auditLogMapper;

    public AuditLogService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    @Transactional(readOnly = true)
    public PageResult<AuditLogVO> page(AuditLogQuery query) {
        Page<AuditLogEntity> page = auditLogMapper.selectPage(
                new Page<>(query.getPage(), query.getSize()), buildWrapper(query));

        List<AuditLogVO> records = page.getRecords().stream().map(AuditLogService::toVO).toList();
        return PageResult.of(records, page.getTotal(), query.getPage(), query.getSize());
    }

    @Transactional(readOnly = true)
    public AuditLogVO detail(long id) {
        AuditLogEntity entity = auditLogMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "审计记录不存在");
        }
        return toVO(entity);
    }

    @Transactional(readOnly = true)
    public AuditStatsVO stats(AuditStatsQuery query) {
        long endTime = query.getEndTime() == null ? System.currentTimeMillis() : query.getEndTime();
        long startTime = query.getStartTime() == null ? endTime - DEFAULT_WINDOW_MILLIS : query.getStartTime();

        long total = auditLogMapper.selectCount(baseWrapper(query.getResource(), query.getOperatorId())
                .ge(AuditLogEntity::getServerTime, startTime)
                .le(AuditLogEntity::getServerTime, endTime));

        // 失败数单独查一次而不是从下面的动作分布里加总：加总要求"分布查询没有做任何裁剪"，
        // 而将来一旦给分布加上 Top N 之类的限制，这个隐含前提就会静默失效。
        long failureTotal = auditLogMapper.selectCount(baseWrapper(query.getResource(), query.getOperatorId())
                .eq(AuditLogEntity::getResult, AuditResult.FAILURE.wireName())
                .ge(AuditLogEntity::getServerTime, startTime)
                .le(AuditLogEntity::getServerTime, endTime));

        List<AuditActionCount> actions =
                auditLogMapper.countByAction(query.getResource(), query.getOperatorId(), startTime, endTime);

        return new AuditStatsVO(total, failureTotal, startTime, endTime, actions);
    }

    private LambdaQueryWrapper<AuditLogEntity> buildWrapper(AuditLogQuery query) {
        long endTime = query.getEndTime() == null ? System.currentTimeMillis() : query.getEndTime();
        long startTime = query.getStartTime() == null ? endTime - DEFAULT_WINDOW_MILLIS : query.getStartTime();

        LambdaQueryWrapper<AuditLogEntity> wrapper = baseWrapper(query.getResource(), query.getOperatorId())
                .eq(hasText(query.getAction()), AuditLogEntity::getAction, query.getAction())
                .eq(hasText(query.getTargetId()), AuditLogEntity::getTargetId, query.getTargetId())
                .eq(hasText(query.getOperatorName()), AuditLogEntity::getOperatorName, query.getOperatorName())
                .eq(hasText(query.getResult()), AuditLogEntity::getResult, query.getResult())
                .ge(AuditLogEntity::getServerTime, startTime)
                .le(AuditLogEntity::getServerTime, endTime);

        // 次级排序键用 id：同一毫秒内的多条记录若只按时间排序，翻页时顺序不稳定，
        // 会出现"某条记录在第 1 页和第 2 页都出现"或"哪一页都没有"。
        // 审计尤其怕这个 —— 翻页看到的重复/遗漏会被当成"记录被篡改了"。
        return wrapper.orderByDesc(AuditLogEntity::getServerTime).orderByDesc(AuditLogEntity::getId);
    }

    private static LambdaQueryWrapper<AuditLogEntity> baseWrapper(String resource, Long operatorId) {
        return Wrappers.<AuditLogEntity>lambdaQuery()
                .eq(hasText(resource), AuditLogEntity::getResource, resource)
                .eq(operatorId != null, AuditLogEntity::getOperatorId, operatorId);
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

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
