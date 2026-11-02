package com.bluewhale.doc.application.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageAcl;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageAclMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.interfaces.dto.DocPageAclUpdateRequest;
import com.bluewhale.doc.interfaces.vo.DocPageAclVO;
import com.bluewhale.doc.support.DocAccessGuard;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DocPageAclService} 页面级权限管理行为断言。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocPageAclServiceTest {

    @Mock
    private DocPageAclMapper aclMapper;

    @Mock
    private DocPageMapper pageMapper;

    @Mock
    private DocAccessGuard accessGuard;

    @Mock
    private DocPageActivityService activityService;

    @InjectMocks
    private DocPageAclService service;

    /**
     * 初始化 MyBatis-Plus 表信息，使 Lambda 查询列名解析可在纯单测中工作。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DocPageAcl.class);
        TableInfoHelper.initTableInfo(assistant, DocPage.class);
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getShouldDenyWhenUserCannotManagePage() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, 0));
        when(accessGuard.canManage(any(DocPage.class), eq(2L))).thenReturn(false);

        assertThatThrownBy(() -> service.get(7L, 2L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(40301);
        verify(aclMapper, never()).selectList(any());
    }

    @Test
    void getShouldMapRestrictedFlagAndGrants() {
        DocPageAcl acl = acl(7L, 8L, "EDIT");
        acl.setCreateTime(LocalDateTime.of(2026, 3, 4, 5, 6));
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, 1));
        when(accessGuard.canManage(any(DocPage.class), eq(2L))).thenReturn(true);
        when(accessGuard.isRestricted(any(DocPage.class))).thenReturn(true);
        when(aclMapper.selectList(any())).thenReturn(List.of(acl));

        DocPageAclVO vo = service.get(7L, 2L);

        assertThat(vo.getPageId()).isEqualTo(7L);
        assertThat(vo.isRestricted()).isTrue();
        assertThat(vo.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getUserId()).isEqualTo(8L);
            assertThat(item.getAclRole()).isEqualTo("EDIT");
            assertThat(item.getCreateTime()).isEqualTo(LocalDateTime.of(2026, 3, 4, 5, 6));
        });
    }

    @Test
    void updateShouldRejectIllegalUserId() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, 0));
        when(accessGuard.canManage(any(DocPage.class), eq(2L))).thenReturn(true);
        DocPageAclUpdateRequest request = request(true, item(0L, "READ"));

        assertThatThrownBy(() -> service.update(7L, request, 2L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("授权用户ID非法");
        verify(aclMapper, never()).deleteByPage(anyLong(), anyLong());
    }

    @Test
    void updateShouldRejectIllegalAclRole() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, 0));
        when(accessGuard.canManage(any(DocPage.class), eq(2L))).thenReturn(true);
        DocPageAclUpdateRequest request = request(true, item(8L, "OWNER"));

        assertThatThrownBy(() -> service.update(7L, request, 2L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("页面角色只能是 READ 或 EDIT");
    }

    @Test
    void updateShouldRejectMoreThanHundredGrants() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, 0));
        when(accessGuard.canManage(any(DocPage.class), eq(2L))).thenReturn(true);
        DocPageAclUpdateRequest.Item[] items = IntStream.rangeClosed(1, 101)
                .mapToObj(index -> item((long) index, "READ"))
                .toArray(DocPageAclUpdateRequest.Item[]::new);
        DocPageAclUpdateRequest request = new DocPageAclUpdateRequest();
        request.setRestricted(true);
        request.setItems(List.of(items));

        assertThatThrownBy(() -> service.update(7L, request, 2L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("授权用户不能超过 100 个");
        verify(aclMapper, never()).deleteByPage(anyLong(), anyLong());
    }

    @Test
    void updateShouldReplaceGrantsAndEnableRestricted() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, 1));
        when(accessGuard.canManage(any(DocPage.class), eq(2L))).thenReturn(true);
        when(accessGuard.isRestricted(any(DocPage.class))).thenReturn(true);
        when(aclMapper.selectList(any())).thenReturn(List.of(acl(7L, 8L, "EDIT")));
        DocPageAclUpdateRequest request = request(true,
                item(8L, "EDIT"), item(9L, "READ"), item(8L, "READ"));

        DocPageAclVO vo = service.update(7L, request, 2L);

        verify(aclMapper).deleteByPage(1L, 7L);
        ArgumentCaptor<DocPageAcl> captor = ArgumentCaptor.forClass(DocPageAcl.class);
        verify(aclMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(DocPageAcl::getUserId).containsExactly(8L, 9L);
        // 同一用户重复授权时后者覆盖（LinkedHashMap put 语义），用户 8 最终为 READ
        assertThat(captor.getAllValues()).extracting(DocPageAcl::getAclRole).containsExactly("READ", "READ");
        verify(pageMapper).update(org.mockito.ArgumentMatchers.isNull(), any());
        verify(activityService).record(eq(7L), eq("ACL"), eq(2L), eq("启用页面级权限，授权 2 人"));
        assertThat(vo.getItems()).hasSize(1);
    }

    @Test
    void updateShouldIgnoreItemsAndDisableRestrictedWhenSwitchOff() {
        when(accessGuard.requirePage(7L)).thenReturn(page(7L, 1));
        when(accessGuard.canManage(any(DocPage.class), eq(2L))).thenReturn(true);
        when(aclMapper.selectList(any())).thenReturn(List.of());
        DocPageAclUpdateRequest request = request(false, item(8L, "EDIT"));

        service.update(7L, request, 2L);

        verify(aclMapper).deleteByPage(1L, 7L);
        verify(aclMapper, never()).insert(any(DocPageAcl.class));
        verify(activityService).record(eq(7L), eq("ACL"), eq(2L), eq("关闭页面级权限"));
    }

    /**
     * 构造页面。
     *
     * @param id           页面ID
     * @param isRestricted 页面级权限开关
     * @return 页面
     */
    private DocPage page(Long id, Integer isRestricted) {
        DocPage page = new DocPage();
        page.setId(id);
        page.setIsRestricted(isRestricted);
        return page;
    }

    /**
     * 构造 ACL 行。
     *
     * @param pageId 页面ID
     * @param userId 用户ID
     * @param role   角色
     * @return ACL 行
     */
    private DocPageAcl acl(Long pageId, Long userId, String role) {
        DocPageAcl acl = new DocPageAcl();
        acl.setPageId(pageId);
        acl.setUserId(userId);
        acl.setAclRole(role);
        return acl;
    }

    /**
     * 构造更新请求。
     *
     * @param restricted 是否受限
     * @param items      授权项
     * @return 请求
     */
    private DocPageAclUpdateRequest request(boolean restricted, DocPageAclUpdateRequest.Item... items) {
        DocPageAclUpdateRequest request = new DocPageAclUpdateRequest();
        request.setRestricted(restricted);
        request.setItems(List.of(items));
        return request;
    }

    /**
     * 构造授权项。
     *
     * @param userId  用户ID
     * @param aclRole 角色
     * @return 授权项
     */
    private DocPageAclUpdateRequest.Item item(Long userId, String aclRole) {
        DocPageAclUpdateRequest.Item item = new DocPageAclUpdateRequest.Item();
        item.setUserId(userId);
        item.setAclRole(aclRole);
        return item;
    }
}
