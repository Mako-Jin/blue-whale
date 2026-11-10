package com.bluewhale.twin.service;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.twin.config.TwinProperties;
import com.bluewhale.twin.dto.BindingStateVO;
import com.bluewhale.twin.dto.InsightRequest;
import com.bluewhale.twin.dto.InsightVO;
import com.bluewhale.twin.entity.TwinEntity;
import com.bluewhale.twin.entity.TwinScene;
import com.bluewhale.twin.mapper.TwinEntityMapper;
import com.bluewhale.twin.support.AssistantChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 解读服务（Wave 6.04 M1）：孪生上下文组装 + assistant 开放问答。
 *
 * <p>上下文 = 场景元数据 + 目标实体（或全场景实体清单）+ 绑定实时态
 * （来自 {@link TwinBindingRuntimeService#currentStates}）。问题文本由服务端
 * 组装（上下文为正文、用户追问为诉求），前端不直接接触内网令牌。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwinInsightService {

    /** 默认诉求（前端未传追问时） */
    private static final String DEFAULT_ASK = "请解读当前设备/场景状态，指出异常与风险，并给出处置建议。";

    /** 实时态上下文最多携带的绑定条数（防问题超长） */
    private static final int MAX_STATE_LINES = 20;

    private final TwinSceneService sceneService;
    private final TwinEntityMapper entityMapper;
    private final TwinBindingRuntimeService runtimeService;
    private final AssistantChatClient assistantClient;
    private final TwinProperties properties;

    /**
     * 生成 AI 解读。
     *
     * @param sceneId  场景ID
     * @param request  解读请求（entityId 可空）
     * @param userId   当前用户ID
     * @param tenantId 当前租户ID（可空，缺省 1）
     * @return 解读结果
     */
    public InsightVO insight(Long sceneId, InsightRequest request, Long userId, String tenantId) {
        TwinScene scene = sceneService.requireExists(sceneId);
        TwinEntity target = resolveTarget(sceneId, request.getEntityId());
        List<BindingStateVO> states = runtimeService.currentStates(sceneId);
        String context = buildContext(scene, target, states);
        String ask = StringUtils.hasText(request.getQuestion()) ? request.getQuestion() : DEFAULT_ASK;
        String question = context + "\n\n用户诉求：" + ask;
        String channelRef = "twin:" + sceneId + ":" + (target == null ? "-" : target.getId());

        Map<String, Object> data = assistantClient.chat(question, userId, tenantId, channelRef);
        return toVo(data);
    }

    /**
     * 解析解读目标实体（校验归属；可空 = 整场景）。
     *
     * @param sceneId  场景ID
     * @param entityId 实体ID（可空）
     * @return 实体或 {@code null}
     */
    private TwinEntity resolveTarget(Long sceneId, Long entityId) {
        if (entityId == null) {
            return null;
        }
        TwinEntity entity = entityMapper.selectById(entityId);
        if (entity == null || !sceneId.equals(entity.getSceneId())) {
            throw new BizException("实体不存在或不属于当前场景");
        }
        return entity;
    }

    /**
     * 组装孪生上下文文本（供大模型阅读；只放只读元数据与实时态，不放令牌/密钥）。
     *
     * @param scene  场景
     * @param target 目标实体（可空）
     * @param states 绑定实时态
     * @return 上下文文本
     */
    private String buildContext(TwinScene scene, TwinEntity target, List<BindingStateVO> states) {
        StringBuilder sb = new StringBuilder();
        sb.append("【数字孪生上下文】\n场景：").append(safe(scene.getName()))
                .append("（编码 ").append(safe(scene.getCode()))
                .append("，状态 ").append(safe(scene.getStatus())).append("）\n");
        if (target != null) {
            sb.append("解读目标实体：").append(safe(target.getName()))
                    .append("（编码 ").append(safe(target.getEntityCode()))
                    .append("）\n");
        }
        if (states.isEmpty()) {
            sb.append("该场景暂无数据绑定或实时态尚未拉取到。\n");
            return sb.toString();
        }
        List<BindingStateVO> lines = states;
        if (target != null) {
            lines = states.stream()
                    .filter(state -> target.getId().toString().equals(state.getEntityId()))
                    .toList();
            if (lines.isEmpty()) {
                sb.append("目标实体当前没有数据绑定（或实时态未拉取到）。\n");
                return sb.toString();
            }
        }
        sb.append("实时态（实体 → 数据源 → 在线 → 取值）：\n");
        int limit = Math.min(lines.size(), MAX_STATE_LINES);
        for (int i = 0; i < limit; i++) {
            BindingStateVO state = lines.get(i);
            sb.append("- ").append(state.getEntityCode() == null ? state.getEntityId() : state.getEntityCode())
                    .append(" | ").append(state.getSourceType()).append(":").append(state.getBindKey())
                    .append(" | ").append(Boolean.TRUE.equals(state.getOnline()) ? "在线" : "离线")
                    .append(" | ").append(state.getValues() == null ? "{}" : state.getValues())
                    .append('\n');
        }
        if (lines.size() > limit) {
            sb.append("（其余 ").append(lines.size() - limit).append(" 条已省略）\n");
        }
        return sb.toString();
    }

    /**
     * 开放问答结果转视图对象（citations 以通用结构透传）。
     *
     * @param data assistant 返回 data
     * @return 视图对象
     */
    @SuppressWarnings("unchecked")
    private InsightVO toVo(Map<String, Object> data) {
        List<Map<String, Object>> citations = new ArrayList<>();
        Object rawCitations = data.get("citations");
        if (rawCitations instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map) {
                    citations.add((Map<String, Object>) item);
                }
            }
        }
        return InsightVO.builder()
                .answer(data.get("answer") == null ? "" : String.valueOf(data.get("answer")))
                .degraded(Boolean.TRUE.equals(data.get("degraded")))
                .tokens(data.get("tokens") instanceof Number number ? number.intValue() : 0)
                .elapsedMillis(data.get("elapsedMillis") instanceof Number number ? number.intValue() : 0)
                .sessionId(data.get("sessionId") == null ? null : String.valueOf(data.get("sessionId")))
                .citations(citations)
                .build();
    }

    /**
     * 空安全字符串化。
     *
     * @param value 原值
     * @return 非空字符串
     */
    private static String safe(Object value) {
        return value == null ? "-" : String.valueOf(value);
    }
}
