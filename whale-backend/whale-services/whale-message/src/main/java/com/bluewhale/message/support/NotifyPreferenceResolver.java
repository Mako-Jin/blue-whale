package com.bluewhale.message.support;

import com.bluewhale.message.config.MessagePreferenceProperties;
import com.bluewhale.message.dto.PreferenceResolveRequest;
import com.bluewhale.message.enums.MessageChannel;
import com.bluewhale.message.vo.PreferenceDecisionVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 通知偏好解析器（语义解释唯一实现处）。
 *
 * <p>语义口径（与 twin 静默窗口实现对齐，Wave 6.10/6.11 的坑不重踩）：</p>
 * <ul>
 *   <li>偏好值 {@code notify.<bizType>} = {@code {channels:[], minLevel:"",
 *       quietStart:"HH:mm", quietEnd:"HH:mm"}}，字段全部可选、可只出现一部分；</li>
 *   <li>渠道集：偏好声明了 channels 时，生效渠道 = 调用方默认 ∩ 偏好渠道
 *       （用户只能收紧，不能放大调用方未申请的渠道）；</li>
 *   <li>静默窗口：HH:mm 归一、<b>左闭右开</b>、可跨零点、{@code start==end} 或
 *       非法一律 fail-open（不静默）；</li>
 *   <li>最低级别阈值：只过滤<b>非站内信</b>渠道——站内信是落库不是打扰，永远保留；
 *       级别序 {@code INFO < WARN < ERROR < CRITICAL = FATAL}，未知级别 fail-open；</li>
 *   <li>豁免级别（如 CRITICAL 穿透）由调用方经 {@code quietBypassLevels} 声明；</li>
 *   <li>无配置 / 配置解析失败 / auth 不可用 → 一律按调用方默认放行（fail-open）。</li>
 * </ul>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class NotifyPreferenceResolver {

    /** 级别权重：INFO */
    private static final int LEVEL_INFO = 0;

    /** 级别权重：WARN */
    private static final int LEVEL_WARN = 1;

    /** 级别权重：ERROR */
    private static final int LEVEL_ERROR = 2;

    /** 级别权重：CRITICAL / FATAL */
    private static final int LEVEL_CRITICAL = 3;

    /** HH:mm 解析格式 */
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final MessagePreferenceProperties properties;

    private final AuthUserConfigClient authUserConfigClient;

    private final ObjectMapper objectMapper;

    /**
     * 构造解析器。
     *
     * @param properties          偏好解析配置
     * @param authUserConfigClient auth 用户配置客户端
     * @param objectMapper        JSON 映射器
     */
    public NotifyPreferenceResolver(MessagePreferenceProperties properties,
                                    AuthUserConfigClient authUserConfigClient,
                                    ObjectMapper objectMapper) {
        this.properties = properties;
        this.authUserConfigClient = authUserConfigClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 解析每个收件人的生效渠道与静默状态。
     *
     * @param request 解析请求
     * @return 每个用户一个决策项（与请求 userIds 顺序一致，逐条独立裁决）
     */
    public List<PreferenceDecisionVO> resolve(PreferenceResolveRequest request) {
        List<String> defaultChannels = parseChannels(
                request.defaults() == null ? null : request.defaults().channels());
        Map<Long, String> values = loadPreferences(request);
        List<PreferenceDecisionVO> decisions = new ArrayList<>(request.userIds().size());
        for (Long userId : dedupe(request.userIds())) {
            decisions.add(decide(userId, request, defaultChannels, values.get(userId)));
        }
        return decisions;
    }

    /**
     * 加载偏好值映射；开关关闭 / 失败 / 未命中 → 空映射（按默认放行）。
     *
     * @param request 解析请求
     * @return {userId: 偏好值 JSON}
     */
    private Map<Long, String> loadPreferences(PreferenceResolveRequest request) {
        if (!properties.isEnabled()) {
            return Map.of();
        }
        return authUserConfigClient.batchGet(
                request.tenantId(), properties.getKeyPrefix() + request.bizType(), request.userIds());
    }

    /**
     * 单个用户的裁决。
     *
     * @param userId          用户 ID
     * @param request         解析请求
     * @param defaultChannels 调用方默认渠道集
     * @param raw             偏好值 JSON（可为 null）
     * @return 决策项
     */
    private PreferenceDecisionVO decide(Long userId, PreferenceResolveRequest request,
                                        List<String> defaultChannels, String raw) {
        JsonNode pref = parsePreference(raw);
        String source = pref != null ? PreferenceDecisionVO.SOURCE_USER
                : PreferenceDecisionVO.SOURCE_NONE;
        List<String> effective = effectiveChannels(defaultChannels, pref);
        boolean quiet = pref != null && inQuietWindow(pref, request.quietBypassLevels(), request.level());
        int minLevel = levelWeight(firstNonBlank(
                pref == null ? null : asText(pref.path("minLevel")),
                request.defaults() == null ? null : request.defaults().minLevel()));
        int current = levelWeight(request.level());
        List<String> deliverable = deliverableChannels(effective, quiet, minLevel, current);
        boolean nonInappBlocked = !effective.isEmpty()
                && effective.stream().anyMatch(c -> !MessageChannel.INAPP.name().equals(c))
                && deliverable.stream().noneMatch(c -> !MessageChannel.INAPP.name().equals(c));
        return new PreferenceDecisionVO(userId, deliverable, quiet,
                deliverable.isEmpty() || nonInappBlocked, source);
    }

    /**
     * 计算生效渠道集：偏好声明了渠道时与默认集取交集（用户只能收紧）。
     *
     * @param defaultChannels 调用方默认渠道集
     * @param pref            偏好节点（可为 null）
     * @return 生效渠道集
     */
    private static List<String> effectiveChannels(List<String> defaultChannels, JsonNode pref) {
        JsonNode prefChannels = pref == null ? null : pref.path("channels");
        if (prefChannels == null || !prefChannels.isArray() || prefChannels.isEmpty()) {
            return new ArrayList<>(defaultChannels);
        }
        Set<String> allowed = new LinkedHashSet<>(parseChannelsNode(prefChannels));
        return defaultChannels.stream().filter(allowed::contains).toList();
    }

    /**
     * 计算本次可投渠道：站内信永远保留；其余渠道受静默与最低级别阈值约束。
     *
     * @param effective 生效渠道集
     * @param quiet     是否处于静默窗口
     * @param minLevel  最低级别阈值权重（-1 表示不设阈值）
     * @param current   当前消息级别权重（-1 表示未知，fail-open）
     * @return 可投渠道列表
     */
    private static List<String> deliverableChannels(List<String> effective, boolean quiet,
                                                    int minLevel, int current) {
        List<String> deliverable = new ArrayList<>();
        for (String channel : effective) {
            if (MessageChannel.INAPP.name().equals(channel)) {
                deliverable.add(channel);
                continue;
            }
            if (quiet) {
                continue;
            }
            if (minLevel >= 0 && current >= 0 && current < minLevel) {
                continue;
            }
            deliverable.add(channel);
        }
        return deliverable;
    }

    /**
     * 解析偏好值 JSON；失败返回 null（fail-open）。
     *
     * @param raw 原始 JSON 文本
     * @return 偏好节点；不可解析返回 null
     */
    private JsonNode parsePreference(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(raw);
            return node != null && node.isObject() ? node : null;
        } catch (Exception error) {
            log.warn("偏好值解析失败（按无配置处理）：reason={}", error.getMessage());
            return null;
        }
    }

    /**
     * 判断当前消息是否处于该用户静默窗口（含豁免级别裁决）。
     *
     * @param pref              偏好节点
     * @param quietBypassLevels 调用方声明的静默豁免级别
     * @param level             当前消息级别
     * @return 处于静默窗口且未被豁免时返回 true
     */
    private boolean inQuietWindow(JsonNode pref, List<String> quietBypassLevels, String level) {
        if (quietBypassLevels != null && level != null
                && quietBypassLevels.stream().anyMatch(
                        candidate -> levelWeight(candidate) == levelWeight(level))) {
            return false;
        }
        String start = asText(pref.path("quietStart"));
        String end = asText(pref.path("quietEnd"));
        if (!StringUtils.hasText(start) || !StringUtils.hasText(end)) {
            return false;
        }
        LocalTime from;
        LocalTime to;
        try {
            from = LocalTime.parse(start.trim(), HH_MM);
            to = LocalTime.parse(end.trim(), HH_MM);
        } catch (RuntimeException error) {
            // 非法窗口 fail-open：宁可多发一条，不可漏发告警
            return false;
        }
        if (from.equals(to)) {
            return false;
        }
        LocalTime now = LocalTime.now();
        // 左闭右开 [start, end)，可跨零点：from > to 表示窗口跨天
        if (from.isBefore(to)) {
            return !now.isBefore(from) && now.isBefore(to);
        }
        return !now.isBefore(from) || now.isBefore(to);
    }

    /**
     * 解析调用方默认渠道列表（大小写不敏感，未知渠道忽略）。
     *
     * @param rawChannels 原始渠道列表
     * @return 归一化渠道列表
     */
    private static List<String> parseChannels(List<String> rawChannels) {
        if (rawChannels == null || rawChannels.isEmpty()) {
            return List.of();
        }
        List<String> channels = new ArrayList<>();
        for (String text : rawChannels) {
            MessageChannel channel = MessageChannel.parse(text);
            if (channel != null) {
                channels.add(channel.name());
            }
        }
        return channels;
    }

    /**
     * 解析渠道字符串节点（大小写不敏感，未知渠道忽略）。
     *
     * @param node 渠道数组节点
     * @return 归一化渠道列表
     */
    private static List<String> parseChannelsNode(JsonNode node) {
        List<String> channels = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String text = item.asText("");
                MessageChannel channel = MessageChannel.parse(text);
                if (channel != null) {
                    channels.add(channel.name());
                }
            }
        }
        return channels;
    }

    /**
     * 级别权重；未知级别返回 -1（fail-open：不参与阈值比较）
     *
     * @param level 级别文本
     * @return 权重，未知为 -1
     */
    private static int levelWeight(String level) {
        if (!StringUtils.hasText(level)) {
            return -1;
        }
        return switch (level.trim().toUpperCase(Locale.ROOT)) {
            case "INFO" -> LEVEL_INFO;
            case "WARN" -> LEVEL_WARN;
            case "ERROR" -> LEVEL_ERROR;
            case "CRITICAL", "FATAL" -> LEVEL_CRITICAL;
            default -> -1;
        };
    }

    /**
     * 取首个非空文本。
     *
     * @param first  首选
     * @param second 备选
     * @return 非空者或 null
     */
    private static String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return StringUtils.hasText(second) ? second : null;
    }

    /**
     * JSON 节点转文本（缺省/非文本返回 null）
     *
     * @param node 节点
     * @return 文本或 null
     */
    private static String asText(JsonNode node) {
        return node != null && node.isTextual() ? node.asText() : null;
    }

    /**
     * 用户 ID 去重（保持顺序）。
     *
     * @param userIds 原始列表
     * @return 去重列表
     */
    private static List<Long> dedupe(List<Long> userIds) {
        Map<Long, Boolean> seen = new HashMap<>();
        List<Long> result = new ArrayList<>();
        for (Long userId : userIds) {
            if (userId != null && seen.put(userId, Boolean.TRUE) == null) {
                result.add(userId);
            }
        }
        return result;
    }
}
