package com.bluewhale.sts.auth.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.sts.auth.user.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户仓储。
 *
 * <p>除继承来的通用 CRUD 外，本接口有一个**刻意绕过 {@code @TableLogic} 的例外**（手写 SQL）：
 * {@code uk_bw_auth_user_tenant_username} 建在 {@code (tenant_id, username)} 上、
 * **不含 {@code deleted}**，所以软删的账号仍然占着这个登录名，而自动生成的查询
 * 一律追加 {@code deleted = 0}、看不见这个占位。全仓六个被索引逼着的唯一编码列
 * 连同各自答案的清单见 {@code server/README.md} 的「唯一编码列与它们的软删语义」。
 *
 * <p>{@code @Mapper} 必须显式标注，理由见 {@code TenantMapper}。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    /**
     * 按租户 + 登录名取一行，**包括已被逻辑删除的那些**。
     *
     * <p>手写 SQL 不会被 {@code @TableLogic} 改写，所以这里能看见软删行。
     * 建号前用它回答"这个登录名是真的没人用，还是只是被删过"。
     *
     * <p><b>与资源 / 角色 / 组织 / 用户组四个域不同，用户这一列看见软删行之后
     * 是拒绝、不是复活</b> —— 理由在 {@code UserService#create} 上，简短版是
     * "复活一个已删账号会把它的角色成员关系一起放回来"。所以本接口**没有**
     * {@code restoreDeletedById}：那个方法在这里没有被授权使用的调用点。
     *
     * <p>限定 {@code tenant_id} 是必须的，不能为了少一个条件而只按 {@code username} 查：
     * 索引本身就是复合列，登录名只在租户内唯一 —— 跨租户查到别人的同名账号
     * 会让本租户无法使用这个登录名。
     */
    @Select("""
            SELECT * FROM bw_auth_user
            WHERE tenant_id = #{tenantId} AND username = #{username}
            """)
    UserEntity selectAnyByTenantAndUsername(@Param("tenantId") Long tenantId,
                                           @Param("username") String username);

}
