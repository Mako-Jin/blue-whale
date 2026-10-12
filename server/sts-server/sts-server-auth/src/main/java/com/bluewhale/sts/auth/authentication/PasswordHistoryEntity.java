package com.bluewhale.sts.auth.authentication;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 口令历史实体，对应表 {@code bw_auth_password_history}。
 *
 * <p>用于"禁止重用最近若干个口令"：改密时把被替换掉的旧口令哈希记下来，
 * 新口令逐一与最近 N 条比对，命中即拒绝。
 *
 * <p><b>刻意不继承 {@code BaseEntity}</b>（与 {@link RefreshTokenEntity} 同理）：
 * 它是 append-only 的事实记录 —— 没有"更新人"，也**不该有逻辑删除**。
 * 删掉一条历史，就等于抹掉"这个口令曾被用过"的事实，而防重用检查正是建立在它之上。
 * 表上多出 {@code update_by / deleted} 这类永远落不了值（或被误用）的列，
 * 只会让"能不能删"这个问题没有明确答案。
 *
 * <p><b>只存哈希，不存明文。</b>与 {@code bw_auth_user} 用同一套哈希格式
 * （PBKDF2-SHA256 + 参数随哈希存储），因此比较方式与登录校验完全一致 ——
 * 不需要为历史记录另立一套"是否同一个口令"的判定。
 *
 * <p><b>增长由应用侧控制</b>：每次写入后只保留最近 N 条（N = 配置 {@code historyCount}）。
 * 参照实现 {@code SystemTerminator} 用"历史记录自身的过期时间"来控制，
 * 但那样表的规模取决于"多久清一次"，而不是"要防几次重用"——
 * 后者才是这项功能真正关心的量。
 */
@Getter
@Setter
@TableName("bw_auth_password_history")
public class PasswordHistoryEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;

    private Long userId;

    /** 该口令的哈希，与 {@code bw_auth_user.password_hash} 同一套格式 */
    private String passwordHash;

    private String passwordAlgorithm;

    /** 改密来源：{@code CREATE} / {@code ADMIN_RESET}，取值见 {@code PasswordChangeSource} */
    private String changeSource;

    /** 写入时间（毫秒） */
    private Long changeTime;

}
