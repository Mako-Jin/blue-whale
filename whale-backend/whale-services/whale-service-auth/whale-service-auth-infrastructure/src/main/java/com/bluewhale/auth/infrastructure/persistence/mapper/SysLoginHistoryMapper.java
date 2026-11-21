package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.po.LoginDayCountPo;
import com.bluewhale.auth.infrastructure.persistence.po.SysLoginHistoryPo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 登录历史 Mapper（append-only，仅 INSERT/SELECT）。
 *
 * @author BlueWhale
 */
@Mapper
public interface SysLoginHistoryMapper extends BaseMapper<SysLoginHistoryPo> {

    /**
     * 按天 + 登录结果聚合次数（枢密殿概览 T5.2）
     *
     * <p>用 {@code login_time} 原始列做范围条件（{@code >= / <}），只在输出上套
     * {@code DATE()}：把函数套在条件列上会让 {@code idx_login_history_time} 失效，
     * 7 天窗口在数据量大时从毫秒级变成全表扫。</p>
     *
     * <p>{@code tenant_id} 显式写入而不是依赖行级租户插件——见
     * {@code AuthSecurityStatQuery} 的口径说明。</p>
     *
     * @param tenantId 租户 ID
     * @param from 起始时刻（含）
     * @param to 截止时刻（不含）
     * @return 每天每种结果一行
     */
    @Select("SELECT DATE(login_time) AS day, login_result AS loginResult, COUNT(*) AS total "
            + "FROM sys_login_history "
            + "WHERE tenant_id = #{tenantId} AND login_time >= #{from} AND login_time < #{to} "
            + "GROUP BY DATE(login_time), login_result")
    List<LoginDayCountPo> countByDay(@Param("tenantId") Long tenantId,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);
}
