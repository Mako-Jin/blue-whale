package com.bluewhale.archive.support;

import com.bluewhale.archive.remote.RemoteFileMeta;
import com.bluewhale.archive.remote.RemoteFormArchive;
import com.bluewhale.archive.remote.RemoteFormData;
import com.bluewhale.archive.remote.RemoteInstanceDetail;
import com.bluewhale.archive.remote.RemoteTask;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 档案快照生成器单元测试。
 *
 * @author BlueWhale
 */
class ArchiveSnapshotBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private ArchiveSnapshotBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new ArchiveSnapshotBuilder(objectMapper);
    }

    @Test
    @DisplayName("build：快照包含实例、表单、文件与生成时间，且同输入摘要稳定")
    void shouldBuildDeterministicSnapshot() throws Exception {
        RemoteInstanceDetail detail = detail();
        RemoteFormArchive form = new RemoteFormArchive();
        RemoteFormData formData = new RemoteFormData();
        formData.setId(123L);
        formData.setFields(Map.of("title", "报销单"));
        form.setData(formData);
        RemoteFileMeta file = new RemoteFileMeta();
        file.setId(11L);
        file.setOriginalName("invoice.pdf");
        LocalDateTime generatedAt = LocalDateTime.of(2026, 10, 24, 9, 30);

        byte[] first = builder.build(detail, form, List.of(file), generatedAt);
        byte[] second = builder.build(detail, form, List.of(file), generatedAt);

        assertArrayEquals(first, second);
        assertEquals(builder.sha256(first), builder.sha256(second));
        assertEquals(64, builder.sha256(first).length());

        JsonNode root = objectMapper.readTree(first);
        assertEquals(ArchiveConstants.SOURCE_WORKFLOW, root.get("sourceType").asText());
        assertEquals("7", root.get("sourceId").asText());
        assertEquals("出差审批", root.get("instance").get("title").asText());
        assertEquals("2026-05-01T10:00:00", root.get("instance").get("finishedAt").asText());
        assertEquals("{\"nodes\":[]}", root.get("definition").get("definitionJson").asText());
        assertEquals("报销单", root.get("form").get("fields").get("title").asText());
        assertEquals(11L, root.get("files").get(0).get("id").asLong());
        assertNotNull(root.get("tasks"));
        assertEquals("部门审批", root.get("tasks").get(0).get("nodeName").asText());
    }

    @Test
    @DisplayName("fileName：按来源与时间生成 JSON 文件名")
    void shouldBuildFileName() {
        String name = builder.fileName(detail(), LocalDateTime.of(2026, 10, 24, 9, 30, 15));

        assertEquals("archive-WORKFLOW-7-20261024093015.json", name);
    }

    /**
     * 构造实例归档详情。
     *
     * @return 实例归档详情
     */
    private static RemoteInstanceDetail detail() {
        RemoteInstanceDetail detail = new RemoteInstanceDetail();
        detail.setId(7L);
        detail.setDefinitionCode("DOC_APPROVE");
        detail.setDefinitionVersion(2);
        detail.setTitle("出差审批");
        detail.setBusinessKey("123");
        detail.setInitiator("zhangsan");
        detail.setStatus(1);
        detail.setDefinitionJson("{\"nodes\":[]}");
        detail.setFinishedAt(LocalDateTime.of(2026, 5, 1, 10, 0));
        RemoteTask task = new RemoteTask();
        task.setId(1L);
        task.setNodeName("部门审批");
        task.setStatus(1);
        detail.setTasks(List.of(task));
        return detail;
    }
}
