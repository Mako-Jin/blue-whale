package com.bluewhale.message.service;

import com.bluewhale.message.dto.PreferenceResolveRequest;
import com.bluewhale.message.vo.PreferenceDecisionVO;

import java.util.List;

/**
 * 通知偏好服务：按用户偏好裁决通知渠道。
 *
 * @author BlueWhale
 */
public interface MessagePreferenceService {

    /**
     * 按用户偏好解析每个收件人的生效渠道与静默状态。
     *
     * @param request 解析请求
     * @return 每个用户一个决策项
     */
    List<PreferenceDecisionVO> resolve(PreferenceResolveRequest request);
}
