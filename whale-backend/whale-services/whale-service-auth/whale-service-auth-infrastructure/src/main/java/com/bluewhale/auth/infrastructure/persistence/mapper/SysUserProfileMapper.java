package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserProfilePo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户身份档案主表 Mapper。
 *
 * <p>{@code sys_user_profile} 上有唯一键 {@code uk_profile_user(user_id)}（不含 {@code deleted}），
 * 主表又是逻辑删除：用户删档后再建档时直接 INSERT 会撞唯一键。因此提供两条「含已删行」的
 * 显式 SQL——按 user_id 找到历史行并复活（{@code deleted = 0}），再按主键更新，
 * 保持「一个用户一行档案」的口径。</p>
 *
 * @author BlueWhale
 */
@Mapper
public interface SysUserProfileMapper extends BaseMapper<SysUserProfilePo> {

    /**
     * 按用户 ID 查历史行主键（含逻辑删除行；userId 全局唯一，故绕过租户插件）
     *
     * @param userId 用户 ID
     * @return 主键；无历史行返回 null
     */
    @InterceptorIgnore(tenantLine = "true")
    @Select("SELECT id FROM sys_user_profile WHERE user_id = #{userId} ORDER BY deleted ASC LIMIT 1")
    Long selectAnyIdByUserId(@Param("userId") Long userId);

    /**
     * 复活逻辑删除行（只清 deleted，其余字段由后续 updateById 覆盖）
     *
     * @param id 主键
     * @return 更新行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Update("UPDATE sys_user_profile SET deleted = 0 WHERE id = #{id}")
    int reviveById(@Param("id") Long id);
}
