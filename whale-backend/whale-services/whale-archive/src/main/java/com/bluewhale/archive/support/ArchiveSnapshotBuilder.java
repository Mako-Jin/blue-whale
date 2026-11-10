package com.bluewhale.archive.support;

import com.bluewhale.archive.remote.RemoteFileMeta;
import com.bluewhale.archive.remote.RemoteFormArchive;
import com.bluewhale.archive.remote.RemoteFormData;
import com.bluewhale.archive.remote.RemoteInstanceDetail;
import com.bluewhale.archive.remote.RemoteTask;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 档案不可变快照生成器。
 *
 * <p>快照内容 = 实例 + 定义快照 + 任务轨迹 + 表单值与字段定义 + 文件清单 + 生成时间；
 * 键顺序固定，序列化结果确定，便于计算并比对外部存证所需的 SHA-256 摘要。</p>
 *
 * @author BlueWhale
 */
@Component
@RequiredArgsConstructor
public class ArchiveSnapshotBuilder {

    /** 快照结构版本 */
    private static final int SNAPSHOT_VERSION = 1;

    /** 摘要算法 */
    private static final String DIGEST_ALGORITHM = "SHA-256";

    /** 快照文件名时间格式 */
    private static final DateTimeFormatter FILE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 字节转十六进制格式 */
    private static final String HEX_FORMAT = "%02x";

    private final ObjectMapper objectMapper;

    /**
     * 生成快照字节内容（UTF-8 JSON，键顺序固定）。
     *
     * @param detail 实例归档详情
     * @param form 表单归档详情，无表单时为 {@code null}
     * @param files 文件元数据清单
     * @param generatedAt 生成时间
     * @return 快照字节内容
     */
    public byte[] build(RemoteInstanceDetail detail, RemoteFormArchive form, List<RemoteFileMeta> files,
                        LocalDateTime generatedAt) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("version", SNAPSHOT_VERSION);
        snapshot.put("sourceType", ArchiveConstants.SOURCE_WORKFLOW);
        snapshot.put("sourceId", String.valueOf(detail.getId()));
        snapshot.put("generatedAt", formatTime(generatedAt));
        snapshot.put("instance", instanceMap(detail));
        Map<String, Object> definition = new LinkedHashMap<>();
        definition.put("definitionJson", detail.getDefinitionJson());
        snapshot.put("definition", definition);
        snapshot.put("tasks", taskMaps(detail.getTasks()));
        snapshot.put("form", formMap(form));
        snapshot.put("files", fileMaps(files));
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(snapshot);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "生成档案快照失败: " + e.getOriginalMessage());
        }
    }

    /**
     * 构造快照中的实例节点（时间统一为 ISO-8601 字符串，避免依赖 JSON 映射器的时间模块配置）。
     *
     * @param detail 实例归档详情
     * @return 实例节点
     */
    private static Map<String, Object> instanceMap(RemoteInstanceDetail detail) {
        Map<String, Object> instance = new LinkedHashMap<>();
        instance.put("id", detail.getId());
        instance.put("definitionId", detail.getDefinitionId());
        instance.put("definitionCode", detail.getDefinitionCode());
        instance.put("definitionVersion", detail.getDefinitionVersion());
        instance.put("title", detail.getTitle());
        instance.put("businessKey", detail.getBusinessKey());
        instance.put("initiator", detail.getInitiator());
        instance.put("status", detail.getStatus());
        instance.put("finishedAt", formatTime(detail.getFinishedAt()));
        instance.put("createTime", formatTime(detail.getCreateTime()));
        instance.put("updateTime", formatTime(detail.getUpdateTime()));
        instance.put("variablesJson", detail.getVariablesJson());
        return instance;
    }

    /**
     * 构造快照中的任务轨迹节点列表。
     *
     * @param tasks 任务轨迹
     * @return 任务节点列表
     */
    private static List<Map<String, Object>> taskMaps(List<RemoteTask> tasks) {
        if (tasks == null) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>(tasks.size());
        for (RemoteTask task : tasks) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", task.getId());
            node.put("nodeIndex", task.getNodeIndex());
            node.put("nodeName", task.getNodeName());
            node.put("nodeKey", task.getNodeKey());
            node.put("formCode", task.getFormCode());
            node.put("assignee", task.getAssignee());
            node.put("signType", task.getSignType());
            node.put("signPolicy", task.getSignPolicy());
            node.put("status", task.getStatus());
            node.put("comment", task.getComment());
            node.put("createTime", formatTime(task.getCreateTime()));
            node.put("updateTime", formatTime(task.getUpdateTime()));
            result.add(node);
        }
        return result;
    }

    /**
     * 构造快照中的表单节点（无表单时返回 {@code null}）。
     *
     * @param form 表单归档详情
     * @return 表单节点
     */
    private static Map<String, Object> formMap(RemoteFormArchive form) {
        if (form == null || form.getData() == null) {
            return null;
        }
        RemoteFormData data = form.getData();
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", data.getId());
        node.put("definitionCode", data.getDefinitionCode());
        node.put("definitionVersion", data.getDefinitionVersion());
        node.put("bizKey", data.getBizKey());
        node.put("submitter", data.getSubmitter());
        node.put("status", data.getStatus());
        node.put("dataJson", data.getDataJson());
        node.put("fields", data.getFields());
        node.put("fieldDefinitions", form.getFieldDefinitions());
        return node;
    }

    /**
     * 构造快照中的文件清单。
     *
     * @param files 文件元数据
     * @return 文件节点列表
     */
    private static List<Map<String, Object>> fileMaps(List<RemoteFileMeta> files) {
        if (files == null) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>(files.size());
        for (RemoteFileMeta file : files) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", file.getId());
            node.put("originalName", file.getOriginalName());
            node.put("fileName", file.getFileName());
            node.put("fileSize", file.getFileSize());
            node.put("contentType", file.getContentType());
            node.put("md5", file.getMd5());
            node.put("bucket", file.getBucket());
            node.put("status", file.getStatus());
            result.add(node);
        }
        return result;
    }

    /**
     * 格式化时间为 ISO-8601 本地时间字符串。
     *
     * @param time 时间
     * @return 字符串；空值返回 {@code null}
     */
    private static String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    /**
     * 计算快照 SHA-256 摘要（小写十六进制）。
     *
     * @param content 快照字节内容
     * @return 64 位十六进制摘要
     */
    public String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance(DIGEST_ALGORITHM).digest(content);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                hex.append(String.format(Locale.ROOT, HEX_FORMAT, value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "快照摘要算法不可用");
        }
    }

    /**
     * 生成快照文件名：{@code archive-WORKFLOW-{实例ID}-{yyyyMMddHHmmss}.json}。
     *
     * @param detail 实例归档详情
     * @param now 生成时间
     * @return 文件名
     */
    public String fileName(RemoteInstanceDetail detail, LocalDateTime now) {
        return "archive-" + ArchiveConstants.SOURCE_WORKFLOW + "-" + detail.getId() + "-"
                + FILE_TIME_FORMATTER.format(now) + ArchiveConstants.SNAPSHOT_FILE_SUFFIX;
    }
}
