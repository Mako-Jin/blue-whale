package com.bluewhale.crm.marketing.domain.model;

import com.bluewhale.crm.common.CrmErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.List;

/**
 * 活动人群筛选条件（行业 / 区域 / 等级 / 来源 / 标签 / 最近跟进 / 负责人）。
 *
 * @author BlueWhale
 */
@Data
public class AudienceCriteria {

    /** JSON 序列化器（线程安全，可复用） */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 行业 */
    private List<String> industries;

    /** 区域 */
    private List<String> regions;

    /** 客户等级 */
    private List<String> levels;

    /** 来源 */
    private List<String> sources;

    /** 标签 */
    private List<String> tags;

    /** 最近跟进天数（0 表示不限） */
    private Integer lastFollowUpDays;

    /** 负责人 */
    private Long ownerId;

    /** 是否排除退订 / 黑名单 / 无联系方式，默认排除 */
    private Boolean excludeUnsubscribed = Boolean.TRUE;

    /**
     * 序列化为 JSON 文本。
     *
     * @return 条件 JSON
     */
    public String toJson() {
        try {
            return OBJECT_MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.AUDIENCE_INVALID, "人群条件序列化失败");
        }
    }

    /**
     * 反序列化条件 JSON。
     *
     * @param json JSON 文本
     * @return 条件；空文本返回空对象
     */
    public static AudienceCriteria fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new AudienceCriteria();
        }
        try {
            return OBJECT_MAPPER.readValue(json, AudienceCriteria.class);
        } catch (JsonProcessingException exception) {
            throw CrmErrorCode.exception(CrmErrorCode.AUDIENCE_INVALID, "人群条件格式非法");
        }
    }
}
