package com.bluewhale.im.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.im.entity.ImAgentBinding;
import com.bluewhale.im.entity.ImConversation;
import com.bluewhale.im.entity.ImConversationMember;
import com.bluewhale.im.mapper.ImAgentBindingMapper;
import com.bluewhale.im.mapper.ImConversationMapper;
import com.bluewhale.im.mapper.ImConversationMemberMapper;
import com.bluewhale.im.vo.ConversationVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

/**
 * 机器人会话服务：初始化并返回「当前用户 × 蓝鲸助手」的 {@code BOT} 会话。
 *
 * <p>机器人账号不落 {@code bluewhale_auth}：以配置项 {@code whale.im.bot.user-id} 作为
 * 逻辑用户ID，会话成员与消息发送者均以该ID + {@code member_type/sender_type=BOT} 标识。
 * 绑定关系写入 {@code im_agent_binding}，供 T8 调用 {@code whale-agent} 时定位机器人
 * 与智能体场景。</p>
 *
 * <p>重复调用为幂等：已存在则由配置定位并返回；缺失的绑定/成员会自动补齐。</p>
 *
 * @author BlueWhale
 */
@Service
public class ImBotService {

    /** 会话类型：机器人会话 */
    public static final String TYPE_BOT = "BOT";

    /** 会话类型：群聊 */
    private static final String TYPE_GROUP = "GROUP";

    /** 会话角色：群主 */
    private static final String ROLE_OWNER = "OWNER";

    /** 会话角色：管理员 */
    private static final String ROLE_ADMIN = "ADMIN";

    /** 成员类型：用户 */
    private static final String MEMBER_USER = "USER";

    /** 成员类型：机器人 */
    private static final String MEMBER_BOT = "BOT";

    /** 会话角色：成员 */
    private static final String ROLE_MEMBER = "MEMBER";

    /** 绑定启用 */
    private static final int BINDING_ENABLED = 1;

    private final ImConversationMapper conversationMapper;

    private final ImConversationMemberMapper memberMapper;

    private final ImAgentBindingMapper bindingMapper;

    private final ImConversationGuard guard;

    /** 机器人展示名（会话标题） */
    private final String botName;

    /** 机器人逻辑用户ID */
    private final Long botUserId;

    /** 默认智能体场景 */
    private final String botScenario;

    /** 并发首建恢复读事务模板：REQUIRES_NEW + READ_COMMITTED，跳出外层 REPEATABLE READ 快照 */
    private final TransactionTemplate recoveryReadTemplate;

    /**
     * 构造机器人会话服务。
     *
     * @param conversationMapper  会话 Mapper
     * @param memberMapper        成员 Mapper
     * @param bindingMapper       绑定 Mapper
     * @param guard               会话成员校验
     * @param transactionManager  事务管理器（并发首建恢复读用）
     * @param botName             机器人展示名
     * @param botUserId           机器人逻辑用户ID
     * @param botScenario         默认智能体场景
     */
    public ImBotService(ImConversationMapper conversationMapper, ImConversationMemberMapper memberMapper,
                        ImAgentBindingMapper bindingMapper, ImConversationGuard guard,
                        PlatformTransactionManager transactionManager,
                        @Value("${whale.im.bot.name:蓝鲸助手}") String botName,
                        @Value("${whale.im.bot.user-id:900000000000000001}") Long botUserId,
                        @Value("${whale.im.bot.scenario:DEFAULT}") String botScenario) {
        this.conversationMapper = conversationMapper;
        this.memberMapper = memberMapper;
        this.bindingMapper = bindingMapper;
        this.guard = guard;
        this.botName = botName;
        this.botUserId = botUserId;
        this.botScenario = botScenario;
        this.recoveryReadTemplate = new TransactionTemplate(transactionManager);
        this.recoveryReadTemplate.setPropagationBehavior(
                org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.recoveryReadTemplate.setIsolationLevel(
                org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /**
     * 获取或初始化当前用户的默认机器人会话。
     *
     * @param userId 当前用户ID
     * @return 机器人会话（含未读数）
     */
    @Transactional
    public ConversationVO openDefault(Long userId) {
        ImConversation conversation = findExisting(userId);
        if (conversation == null) {
            try {
                conversation = createConversation(userId);
            } catch (DuplicateKeyException e) {
                // 并发首建：pair_key 唯一索引兜底，返回先落库的会话。
                // 恢复读须在 REQUIRES_NEW + READ_COMMITTED 新事务中执行——外层事务
                // （REPEATABLE READ）的快照已在首次 findExisting 建立，同事务重读
                // 看不到并发事务已提交的行，会误判为 null 而重复抛出。
                conversation = recoveryReadTemplate.execute(status -> {
                    ImConversation found = findExisting(userId);
                    if (found != null) {
                        ensureMember(found.getId(), userId, MEMBER_USER, ROLE_OWNER);
                        ensureBinding(found.getId());
                    }
                    return found;
                });
                if (conversation == null) {
                    throw e;
                }
            }
        } else {
            ensureMember(conversation.getId(), userId, MEMBER_USER, ROLE_OWNER);
            ensureBinding(conversation.getId());
        }
        ImConversationMember member = memberMapper.selectOne(memberWrapper(conversation.getId(), userId));
        long lastReadSeq = member == null || member.getLastReadSeq() == null ? 0L : member.getLastReadSeq();
        long lastSeq = conversation.getLastSeq() == null ? 0L : conversation.getLastSeq();
        return ImConversationService.toVo(conversation, Math.max(0L, lastSeq - lastReadSeq), null);
    }

    /**
     * 启用或停用群聊机器人（群主 / 管理员）。
     *
     * <p>停用后群内 {@code @机器人} 不再触发；启用时自动补齐机器人成员与绑定。</p>
     *
     * @param conversationId 群聊会话ID
     * @param operatorId     操作人ID
     * @param enabled        是否启用
     */
    @Transactional
    public void setGroupBotEnabled(Long conversationId, Long operatorId, boolean enabled) {
        ImConversation conversation = guard.requireMember(conversationId, operatorId);
        if (!TYPE_GROUP.equals(conversation.getType())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "仅群聊支持机器人开关");
        }
        ImConversationMember operator = guard.requireMemberRecord(conversationId, operatorId);
        if (operator == null || (!ROLE_OWNER.equals(operator.getRole()) && !ROLE_ADMIN.equals(operator.getRole()))) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅群主或管理员可管理机器人");
        }
        ImAgentBinding binding = findBinding(conversationId);
        if (binding == null) {
            binding = ensureGroupBinding(conversationId);
        }
        if (binding == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "机器人绑定初始化失败");
        }
        LambdaUpdateWrapper<ImAgentBinding> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(ImAgentBinding::getId, binding.getId());
        wrapper.set(ImAgentBinding::getEnabled, enabled ? BINDING_ENABLED : 0);
        bindingMapper.update(null, wrapper);
    }

    /**
     * 机器人逻辑用户ID（供消息发送与群内 @ 识别）。
     *
     * @return 机器人用户ID
     */
    public Long botUserId() {
        return botUserId;
    }

    /**
     * 机器人展示名（群内 {@code @} 触发识别用）。
     *
     * @return 机器人展示名
     */
    public String botName() {
        return botName;
    }

    /**
     * 群聊启用机器人：补齐机器人成员与绑定关系（幂等自愈）。
     *
     * <p>群内首次 {@code @} 机器人时调用；若该群已被显式停用（{@code enabled=0}），
     * 不重复启用，由调用方按启用位判定是否触发。</p>
     *
     * @param conversationId 群聊会话ID
     * @return 绑定关系；查询不到返回 {@code null}
     */
    @Transactional
    public ImAgentBinding ensureGroupBinding(Long conversationId) {
        ensureMember(conversationId, botUserId, MEMBER_BOT, ROLE_MEMBER);
        ensureBinding(conversationId);
        LambdaQueryWrapper<ImAgentBinding> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImAgentBinding::getConversationId, conversationId);
        wrapper.eq(ImAgentBinding::getBotUserId, botUserId);
        wrapper.last("LIMIT 1");
        return bindingMapper.selectOne(wrapper);
    }

    /**
     * 定位当前用户既有的机器人会话。
     *
     * @param userId 当前用户ID
     * @return 会话实体；不存在返回 {@code null}
     */
    private ImConversation findExisting(Long userId) {
        LambdaQueryWrapper<ImConversation> byPairKey = new LambdaQueryWrapper<>();
        byPairKey.eq(ImConversation::getPairKey, "B:" + userId);
        byPairKey.last("LIMIT 1");
        ImConversation existing = conversationMapper.selectOne(byPairKey);
        if (existing != null) {
            return existing;
        }
        LambdaQueryWrapper<ImConversation> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImConversation::getType, TYPE_BOT);
        wrapper.eq(ImConversation::getOwnerId, userId);
        wrapper.orderByAsc(ImConversation::getId);
        wrapper.last("LIMIT 1");
        return conversationMapper.selectOne(wrapper);
    }

    /**
     * 创建机器人会话：会话 + 用户成员 + 机器人成员 + 绑定。
     *
     * @param userId 当前用户ID
     * @return 新建会话
     */
    private ImConversation createConversation(Long userId) {
        ImConversation conversation = new ImConversation();
        conversation.setType(TYPE_BOT);
        conversation.setTitle(botName);
        conversation.setOwnerId(userId);
        conversation.setPairKey("B:" + userId);
        conversation.setLastSeq(0L);
        conversation.setLastActiveAt(LocalDateTime.now());
        conversationMapper.insert(conversation);
        ensureMember(conversation.getId(), userId, MEMBER_USER, ROLE_OWNER);
        ensureMember(conversation.getId(), botUserId, MEMBER_BOT, ROLE_MEMBER);
        ensureBinding(conversation.getId());
        return conversation;
    }

    /**
     * 补齐会话成员（已存在则跳过）。
     *
     * @param conversationId 会话ID
     * @param userId         成员用户ID
     * @param memberType     成员类型
     * @param role           会话角色
     */
    private void ensureMember(Long conversationId, Long userId, String memberType, String role) {
        if (memberMapper.selectCount(memberWrapper(conversationId, userId)) > 0) {
            return;
        }
        ImConversationMember member = new ImConversationMember();
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setMemberType(memberType);
        member.setRole(role);
        member.setLastReadSeq(0L);
        member.setJoinTime(LocalDateTime.now());
        memberMapper.insert(member);
    }

    /**
     * 补齐会话与机器人的绑定关系（已存在则跳过）。
     *
     * @param conversationId 会话ID
     */
    private void ensureBinding(Long conversationId) {
        LambdaQueryWrapper<ImAgentBinding> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImAgentBinding::getConversationId, conversationId);
        wrapper.eq(ImAgentBinding::getBotUserId, botUserId);
        if (bindingMapper.selectCount(wrapper) > 0) {
            return;
        }
        ImAgentBinding binding = new ImAgentBinding();
        binding.setConversationId(conversationId);
        binding.setBotUserId(botUserId);
        binding.setAgentScenario(botScenario);
        binding.setEnabled(BINDING_ENABLED);
        bindingMapper.insert(binding);
    }

    /**
     * 查询会话的机器人绑定。
     *
     * @param conversationId 会话ID
     * @return 绑定；不存在返回 {@code null}
     */
    private ImAgentBinding findBinding(Long conversationId) {
        LambdaQueryWrapper<ImAgentBinding> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImAgentBinding::getConversationId, conversationId);
        wrapper.eq(ImAgentBinding::getBotUserId, botUserId);
        wrapper.last("LIMIT 1");
        return bindingMapper.selectOne(wrapper);
    }

    /**
     * 构造成员查询条件。
     *
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @return 查询条件
     */
    private static LambdaQueryWrapper<ImConversationMember> memberWrapper(Long conversationId, Long userId) {
        LambdaQueryWrapper<ImConversationMember> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ImConversationMember::getConversationId, conversationId);
        wrapper.eq(ImConversationMember::getUserId, userId);
        return wrapper;
    }
}
