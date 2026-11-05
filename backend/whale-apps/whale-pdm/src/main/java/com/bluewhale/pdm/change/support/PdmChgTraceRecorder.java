package com.bluewhale.pdm.change.support;

import com.bluewhale.pdm.change.entity.PdmChgTrace;
import com.bluewhale.pdm.change.mapper.PdmChgTraceMapper;
import com.bluewhale.pdm.change.vo.PdmChgImpactObjectVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 变更追溯记录器
 *
 * <p>所有 ECR/ECO/ECN 状态流转与版本变化统一经此写入；只新增，不更新、不删除。
 * 影响对象会展开为按对象可查询的追溯行，便于按零件/BOM/文档查历史。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PdmChgTraceRecorder {

    /** 影响对象序列化器 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PdmChgTraceMapper traceMapper;

    /**
     * 记录业务级追溯（无具体对象）。
     *
     * @param bizType    业务类型
     * @param bizId      业务ID
     * @param bizNo      业务编号
     * @param action     动作
     * @param operatorId 操作人
     * @param content    内容摘要
     */
    public void recordBiz(String bizType, Long bizId, String bizNo, String action,
                          Long operatorId, String content) {
        write(bizType, bizId, bizNo, null, null, null, action, null, null, content, operatorId);
    }

    /**
     * 按影响对象快照展开记录。
     *
     * @param bizType    业务类型
     * @param bizId      业务ID
     * @param bizNo      业务编号
     * @param action     动作
     * @param operatorId 操作人
     * @param impactJson 影响对象 JSON
     * @param content    内容摘要
     */
    public void recordImpact(String bizType, Long bizId, String bizNo, String action,
                             Long operatorId, String impactJson, String content) {
        List<PdmChgImpactObjectVO> objects = parseImpact(impactJson);
        if (objects.isEmpty()) {
            recordBiz(bizType, bizId, bizNo, action, operatorId, content);
            return;
        }
        for (PdmChgImpactObjectVO object : objects) {
            write(bizType, bizId, bizNo, object.getObjectType(), object.getObjectId(), object.getObjectCode(),
                    action, null, null, content, operatorId);
        }
    }

    /**
     * 记录对象版本变化。
     *
     * @param bizType     业务类型
     * @param bizId       业务ID
     * @param bizNo       业务编号
     * @param objectType  对象类型
     * @param objectId    对象ID
     * @param objectCode  对象编码
     * @param action      动作
     * @param fromVersion 变更前版本
     * @param toVersion   变更后版本
     * @param operatorId  操作人
     * @param content     内容摘要
     */
    public void recordObject(String bizType, Long bizId, String bizNo, String objectType, Long objectId,
                             String objectCode, String action, String fromVersion, String toVersion,
                             Long operatorId, String content) {
        write(bizType, bizId, bizNo, objectType, objectId, objectCode, action,
                fromVersion, toVersion, content, operatorId);
    }

    /**
     * 写入一条追溯记录。
     */
    private void write(String bizType, Long bizId, String bizNo, String objectType, Long objectId,
                       String objectCode, String action, String fromVersion, String toVersion,
                       String content, Long operatorId) {
        PdmChgTrace trace = new PdmChgTrace();
        trace.setBizType(bizType);
        trace.setBizId(bizId);
        trace.setBizNo(bizNo);
        trace.setObjectType(objectType);
        trace.setObjectId(objectId);
        trace.setObjectCode(objectCode);
        trace.setAction(action);
        trace.setFromVersion(fromVersion);
        trace.setToVersion(toVersion);
        trace.setContent(content);
        trace.setOperatorId(operatorId);
        trace.setTraceTime(LocalDateTime.now());
        traceMapper.insert(trace);
    }

    /**
     * 解析影响对象 JSON。
     *
     * @param json JSON 文本
     * @return 影响对象列表
     */
    private List<PdmChgImpactObjectVO> parseImpact(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(json,
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, PdmChgImpactObjectVO.class));
        } catch (Exception ex) {
            log.warn("变更追溯影响对象解析失败", ex);
            return List.of();
        }
    }
}
