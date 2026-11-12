package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysGatewayWhitelistPo;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 网关白名单 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SysGatewayWhitelistMapper extends BaseMapper<SysGatewayWhitelistPo> {

    /**
     * 物理删除条目：白名单表唯一键为 {@code (path_pattern, http_method, scope)}，
     * 逻辑删除会长期占用唯一键导致同路径无法重新录入，故管理端删除走物理删除
     * （仅停用请把 {@code status} 置 0）。
     *
     * @param id 主键
     * @return 影响行数
     */
    @Delete("DELETE FROM sys_gateway_whitelist WHERE id = #{id}")
    int deletePhysically(@Param("id") Long id);
}
