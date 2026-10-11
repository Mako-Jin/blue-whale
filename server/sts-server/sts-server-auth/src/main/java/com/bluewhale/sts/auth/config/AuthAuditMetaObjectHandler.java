package com.bluewhale.sts.auth.config;

import com.bluewhale.sts.auth.context.AuthContextHolder;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

/**
 * 审计字段自动填充。
 *
 * <p>把 {@code create_time} / {@code update_time} / {@code create_by} / {@code update_by}
 * 的填充从每个 Service 里收拢到一处：散在各处的 {@code entity.setCreateTime(...)}
 * 迟早有人漏写，而漏写不会报错，只会让某张表的审计列永远为空。
 *
 * <p><b>用 strict 系列方法</b>（{@code strictInsertFill}）而不是 {@code setFieldValByName}：
 * strict 版本只在实体上**确有该字段且声明了对应 fill 策略**时才填，
 * 因此对没有审计字段的实体（如 {@code RefreshTokenEntity}）自动无操作 ——
 * 不必写 {@code if (entity instanceof BaseEntity)} 这类判断。
 *
 * <p>{@code createBy / updateBy} 取当前登录用户；系统初始化等无请求上下文的场景
 * 取到 null，此时**只填时间、留空操作人**，而不是塞一个假的 0 号用户 ——
 * 假数据一旦进库，事后无法与真实的 0 号用户区分。
 */
public class AuthAuditMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        long now = System.currentTimeMillis();
        strictInsertFill(metaObject, "createTime", Long.class, now);
        strictInsertFill(metaObject, "updateTime", Long.class, now);
        Long userId = AuthContextHolder.currentUserId();
        if (userId != null) {
            strictInsertFill(metaObject, "createBy", Long.class, userId);
            strictInsertFill(metaObject, "updateBy", Long.class, userId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", Long.class, System.currentTimeMillis());
        Long userId = AuthContextHolder.currentUserId();
        if (userId != null) {
            strictUpdateFill(metaObject, "updateBy", Long.class, userId);
        }
    }

}
