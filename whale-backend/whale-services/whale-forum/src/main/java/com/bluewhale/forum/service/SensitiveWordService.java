package com.bluewhale.forum.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.forum.dto.SensitiveWordSaveRequest;
import com.bluewhale.forum.dto.SensitiveWordVO;

/**
 * 敏感词扫描服务。
 *
 * <p><b>三档动作取最严</b>：同一段文本可能同时命中多个词，
 * 只要有一个 {@code BLOCK} 就整段拒绝（{@link #scan} 返回 BLOCK），
 * 其次 {@code PENDING} → 进待审，最后 {@code REPLACE} → 打码放行。
 * 取「先命中的那个」会让用户靠调整词序绕过拦截。</p>
 *
 * <p>词库按租户懒加载并缓存，管理端改词后调用 {@link #reload()} 生效，
 * 不需要重启服务。</p>
 *
 * @author BlueWhale
 */
public interface SensitiveWordService {

    /**
     * 扫描文本。
     *
     * <p>纯函数：不落库、不抛业务异常，调用方按返回的动作自行决定拦截/打码/转待审。</p>
     *
     * @param text 待扫描文本（可空）
     * @return 扫描结果（无命中时动作为 {@code NONE}）
     */
    ScanResult scan(String text);

    /**
     * 重载当前租户词库。
     *
     * @return 加载到的启用词条数
     */
    int reload();

    /**
     * 当前租户已缓存的词条数（未加载时为 0）。
     *
     * @return 词条数
     */
    int cachedWordCount();

    /**
     * 记录命中留痕（{@code fm_sensitive_hit}）。
     *
     * <p>留痕失败只记日志、**不抛出**：留痕是旁路，不能因为审计表写不进去
     * 就让用户发不出帖。真正需要拦截的内容已经在 {@link #scan} 阶段决定。</p>
     *
     * <p><b>必须独立事务</b>（REQUIRES_NEW）：BLOCK 场景下调用方随后抛 30031，
     * 同事务的留痕会随回滚一起消失——最该留证据的拦截反而没有记录。</p>
     *
     * @param targetType 对象类型（THREAD / POST）
     * @param targetId 对象ID（拦截未落库时传 {@code null}）
     * @param userId 触发用户
     * @param result 扫描结果
     */
    void recordHit(String targetType, Long targetId, Long userId, ScanResult result);

    /**
     * 词库分页。
     *
     * @param keyword 关键词（可空）
     * @param current 页码
     * @param size 每页条数
     * @return 分页结果
     */
    PageResult<SensitiveWordVO> page(String keyword, Long current, Long size);

    /**
     * 新增或更新词条（同词重复提交按更新处理），随后自动重载缓存。
     *
     * @param request 词条请求
     * @param operatorId 操作人
     * @return 词条ID
     */
    Long save(SensitiveWordSaveRequest request, Long operatorId);

    /**
     * 删除词条（逻辑删），随后自动重载缓存。
     *
     * @param id 词条ID
     */
    void remove(Long id);

    /**
     * 启用/停用词条，随后自动重载缓存。
     *
     * @param id 词条ID
     * @param status 1 启用 / 0 停用
     */
    void toggle(Long id, Integer status);

    /**
     * 一次扫描的结果。
     *
     * @param action 动作：NONE / BLOCK / PENDING / REPLACE
     * @param hitWord 命中词（无命中为 null；REPLACE 时为第一个命中词）
     * @param position 命中位置（字符偏移，无命中为 -1）
     * @param text 处理后的文本（REPLACE 已打码；其余与入参相同）
     */
    record ScanResult(String action, String hitWord, int position, String text) {

        /** 无命中动作 */
        public static final String NONE = "NONE";

        /** 拦截 */
        public static final String BLOCK = "BLOCK";

        /** 转待审 */
        public static final String PENDING = "PENDING";

        /** 替换 */
        public static final String REPLACE = "REPLACE";

        /**
         * 是否命中任何词条。
         *
         * @return 命中返回 {@code true}
         */
        public boolean hit() {
            return !NONE.equals(action);
        }
    }
}
