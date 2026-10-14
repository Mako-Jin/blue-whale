package com.bluewhale.sts.auth.authentication;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;

/**
 * <b>作废刷新令牌的唯一入口</b> —— 单个（{@link #revoke}）与该用户全部
 * （{@link #revokeAll}）两种粒度都在这里。
 *
 * <p><b>为什么要有这个类：</b>它与 {@link PasswordChanger} 是同一个形状的问题 ——
 * 一个动作牵涉**必须一起变的两列**，而这两列没有任何编译期或数据库约束绑在一起。
 * 那里的两列是"口令哈希 + 算法标识 + 更新时刻"，这里是"是否作废 + 作废时刻"。
 * 少写 {@code revokedAt} 不会报错：那一行看起来已经作废了，
 * 只是**作废于何时**无从判断 —— 会话时间线从此缺一段，而缺的那段正是排查泄露时
 * 唯一有用的那段。（{@code AuthService#refresh} 的重放检测、{@code logout}、
 * 自助改密、管理员重置原本各自写这两行，四处"都对"的实现，
 * 会在将来某一次修改里只改到正在写的那一处。）
 *
 * <p><b>为什么"撤销已撤销的令牌"要在这里挡：</b>它不是防御性编程，是这条记录的性质 ——
 * 撤销时刻**只能被写一次**。重复撤销会把那一列改写成一个更晚的时刻，
 * 于是"这个会话是什么时候被掐掉的"变成一个随调用次数漂移的值。
 * 今天每个调用点各自都挡着（{@code logout} 先查、{@code refresh} 只对未作废的令牌调用、
 * {@code revokeAll} 的 SQL 带 {@code revoked = 0}），但那是**各挡一次**；
 * 挡在实现里，才不会有下一个调用点忘了挡。这与 {@link PasswordChanger#change} 里
 * "被替换的口令一定要进历史"是同一条：**成对的东西由一处落，而不是由每个调用方记得落。**
 *
 * <p><b>它不负责"该不该撤"。</b>撤销是**凭据变更**的直接后果（换口令 ⇒ 旧会话失去依据），
 * 也用于三处与凭据无关的场合（登出这一个会话、退出所有设备、刷新令牌重放检测）。
 * 至于"账号被停用 / 被锁定要不要顺带清会话"，本类刻意不参与 —— 那两件事是**当前状态**，
 * 每次校验时读得到，靠判断而不是靠清理，见 {@code docs/decisions/0046}。
 */
@Service
public class SessionRevoker {

    private final RefreshTokenMapper refreshTokenMapper;

    public SessionRevoker(RefreshTokenMapper refreshTokenMapper) {
        this.refreshTokenMapper = refreshTokenMapper;
    }

    /**
     * 作废单个刷新令牌（登出、以及刷新时的"用旧的换新的"）。
     *
     * <p>已经作废的令牌**不再被改写撤销时刻**：一次登出重复提交、或客户端把同一个
     * 刷新令牌并发用了两次，都不该把"它是什么时候失效的"顺手推后。
     *
     * @return 实际改动的行数（0 = 本就已作废，或实体为 null）
     */
    public int revoke(RefreshTokenEntity token, long now) {
        if (token == null || (token.getRevoked() != null && token.getRevoked() != 0)) {
            return 0;
        }
        token.setRevoked(1);
        token.setRevokedAt(now);
        return refreshTokenMapper.updateById(token);
    }

    /**
     * 作废该用户**全部**未作废的刷新令牌（"退出所有设备"、凭据变更、重放检测）。
     *
     * <p>用一条带条件的 UPDATE 而不是"查出来逐个改"：后者在令牌多的时候既慢，
     * 又会在两次读之间漏掉新签发的那些。
     *
     * @return 实际作废的令牌数 —— 调用方可以据此判断"这次动作到底踢掉了几个会话"
     */
    public int revokeAll(Long userId, long now) {
        RefreshTokenEntity update = new RefreshTokenEntity();
        update.setRevoked(1);
        update.setRevokedAt(now);
        return refreshTokenMapper.update(update, Wrappers.<RefreshTokenEntity>lambdaQuery()
                .eq(RefreshTokenEntity::getUserId, userId)
                .eq(RefreshTokenEntity::getRevoked, 0));
    }

}
