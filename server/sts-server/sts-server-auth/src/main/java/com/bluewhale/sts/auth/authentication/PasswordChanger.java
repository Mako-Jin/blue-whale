package com.bluewhale.sts.auth.authentication;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.sts.auth.common.AuthResultCode;
import com.bluewhale.sts.auth.common.PasswordPolicy;
import com.bluewhale.sts.auth.common.codec.PasswordHasher;
import com.bluewhale.sts.auth.common.enums.PasswordChangeSource;
import com.bluewhale.sts.auth.user.UserEntity;
import com.bluewhale.sts.auth.user.UserMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <b>设置口令的唯一入口</b> —— 强度校验、防重用、历史留痕三件事的唯一实现。
 *
 * <p><b>为什么要有这个类：</b>ADR-0020 的「后续约束」写着"任何'设置口令'的入口
 * （建号 / 重置 / 自助改密）都必须依次经过 {@code PasswordPolicy.violations(...)}、
 * 防重用检查、历史写入三件事。只做其中一件都是漏洞"。那条约束要立得住，
 * 前提是这三件事**只有一份实现** —— 而它们原本是 {@code UserService} 的私有方法，
 * 于是每出现一个新入口，唯一可行的做法就是复制粘贴，**而复制粘贴出来的三份"都对"，
 * 会在将来某一次修改里只改到其中两份**（改的那个人当然会改自己正在写的那个入口）。
 * 本类把它们提出来，让那条约束从"记得这么做"变成"只能这么做"。
 *
 * <p><b>为什么它替调用方落库：</b>因为它**已经在写库了** —— 历史那一条是
 * {@code passwordHistoryMapper.insert}。"写历史"与"写新口令"必须在同一次提交里发生，
 * 否则会出现"历史记了、口令没换"（下次改密会被一个从未用过的口令挡住）
 * 或"口令换了、历史没记"（防重用从这一轮起失效）这类半成品状态。
 * 既然写库躲不掉，就让新口令与历史在同一处落下，调用方少一个可以忘的步骤。
 *
 * <p><b>调用方负责的部分（本类刻意不做）：</b>
 * <ul>
 *   <li><b>验旧口令</b> —— 只有"自助改密"需要（管理员重置时他不知道、也不该知道旧口令）。
 *       它是自助改密与管理员重置的**分界**，见 {@code AuthService#changePassword}。</li>
 *   <li><b>{@code mustChangePassword} 的取值</b> —— 管理员重置置 1（"下次登录必须改"），
 *       自助改密置 0（改密这件事本人刚做完）。这是**调用方的语义**，不是"换口令"的固有属性。
 *       在调用本方法**之前**设好，它才来得及被同一次 {@code updateById} 带下去。</li>
 *   <li><b>解锁与清零失败次数</b> —— 管理员重置时通常正因为用户被锁住了，顺带解开；
 *       自助改密时用户本就是登录状态（失败次数在登录成功时已清零），不必重复处理。</li>
 * </ul>
 */
@Service
public class PasswordChanger {

    private final UserMapper userMapper;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final PasswordHistoryMapper passwordHistoryMapper;

    public PasswordChanger(UserMapper userMapper,
                           PasswordHasher passwordHasher,
                           PasswordPolicy passwordPolicy,
                           PasswordHistoryMapper passwordHistoryMapper) {
        this.userMapper = userMapper;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.passwordHistoryMapper = passwordHistoryMapper;
    }

    /**
     * 把 {@code user} 的口令换成 {@code rawNewPassword} 并落库。
     *
     * <p>三件事按固定顺序：形态校验（{@code 40003}）→ 防重用（{@code 40914}）→
     * 记历史并写新口令。**前一件不过就不做后一件** —— 校验失败时库里一片安静，
     * 没有"历史多了一条但口令没换"这种中间状态。
     *
     * <p><b>记历史必须在改字段之前。</b>{@code recordHistory} 读的是 {@code user} 当前
     * 的 {@code passwordAlgorithm}；先 {@code setPasswordAlgorithm(...)} 再记历史，
     * 历史里那条就会标着**新口令的算法**，而它存的实际是旧口令的哈希 ——
     * 与 {@code PasswordHasher} 的"算法随哈希存储"约定正好相反，且不会报任何错。
     *
     * @param user          目标用户。调用方先前设好的字段（如 {@code mustChangePassword}）
     *                      会随本方法末尾的 {@code updateById} 一起落库
     * @param rawNewPassword 新口令明文，只在本次调用内存在，不落库、不进日志
     * @param source        变更来源，只作证据留痕（见 {@link PasswordChangeSource}）
     * @param now           当前毫秒时刻，由调用方传入以与同一请求内的其它写共用同一个时刻
     */
    public void change(UserEntity user, String rawNewPassword, PasswordChangeSource source, long now) {
        assertStrength(rawNewPassword);
        assertNotReused(user, rawNewPassword);

        // 被替换掉的口令进历史 —— 它从此"成为过去"，而"过去用过的不能再回来"正是
        // 防重用检查要保证的事。建号时不写历史：那时没有"被替换下来的口令"，
        // 当前口令由 user.password_hash 自己承担（校验时两者都看，见 assertNotReused）。
        if (user.getPasswordHash() != null) {
            recordHistory(user, user.getPasswordHash(), user.getPasswordAlgorithm(), source, now);
        }

        assignNewPassword(user, rawNewPassword, now);
        userMapper.updateById(user);
    }

    /**
     * 换口令的最后一笔：把新哈希写进实体（**只改内存，不落库**）。
     *
     * <p>建号路径也要用它 —— 那里走的是 insert，调用方随后把整个实体写进去。
     * 单独拿出来的理由是它承载了一条**约定**而不只是三行赋值：设口令时必须
     * 三列一起更新（哈希 / 算法标识 / 更新时刻），落下任何一列都不会报错 ——
     * 少写算法标识会让 {@code PasswordHasher} 无法判断该用哪套参数校验，
     * 少写更新时刻会让"口令多久没换"这类判断静默失准。写在一处，就不会有人漏。
     */
    public void assignNewPassword(UserEntity user, String rawPassword, long now) {
        user.setPasswordHash(passwordHasher.hash(rawPassword));
        user.setPasswordAlgorithm(passwordHasher.algorithmId());
        user.setPasswordUpdatedAt(now);
    }

    // ==================== 口令强度与防重用 ====================

    /**
     * 形态校验：长度与字符类别。失败时把**缺哪几项**与**完整要求**一起回给调用方。
     *
     * <p><b>为什么单独公开这一件：</b>建号时只有它适用 —— 那一刻没有"被替换下来的口令"
     * （历史无从记起），也没有可重用的对象（防重用要拿当前或历史口令来比）。
     * ADR-0020 说的"任何设置口令的入口都必须经过三件事"，指的是**改口令**的那些入口；
     * 建号是"从无到有"，三件事里只有第一件说得通。把这一点摆成一个公开方法，
     * 而不是让建号自己再写一份长度与字符类别的判断 —— 那正是"同一个规则两处实现"
     * 的开端，而 ADR-0020 决策 2 已经为此删掉过 DTO 上的 {@code @Size(min = 8)}。
     */
    public void assertStrength(String rawPassword) {
        List<String> missing = passwordPolicy.violations(rawPassword);
        if (missing.isEmpty()) {
            return;
        }
        throw AuthResultCode.PASSWORD_POLICY_VIOLATION.businessException(
                "口令强度不足：" + String.join("、", missing) + "（要求：" + passwordPolicy.describe() + "）");
    }

    /**
     * 防重用：新口令既不得等于**当前口令**，也不得等于**最近若干个历史口令**。
     *
     * <p><b>当前口令必须单独比对</b>，因为它不在历史表里 ——
     * 历史记的是"被替换下来的那些"，而当前这一个还没被替换过。
     * 漏掉这一步的后果很具体：连续改两次、第二次填回原来的口令会静默成功。
     */
    private void assertNotReused(UserEntity user, String rawPassword) {
        if (user.getPasswordHash() != null && passwordHasher.matches(rawPassword, user.getPasswordHash())) {
            throw AuthResultCode.PASSWORD_REUSED.businessException();
        }
        boolean reused = recentHistories(user.getId()).stream()
                .anyMatch(history -> passwordHasher.matches(rawPassword, history.getPasswordHash()));
        if (reused) {
            throw AuthResultCode.PASSWORD_REUSED.businessException();
        }
    }

    /** 取该用户最近 N 条历史口令（N = 配置 {@code historyCount}；为 0 时返回空列表，即关闭该检查） */
    private List<PasswordHistoryEntity> recentHistories(Long userId) {
        int count = passwordPolicy.historyCount();
        if (count <= 0) {
            return List.of();
        }
        // searchCount=false：只要数据不要总数，避免每次改密都多跑一条 COUNT
        Page<PasswordHistoryEntity> page = new Page<>(1, count, false);
        return passwordHistoryMapper.selectList(page, Wrappers.<PasswordHistoryEntity>lambdaQuery()
                .eq(PasswordHistoryEntity::getUserId, userId)
                .orderByDesc(PasswordHistoryEntity::getChangeTime)
                .orderByDesc(PasswordHistoryEntity::getId));
    }

    /**
     * 把一条口令哈希记入历史，并裁掉超出保留数的旧记录。
     *
     * <p>算法与哈希一起传入，而不是从 {@code user} 现读 —— 调用点那边已经开始改字段了，
     * 从这里回读会读到新值（见 {@link #change} 的注释）。
     */
    private void recordHistory(UserEntity user, String passwordHash, String passwordAlgorithm,
                               PasswordChangeSource source, long now) {
        PasswordHistoryEntity history = new PasswordHistoryEntity();
        history.setTenantId(user.getTenantId());
        history.setUserId(user.getId());
        history.setPasswordHash(passwordHash);
        history.setPasswordAlgorithm(passwordAlgorithm);
        history.setChangeSource(source.wireName());
        history.setChangeTime(now);
        passwordHistoryMapper.insert(history);
        trimHistory(user.getId());
    }

    /**
     * 只保留最近 N 条。
     *
     * <p><b>裁剪按"条数"而不是按"时间"：</b>这项功能关心的是"要防几次重用"，
     * 条数直接对应它；按时间裁剪时表的规模取决于"多久清一次"，与检查能覆盖的范围脱钩 ——
     * 长期不改密的账号会攒下无限多行，而频繁改密的账号可能只剩两行。
     * 参照实现 {@code SystemTerminator} 用的是时间过期，这是本仓不采纳的一处（见 ADR-0020）。
     */
    private void trimHistory(Long userId) {
        int keep = passwordPolicy.historyCount();
        if (keep <= 0) {
            return;
        }
        List<PasswordHistoryEntity> ordered = passwordHistoryMapper.selectList(
                Wrappers.<PasswordHistoryEntity>lambdaQuery()
                        .eq(PasswordHistoryEntity::getUserId, userId)
                        .orderByDesc(PasswordHistoryEntity::getId));
        if (ordered.size() <= keep) {
            return;
        }
        List<Long> keepIds = ordered.subList(0, keep).stream()
                .map(PasswordHistoryEntity::getId)
                .toList();
        passwordHistoryMapper.delete(Wrappers.<PasswordHistoryEntity>lambdaQuery()
                .eq(PasswordHistoryEntity::getUserId, userId)
                .notIn(PasswordHistoryEntity::getId, keepIds));
    }

}
