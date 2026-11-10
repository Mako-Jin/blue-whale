package com.bluewhale.pm.application.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.pm.domain.model.ActivityAction;
import com.bluewhale.pm.infrastructure.entity.PmProject;
import com.bluewhale.pm.infrastructure.entity.PmWorkItem;
import com.bluewhale.pm.infrastructure.entity.PmWorkItemLink;
import com.bluewhale.pm.infrastructure.mapper.PmWorkItemLinkMapper;
import com.bluewhale.pm.infrastructure.mapper.PmWorkItemMapper;
import com.bluewhale.pm.interfaces.dto.WorkItemLinkSaveRequest;
import com.bluewhale.pm.interfaces.vo.WorkItemLinkVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link WorkItemLinkAppService} 工作项关联用例单元测试。
 *
 * @author BlueWhale
 */
class WorkItemLinkAppServiceTest {

    private static final Long PROJECT_ID = 1L;

    private static final Long SOURCE_ID = 3001L;

    private static final Long TARGET_ID = 3002L;

    private static final Long OPERATOR_ID = 9L;

    private final PmWorkItemLinkMapper linkMapper = mock(PmWorkItemLinkMapper.class);

    private final PmWorkItemMapper workItemMapper = mock(PmWorkItemMapper.class);

    private final ProjectGuard projectGuard = mock(ProjectGuard.class);

    private final WorkItemGuard workItemGuard = mock(WorkItemGuard.class);

    private final WorkItemActivityAppService activityAppService = mock(WorkItemActivityAppService.class);

    private final WorkItemLinkAppService service = new WorkItemLinkAppService(linkMapper, workItemMapper,
            projectGuard, workItemGuard, activityAppService);

    /**
     * 初始化 MyBatis-Plus 实体元数据（lambda 列名缓存）
     */
    @BeforeAll
    static void initLambdaCache() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, PmWorkItemLink.class);
    }

    @Test
    @DisplayName("add：不允许关联自身")
    void shouldRejectSelfLink() {
        when(workItemGuard.requireWritable(SOURCE_ID, OPERATOR_ID)).thenReturn(item(SOURCE_ID, "BW-1"));
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());

        WorkItemLinkSaveRequest request = new WorkItemLinkSaveRequest();
        request.setTargetItemId(SOURCE_ID);
        request.setLinkType("BLOCKS");

        assertThrows(BizException.class, () -> service.add(SOURCE_ID, request, OPERATOR_ID));

        verify(linkMapper, never()).insert(any(PmWorkItemLink.class));
    }

    @Test
    @DisplayName("add：重复关联拒绝")
    void shouldRejectDuplicatedLink() {
        when(workItemGuard.requireWritable(SOURCE_ID, OPERATOR_ID)).thenReturn(item(SOURCE_ID, "BW-1"));
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(workItemGuard.requireInProject(TARGET_ID, PROJECT_ID)).thenReturn(item(TARGET_ID, "BW-2"));
        when(linkMapper.selectCount(ArgumentMatchers.<Wrapper<PmWorkItemLink>>any())).thenReturn(1L);

        assertThrows(BizException.class,
                () -> service.add(SOURCE_ID, request("RELATES"), OPERATOR_ID));

        verify(linkMapper, never()).insert(any(PmWorkItemLink.class));
    }

    @Test
    @DisplayName("add：合法关联落库并记录动态")
    void shouldAddLink() {
        when(workItemGuard.requireWritable(SOURCE_ID, OPERATOR_ID)).thenReturn(item(SOURCE_ID, "BW-1"));
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());
        when(workItemGuard.requireInProject(TARGET_ID, PROJECT_ID)).thenReturn(item(TARGET_ID, "BW-2"));
        when(linkMapper.selectCount(ArgumentMatchers.<Wrapper<PmWorkItemLink>>any())).thenReturn(0L);
        when(linkMapper.insert(any(PmWorkItemLink.class))).thenAnswer(invocation -> {
            PmWorkItemLink link = invocation.getArgument(0);
            link.setId(7001L);
            return 1;
        });

        WorkItemLinkVO vo = service.add(SOURCE_ID, request("BLOCKS"), OPERATOR_ID);

        ArgumentCaptor<PmWorkItemLink> captor = ArgumentCaptor.forClass(PmWorkItemLink.class);
        verify(linkMapper).insert(captor.capture());
        assertEquals("BLOCKS", captor.getValue().getLinkType());
        assertEquals(SOURCE_ID, captor.getValue().getSourceId());
        assertEquals(TARGET_ID, captor.getValue().getTargetId());
        assertEquals("BW-2", vo.getTargetKey());
        verify(activityAppService).record(eq(PROJECT_ID), eq(SOURCE_ID), eq(OPERATOR_ID),
                eq(ActivityAction.LINK), eq("linkType"), ArgumentMatchers.<String>isNull(), eq("BLOCKS -> BW-2"));
    }

    @Test
    @DisplayName("list：返回目标工作项键与标题")
    void shouldListLinksWithTarget() {
        when(workItemGuard.requireReadable(SOURCE_ID, OPERATOR_ID)).thenReturn(item(SOURCE_ID, "BW-1"));
        PmWorkItemLink link = new PmWorkItemLink();
        link.setId(7001L);
        link.setProjectId(PROJECT_ID);
        link.setSourceId(SOURCE_ID);
        link.setTargetId(TARGET_ID);
        link.setLinkType("RELATES");
        when(linkMapper.selectList(ArgumentMatchers.<Wrapper<PmWorkItemLink>>any())).thenReturn(List.of(link));
        when(workItemMapper.selectBatchIds(ArgumentMatchers.<java.io.Serializable>anyCollection()))
                .thenReturn(List.of(item(TARGET_ID, "BW-2")));

        List<WorkItemLinkVO> links = service.list(SOURCE_ID, OPERATOR_ID);

        assertEquals(1, links.size());
        assertEquals("BW-2", links.get(0).getTargetKey());
        assertEquals("BW-2 标题", links.get(0).getTargetTitle());
    }

    @Test
    @DisplayName("delete：以源工作项写权限删除关联")
    void shouldDeleteLink() {
        PmWorkItemLink link = new PmWorkItemLink();
        link.setId(7001L);
        link.setProjectId(PROJECT_ID);
        link.setSourceId(SOURCE_ID);
        link.setTargetId(TARGET_ID);
        link.setLinkType("RELATES");
        when(linkMapper.selectById(7001L)).thenReturn(link);
        when(workItemGuard.requireWritable(SOURCE_ID, OPERATOR_ID)).thenReturn(item(SOURCE_ID, "BW-1"));
        when(projectGuard.requireActiveProject(PROJECT_ID)).thenReturn(project());

        service.delete(7001L, OPERATOR_ID);

        verify(linkMapper).deleteById(7001L);
        verify(activityAppService).record(PROJECT_ID, SOURCE_ID, OPERATOR_ID, ActivityAction.LINK,
                "linkType", "RELATES", null);
    }

    /**
     * 构造工作项实体
     *
     * @param id  工作项ID
     * @param key 工作项键
     * @return 工作项实体
     */
    private PmWorkItem item(Long id, String key) {
        PmWorkItem item = new PmWorkItem();
        item.setId(id);
        item.setProjectId(PROJECT_ID);
        item.setItemKey(key);
        item.setTitle(key + " 标题");
        return item;
    }

    /**
     * 构造项目实体
     *
     * @return 项目实体
     */
    private PmProject project() {
        PmProject project = new PmProject();
        project.setId(PROJECT_ID);
        project.setStatus(ProjectGuard.STATUS_ACTIVE);
        return project;
    }

    /**
     * 构造关联请求
     *
     * @param linkType 关联类型
     * @return 关联请求
     */
    private WorkItemLinkSaveRequest request(String linkType) {
        WorkItemLinkSaveRequest request = new WorkItemLinkSaveRequest();
        request.setTargetItemId(TARGET_ID);
        request.setLinkType(linkType);
        return request;
    }
}
