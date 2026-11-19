package com.bluewhale.message.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.message.config.MessageRecipientProperties;
import com.bluewhale.message.dto.MessageRecipient;
import com.bluewhale.message.dto.SendMessageRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link MessageRecipientResolver} 接收人组解析单元测试。
 *
 * @author BlueWhale
 */
class MessageRecipientResolverTest {

    private final MessageRecipientProperties properties = new MessageRecipientProperties();

    private final AuthRecipientGroupClient groupClient = mock(AuthRecipientGroupClient.class);

    private final MessageRecipientResolver resolver = new MessageRecipientResolver(properties, groupClient);

    @Test
    @DisplayName("显式接收人与接收人组合并去重")
    void shouldMergeExplicitAndGroupRecipients() {
        when(groupClient.listMemberUserIds(1L, "tenant_admin")).thenReturn(List.of(2L, 3L, 2L));
        SendMessageRequest request = new SendMessageRequest();
        MessageRecipient explicit = new MessageRecipient();
        explicit.setUserId(1L);
        request.setRecipients(List.of(explicit));
        request.setRecipientGroup("tenant_admin");

        List<MessageRecipient> resolved = resolver.resolve(request, 1L);

        assertEquals(3, resolved.size());
    }

    @Test
    @DisplayName("接收人为空时拒绝发送")
    void shouldRejectEmptyRecipients() {
        SendMessageRequest request = new SendMessageRequest();
        request.setRecipients(List.of());

        assertThrows(BizException.class, () -> resolver.resolve(request, 1L));
    }

    @Test
    @DisplayName("超过接收人上限时拒绝发送")
    void shouldRejectOverLimit() {
        properties.setMaxMembers(2);
        SendMessageRequest request = new SendMessageRequest();
        request.setRecipients(List.of(recipient(1L), recipient(2L), recipient(3L)));

        assertThrows(BizException.class, () -> resolver.resolve(request, 1L));
    }

    @Test
    @DisplayName("接收人组解析关闭时携带组编码直接拒绝")
    void shouldRejectGroupWhenDisabled() {
        properties.setEnabled(false);
        SendMessageRequest request = new SendMessageRequest();
        request.setRecipientGroup("tenant_admin");

        assertThrows(BizException.class, () -> resolver.resolve(request, 1L));
    }

    /**
     * 构造仅含用户ID的接收人。
     *
     * @param userId 用户ID
     * @return 接收人
     */
    private static MessageRecipient recipient(Long userId) {
        MessageRecipient recipient = new MessageRecipient();
        recipient.setUserId(userId);
        return recipient;
    }
}
