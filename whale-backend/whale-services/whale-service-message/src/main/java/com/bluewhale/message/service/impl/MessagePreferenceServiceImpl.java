package com.bluewhale.message.service.impl;

import com.bluewhale.message.dto.PreferenceResolveRequest;
import com.bluewhale.message.service.MessagePreferenceService;
import com.bluewhale.message.support.NotifyPreferenceResolver;
import com.bluewhale.message.vo.PreferenceDecisionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通知偏好服务实现：委托 {@link NotifyPreferenceResolver}（语义解释唯一实现处）。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class MessagePreferenceServiceImpl implements MessagePreferenceService {

    /** 通知偏好解析器 */
    private final NotifyPreferenceResolver resolver;

    @Override
    public List<PreferenceDecisionVO> resolve(PreferenceResolveRequest request) {
        return resolver.resolve(request);
    }
}
