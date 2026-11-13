package com.bluewhale.auth.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.auth.infrastructure.persistence.po.SysUserGroupMemberPo;
import org.apache.ibatis.annotations.Delete;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户组成员关系 Mapper。
 *
 * @author BlueWhale
 */
@Mapper
public interface SysUserGroupMemberMapper extends BaseMapper<SysUserGroupMemberPo> {

    /**
     * 按组物理删除全部成员关系（整体替换口径，绕过逻辑删除；
     * 成员表有 uk_group_user 唯一键，逻辑删后重插会冲突）。
     *
     * @param groupId 用户组 ID
     * @return 删除行数
     */
    @InterceptorIgnore(tenantLine = "true")
    @Delete("DELETE FROM sys_user_group_member WHERE group_id = #{groupId}")
    int deleteByGroupId(@Param("groupId") Long groupId);
}
