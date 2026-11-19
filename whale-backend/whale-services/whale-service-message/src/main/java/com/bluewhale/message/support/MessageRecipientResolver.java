package com.bluewhale.message.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.message.config.MessageRecipientProperties;
import com.bluewhale.message.dto.MessageRecipient;
import com.bluewhale.message.dto.SendMessageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 发送接收人解析：显式接收人与接收人组合并、去重、限量。
 *
 * <p>接收人组只展开为 {@code userId}（auth 通讯录返回的手机号/邮箱已脱敏，
 * 不能用于 EMAIL/SMS 投递），因此组展开适用于 INAPP / IM 渠道。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class MessageRecipientResolver {

    private final MessageRecipientProperties properties;

    private final AuthRecipientGroupClient authRecipientGroupClient;

    /**
     * 解析本次发送的最终接收人列表。
     *
     * @param request  发送请求
     * @param tenantId 租户 ID
     * @return 去重后的接收人；为空或超过上限时抛 {@link BizException}
     */
    public List<MessageRecipient> resolve(SendMessageRequest request, Long tenantId) {
        List<MessageRecipient> merged = new ArrayList<>();
        if (request.getRecipients() != null) {
            merged.addAll(request.getRecipients());
        }
        if (StringUtils.hasText(request.getRecipientGroup())) {
            merged.addAll(expandGroup(tenantId, request.getRecipientGroup().trim()));
        }
        List<MessageRecipient> deduped = dedupe(merged);
        if (deduped.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "接收人为空：接收人组无成员或未提供接收人");
        }
        if (deduped.size() > properties.getMaxMembers()) {
            throw new BizException(ErrorCode.PARAM_ERROR,
                    "接收人不能超过 " + properties.getMaxMembers() + " 人");
        }
        return deduped;
    }

    /**
     * 展开接收人组。
     *
     * @param tenantId  租户 ID
     * @param groupCode 接收人组编码
     * @return 组内接收人
     */
    private List<MessageRecipient> expandGroup(Long tenantId, String groupCode) {
        if (!properties.isEnabled()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "接收人组解析未启用");
        }
        List<Long> userIds = authRecipientGroupClient.listMemberUserIds(tenantId, groupCode);
        List<MessageRecipient> recipients = new ArrayList<>(userIds.size());
        for (Long userId : userIds) {
            if (userId == null) {
                continue;
            }
            MessageRecipient recipient = new MessageRecipient();
            recipient.setUserId(userId);
            recipients.add(recipient);
        }
        return recipients;
    }

    /**
     * 按 (userId, email, phone) 组合键去重，保持原有顺序。
     *
     * @param recipients 原始接收人
     * @return 去重后的接收人
     */
    private static List<MessageRecipient> dedupe(List<MessageRecipient> recipients) {
        Set<String> seen = new LinkedHashSet<>();
        List<MessageRecipient> result = new ArrayList<>();
        for (MessageRecipient recipient : recipients) {
            if (recipient == null) {
                continue;
            }
            String key = recipient.getUserId() + "|" + recipient.getEmail() + "|" + recipient.getPhone();
            if (seen.add(key)) {
                result.add(recipient);
            }
        }
        return result;
    }
}
