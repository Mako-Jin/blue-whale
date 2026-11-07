package com.bluewhale.oa.directory.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.directory.application.view.DirectoryViews;
import com.bluewhale.oa.directory.domain.gateway.DirectoryGateway;
import com.bluewhale.oa.directory.domain.model.DeptNode;
import com.bluewhale.oa.directory.domain.model.DirectoryUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通讯录应用服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class DirectoryAppServiceImplTest {

    @Mock
    private DirectoryGateway gateway;

    private DirectoryAppServiceImpl directoryAppService;

    @BeforeEach
    void setUp() {
        directoryAppService = new DirectoryAppServiceImpl(gateway);
    }

    @Test
    @DisplayName("pageUsers：页码与条数越界时归一化")
    void shouldNormalizePageParameters() {
        when(gateway.pageUsers(isNull(), isNull(), anyLong(), anyLong()))
                .thenReturn(page(user(), 1L, 1L, 200L));

        PageResult<DirectoryViews.UserView> result =
                directoryAppService.pageUsers(null, null, 0L, 500L);

        verify(gateway).pageUsers(null, null, 1L, 200L);
        assertEquals(1L, result.getCurrent());
        assertEquals(200L, result.getSize());
        assertEquals(1L, result.getTotal());
    }

    @Test
    @DisplayName("pageUsers：关键字原样透传并映射视图")
    void shouldPassKeywordThroughAndMapView() {
        when(gateway.pageUsers("张 三", 88L, 2L, 10L))
                .thenReturn(page(user(), 12L, 2L, 10L));

        PageResult<DirectoryViews.UserView> result =
                directoryAppService.pageUsers("张 三", 88L, 2L, 10L);

        assertEquals(1, result.getRecords().size());
        DirectoryViews.UserView view = result.getRecords().getFirst();
        assertEquals(101L, view.getId());
        assertEquals("zhangsan", view.getUsername());
        assertEquals("张三", view.getNickname());
        assertEquals(88L, view.getDeptId());
        assertEquals("研发部", view.getDeptName());
        assertEquals("13800000000", view.getPhone());
        assertEquals("zhangsan@example.com", view.getEmail());
        assertEquals(12L, result.getTotal());
    }

    @Test
    @DisplayName("deptTree：递归映射子部门")
    void shouldMapDeptTreeRecursively() {
        DeptNode root = node(1L, 0L, "蓝鲸科技", List.of(node(2L, 1L, "研发部", List.of())));
        when(gateway.deptTree()).thenReturn(List.of(root));

        List<DirectoryViews.DeptView> tree = directoryAppService.deptTree();

        assertEquals(1, tree.size());
        assertEquals("蓝鲸科技", tree.getFirst().getDeptName());
        assertEquals(1, tree.getFirst().getChildren().size());
        assertEquals("研发部", tree.getFirst().getChildren().getFirst().getDeptName());
        assertEquals(1L, tree.getFirst().getChildren().getFirst().getParentId());
    }

    @Test
    @DisplayName("pageUsers：网关运行时异常转为通讯录不可用")
    void shouldTranslateGatewayFailure() {
        when(gateway.pageUsers(isNull(), isNull(), anyLong(), anyLong()))
                .thenThrow(new IllegalStateException("auth down"));

        BizException exception = assertThrows(BizException.class,
                () -> directoryAppService.pageUsers(null, null, 1L, 10L));

        assertEquals(OaErrorCode.DIRECTORY_UNAVAILABLE, exception.getCode());
    }

    @Test
    @DisplayName("pageUsers：网关已抛业务异常时不再重复包装")
    void shouldKeepGatewayBizException() {
        when(gateway.pageUsers(isNull(), isNull(), anyLong(), anyLong()))
                .thenThrow(OaErrorCode.exception(OaErrorCode.DIRECTORY_UNAVAILABLE, "通讯录服务不可用"));

        BizException exception = assertThrows(BizException.class,
                () -> directoryAppService.pageUsers(null, null, 1L, 10L));

        assertEquals(OaErrorCode.DIRECTORY_UNAVAILABLE, exception.getCode());
        assertEquals("通讯录服务不可用", exception.getMessage());
    }

    private static PageResult<DirectoryUser> page(DirectoryUser user, long total, long current, long size) {
        return PageResult.of(List.of(user), total, current, size);
    }

    private static DirectoryUser user() {
        DirectoryUser user = new DirectoryUser();
        user.setId(101L);
        user.setUsername("zhangsan");
        user.setNickname("张三");
        user.setDeptId(88L);
        user.setDeptName("研发部");
        user.setPhone("13800000000");
        user.setEmail("zhangsan@example.com");
        return user;
    }

    private static DeptNode node(Long id, Long parentId, String name, List<DeptNode> children) {
        DeptNode node = new DeptNode();
        node.setId(id);
        node.setParentId(parentId);
        node.setName(name);
        node.setChildren(children);
        return node;
    }
}
