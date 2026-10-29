package com.bluewhale.audit.converter;

import com.bluewhale.audit.vo.ApiLogVO;
import com.bluewhale.audit.vo.AuditLogVO;
import com.bluewhale.audit.vo.BehaviorLogVO;
import com.bluewhale.audit.vo.FrontendLogVO;
import com.bluewhale.audit.vo.OutboundLogVO;
import com.bluewhale.common.audit.entity.AuditLogRecord;
import com.bluewhale.common.log.entity.ApiLogRecord;
import com.bluewhale.common.log.entity.BehaviorLogRecord;
import com.bluewhale.common.log.entity.FrontendLogRecord;
import com.bluewhale.common.log.entity.OutboundLogRecord;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

/**
 * 审计日志 Record 与 VO 的映射器。
 *
 * <p>替代 {@code BeanUtils.copyProperties} 反射拷贝（审计 P2）：编译期校验字段
 * 对齐。五类日志的同名拷贝语义与原反射一致（Record 侧的 tenantId 不进 VO，
 * 与原行为相同）。</p>
 */
@Mapper
public interface AuditLogConverter {

    /** 单例（无状态映射器） */
    AuditLogConverter INSTANCE = Mappers.getMapper(AuditLogConverter.class);

    /**
     * 接口日志转视图对象
     *
     * @param record 接口日志记录
     * @return 接口日志视图对象
     */
    ApiLogVO toApiLogVo(ApiLogRecord record);

    /**
     * 审计日志转视图对象
     *
     * @param record 审计日志记录
     * @return 审计日志视图对象
     */
    AuditLogVO toAuditLogVo(AuditLogRecord record);

    /**
     * 行为埋点转视图对象
     *
     * @param record 行为埋点记录
     * @return 行为埋点视图对象
     */
    BehaviorLogVO toBehaviorLogVo(BehaviorLogRecord record);

    /**
     * 前端日志转视图对象
     *
     * @param record 前端日志记录
     * @return 前端日志视图对象
     */
    FrontendLogVO toFrontendLogVo(FrontendLogRecord record);

    /**
     * 出站日志转视图对象
     *
     * @param record 出站日志记录
     * @return 出站日志视图对象
     */
    OutboundLogVO toOutboundLogVo(OutboundLogRecord record);
}
