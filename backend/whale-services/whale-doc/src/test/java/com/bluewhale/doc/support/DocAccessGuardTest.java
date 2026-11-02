package com.bluewhale.doc.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.doc.domain.model.DocAclRole;
import com.bluewhale.doc.domain.model.DocMemberRole;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPageAcl;
import com.bluewhale.doc.infrastructure.persistence.entity.DocSpace;
import com.bluewhale.doc.infrastructure.persistence.entity.DocSpaceMember;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageAclMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocSpaceMapper;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocSpaceMemberMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link DocAccessGuard} 数据权限边界断言。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DocAccessGuardTest {

    @Mock
    private DocSpaceMapper spaceMapper;

    @Mock
    private DocSpaceMemberMapper memberMapper;

    @Mock
    private DocPageMapper pageMapper;

    @Mock
    private DocPageAclMapper aclMapper;

    @InjectMocks
    private DocAccessGuard guard;

    /**
     * 初始化 MyBatis-Plus 表信息，使 Lambda 查询列名解析可在纯单测中工作。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, DocSpaceMember.class);
        TableInfoHelper.initTableInfo(assistant, DocPageAcl.class);
        TableInfoHelper.initTableInfo(assistant, DocPage.class);
    }

    @Test
    void roleOfShouldReturnNullWhenNotMember() {
        when(memberMapper.selectOne(any())).thenReturn(null);

        assertThat(guard.roleOf(1L, 2L)).isNull();
    }

    @Test
    void roleOfShouldParseMemberRole() {
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 2L, "EDITOR"));

        assertThat(guard.roleOf(1L, 2L)).isEqualTo(DocMemberRole.EDITOR);
    }

    @Test
    void roleOfShouldTreatIllegalRoleAsNonMember() {
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 2L, "GUEST"));

        assertThat(guard.roleOf(1L, 2L)).isNull();
    }

    @Test
    void requireSpaceShouldThrowWhenMissing() {
        when(spaceMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> guard.requireSpace(1L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(40301);
    }

    @Test
    void requireSpaceShouldReturnExistingSpace() {
        DocSpace space = space(1L, "MEMBER", 1);
        when(spaceMapper.selectById(1L)).thenReturn(space);

        assertThat(guard.requireSpace(1L)).isSameAs(space);
    }

    @Test
    void requireSpaceReadShouldAllowMember() {
        DocSpace space = space(1L, "MEMBER", 0);
        when(spaceMapper.selectById(1L)).thenReturn(space);
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 2L, "READER"));

        assertThat(guard.requireSpaceRead(1L, 2L)).isSameAs(space);
    }

    @Test
    void requireSpaceReadShouldAllowPublicActiveSpaceForOutsider() {
        DocSpace space = space(1L, "PUBLIC", 1);
        when(spaceMapper.selectById(1L)).thenReturn(space);
        when(memberMapper.selectOne(any())).thenReturn(null);

        assertThat(guard.requireSpaceRead(1L, 2L)).isSameAs(space);
    }

    @Test
    void requireSpaceReadShouldDenyPrivateSpaceForOutsider() {
        DocSpace space = space(1L, "MEMBER", 1);
        when(spaceMapper.selectById(1L)).thenReturn(space);
        when(memberMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> guard.requireSpaceRead(1L, 2L))
                .isInstanceOf(BizException.class);
    }

    @Test
    void requireMemberShouldEnforceMinimumRole() {
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 2L, "EDITOR"));

        assertThat(guard.requireMember(1L, 2L, DocMemberRole.READER)).isEqualTo(DocMemberRole.EDITOR);
        assertThatCode(() -> guard.requireMember(1L, 2L, DocMemberRole.EDITOR)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.requireMember(1L, 2L, DocMemberRole.ADMIN))
                .isInstanceOf(BizException.class);
    }

    @Test
    void requireMemberShouldDenyNonMember() {
        when(memberMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> guard.requireMember(1L, 2L, DocMemberRole.READER))
                .isInstanceOf(BizException.class);
    }

    @Test
    void requirePageShouldThrowWhenMissing() {
        when(pageMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> guard.requirePage(9L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(40401);
    }

    @Test
    void requirePageReadShouldThrowWhenInvisible() {
        when(pageMapper.selectById(9L)).thenReturn(page(9L, 1L, "DRAFT", 0));
        when(memberMapper.selectOne(any())).thenReturn(null);
        when(spaceMapper.selectById(1L)).thenReturn(space(1L, "MEMBER", 1));

        assertThatThrownBy(() -> guard.requirePageRead(9L, 2L)).isInstanceOf(BizException.class);
    }

    @Test
    void requirePageReadShouldReturnVisiblePage() {
        when(pageMapper.selectById(9L)).thenReturn(page(9L, 1L, "DRAFT", 0));
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 2L, "READER"));

        assertThat(guard.requirePageRead(9L, 2L).getId()).isEqualTo(9L);
    }

    @Test
    void requirePageEditShouldThrowWhenNotEditable() {
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 2L, "READER"));

        assertThatThrownBy(() -> guard.requirePageEdit(page(9L, 1L, "DRAFT", 0), 2L))
                .isInstanceOf(BizException.class);
    }

    @Test
    void requirePageEditShouldAllowEditor() {
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 2L, "EDITOR"));

        assertThatCode(() -> guard.requirePageEdit(page(9L, 1L, "DRAFT", 0), 2L))
                .doesNotThrowAnyException();
    }

    @Test
    void canReadShouldAllowSpaceMemberOnNormalPage() {
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 2L, "READER"));

        assertThat(guard.canRead(page(9L, 1L, "DRAFT", 0), 2L)).isTrue();
    }

    @Test
    void canReadShouldAllowAdminOnRestrictedPage() {
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 2L, "ADMIN"));

        assertThat(guard.canRead(page(9L, 1L, "DRAFT", 1), 2L)).isTrue();
    }

    @Test
    void canReadShouldAllowReaderWithAclOnRestrictedPage() {
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 2L, "READER"));
        when(aclMapper.selectOne(any())).thenReturn(acl(9L, 2L, "READ"));

        assertThat(guard.canRead(page(9L, 1L, "DRAFT", 1), 2L)).isTrue();
    }

    @Test
    void canReadShouldDenyReaderWithoutAclOnRestrictedPage() {
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 2L, "READER"));
        when(aclMapper.selectOne(any())).thenReturn(null);

        assertThat(guard.canRead(page(9L, 1L, "DRAFT", 1), 2L)).isFalse();
    }

    @Test
    void canReadShouldAllowOutsiderWithAclOnActiveRestrictedPage() {
        when(memberMapper.selectOne(any())).thenReturn(null);
        when(spaceMapper.selectById(1L)).thenReturn(space(1L, "MEMBER", 1));
        when(aclMapper.selectOne(any())).thenReturn(acl(9L, 2L, "READ"));

        assertThat(guard.canRead(page(9L, 1L, "DRAFT", 1), 2L)).isTrue();
    }

    @Test
    void canReadShouldDenyOutsiderWhenSpaceArchived() {
        when(memberMapper.selectOne(any())).thenReturn(null);
        when(spaceMapper.selectById(1L)).thenReturn(space(1L, "MEMBER", 0));
        when(aclMapper.selectOne(any())).thenReturn(acl(9L, 2L, "READ"));

        assertThat(guard.canRead(page(9L, 1L, "DRAFT", 1), 2L)).isFalse();
    }

    @Test
    void canReadShouldDenyOutsiderWhenRestrictedSpaceMissing() {
        when(memberMapper.selectOne(any())).thenReturn(null);
        when(spaceMapper.selectById(1L)).thenReturn(null);
        when(aclMapper.selectOne(any())).thenReturn(acl(9L, 2L, "READ"));

        assertThat(guard.canRead(page(9L, 1L, "DRAFT", 1), 2L)).isFalse();
    }

    @Test
    void canReadShouldAllowOutsiderOnPublishedPageInPublicSpace() {
        when(memberMapper.selectOne(any())).thenReturn(null);
        when(spaceMapper.selectById(1L)).thenReturn(space(1L, "PUBLIC", 1));

        assertThat(guard.canRead(page(9L, 1L, "PUBLISHED", 0), 2L)).isTrue();
    }

    @Test
    void canReadShouldDenyOutsiderOnDraftPageInPublicSpace() {
        when(memberMapper.selectOne(any())).thenReturn(null);
        when(spaceMapper.selectById(1L)).thenReturn(space(1L, "PUBLIC", 1));

        assertThat(guard.canRead(page(9L, 1L, "DRAFT", 0), 2L)).isFalse();
    }

    @Test
    void canReadShouldDenyOutsiderWhenSpaceMissing() {
        when(memberMapper.selectOne(any())).thenReturn(null);
        when(spaceMapper.selectById(1L)).thenReturn(null);

        assertThat(guard.canRead(page(9L, 1L, "PUBLISHED", 0), 2L)).isFalse();
    }

    @Test
    void canEditShouldAllowAdminOnRestrictedPage() {
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 2L, "ADMIN"));

        assertThat(guard.canEdit(page(9L, 1L, "DRAFT", 1), 2L)).isTrue();
    }

    @Test
    void canEditShouldAllowEditorWithEditAclOnRestrictedPage() {
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 2L, "EDITOR"));
        when(aclMapper.selectOne(any())).thenReturn(acl(9L, 2L, "EDIT"));

        assertThat(guard.canEdit(page(9L, 1L, "DRAFT", 1), 2L)).isTrue();
    }

    @Test
    void canEditShouldDenyReadOnlyAclOnRestrictedPage() {
        when(memberMapper.selectOne(any())).thenReturn(null);
        when(aclMapper.selectOne(any())).thenReturn(acl(9L, 2L, "READ"));

        assertThat(guard.canEdit(page(9L, 1L, "DRAFT", 1), 2L)).isFalse();
    }

    @Test
    void canEditShouldDenyEditorWithoutAclOnRestrictedPage() {
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 2L, "EDITOR"));
        when(aclMapper.selectOne(any())).thenReturn(null);

        assertThat(guard.canEdit(page(9L, 1L, "DRAFT", 1), 2L)).isFalse();
    }

    @Test
    void canEditShouldAllowEditorOnNormalPageAndDenyReader() {
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 2L, "EDITOR"));
        assertThat(guard.canEdit(page(9L, 1L, "DRAFT", 0), 2L)).isTrue();

        when(memberMapper.selectOne(any())).thenReturn(member(1L, 2L, "READER"));
        assertThat(guard.canEdit(page(9L, 1L, "DRAFT", 0), 2L)).isFalse();
    }

    @Test
    void canEditShouldAllowOutsiderWithEditAclOnNormalPage() {
        when(memberMapper.selectOne(any())).thenReturn(null);
        when(aclMapper.selectOne(any())).thenReturn(acl(9L, 2L, "EDIT"));

        assertThat(guard.canEdit(page(9L, 1L, "DRAFT", 0), 2L)).isTrue();
    }

    @Test
    void canEditShouldDenyOutsiderWithoutAclOnNormalPage() {
        when(memberMapper.selectOne(any())).thenReturn(null);
        when(aclMapper.selectOne(any())).thenReturn(null);

        assertThat(guard.canEdit(page(9L, 1L, "DRAFT", 0), 2L)).isFalse();
    }

    @Test
    void canCommentShouldRequireReadableAndMembershipOrAcl() {
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 2L, "READER"));
        when(aclMapper.selectOne(any())).thenReturn(null);
        assertThat(guard.canComment(page(9L, 1L, "DRAFT", 0), 2L)).isTrue();

        when(memberMapper.selectOne(any())).thenReturn(null);
        when(spaceMapper.selectById(1L)).thenReturn(space(1L, "PUBLIC", 1));
        when(aclMapper.selectOne(any())).thenReturn(null);
        assertThat(guard.canComment(page(9L, 1L, "PUBLISHED", 0), 2L)).isFalse();

        when(aclMapper.selectOne(any())).thenReturn(acl(9L, 2L, "READ"));
        assertThat(guard.canComment(page(9L, 1L, "PUBLISHED", 0), 2L)).isTrue();
    }

    @Test
    void aclRoleOfShouldIgnoreIllegalValues() {
        when(aclMapper.selectOne(any())).thenReturn(null);
        assertThat(guard.aclRoleOf(9L, 2L)).isNull();

        when(aclMapper.selectOne(any())).thenReturn(acl(9L, 2L, "OWNER"));
        assertThat(guard.aclRoleOf(9L, 2L)).isNull();

        when(aclMapper.selectOne(any())).thenReturn(acl(9L, 2L, "EDIT"));
        assertThat(guard.aclRoleOf(9L, 2L)).isEqualTo(DocAclRole.EDIT);
    }

    @Test
    void isRestrictedShouldHandleNullAndZero() {
        assertThat(guard.isRestricted(page(9L, 1L, "DRAFT", null))).isFalse();
        assertThat(guard.isRestricted(page(9L, 1L, "DRAFT", 0))).isFalse();
        assertThat(guard.isRestricted(page(9L, 1L, "DRAFT", 1))).isTrue();
    }

    @Test
    void filterReadableShouldReturnInputWhenEmpty() {
        assertThat(guard.filterReadable(null, 2L)).isNull();
        assertThat(guard.filterReadable(List.of(), 2L)).isEmpty();
    }

    @Test
    void filterReadableShouldHideRestrictedPagesWithoutAcl() {
        DocPage normalPublished = page(1L, 10L, "PUBLISHED", 0);
        DocPage normalDraft = page(2L, 10L, "DRAFT", 0);
        DocPage restricted = page(3L, 10L, "DRAFT", 1);
        when(memberMapper.selectOne(any())).thenReturn(member(10L, 2L, "READER"));
        when(aclMapper.selectList(any())).thenReturn(List.of(acl(3L, 2L, "READ")));

        List<DocPage> visible = guard.filterReadable(List.of(normalPublished, normalDraft, restricted), 2L);

        assertThat(visible).containsExactly(normalPublished, normalDraft, restricted);
    }

    @Test
    void filterReadableShouldHideRestrictedPagesForOutsiderButShowPublished() {
        DocPage published = page(1L, 10L, "PUBLISHED", 0);
        DocPage draft = page(2L, 10L, "DRAFT", 0);
        DocPage restricted = page(3L, 10L, "DRAFT", 1);
        when(memberMapper.selectOne(any())).thenReturn(null);
        when(aclMapper.selectList(any())).thenReturn(List.of());

        assertThat(guard.filterReadable(List.of(published, draft, restricted), 2L))
                .containsExactly(published);
    }

    @Test
    void filterReadableShouldReturnAllForAdmin() {
        DocPage restricted = page(3L, 10L, "DRAFT", 1);
        DocPage draft = page(2L, 10L, "DRAFT", 0);
        when(memberMapper.selectOne(any())).thenReturn(member(10L, 2L, "ADMIN"));

        assertThat(guard.filterReadable(List.of(restricted, draft), 2L))
                .containsExactly(restricted, draft);
    }

    @Test
    void filterReadableShouldSkipAclQueryWhenNoRestrictedPage() {
        DocPage published = page(1L, 10L, "PUBLISHED", 0);
        when(memberMapper.selectOne(any())).thenReturn(null);

        assertThat(guard.filterReadable(List.of(published), 2L)).containsExactly(published);
    }

    @Test
    void canManageShouldRequireAdmin() {
        when(memberMapper.selectOne(any())).thenReturn(member(1L, 2L, "ADMIN"));
        assertThat(guard.canManage(page(9L, 1L, "DRAFT", 0), 2L)).isTrue();

        when(memberMapper.selectOne(any())).thenReturn(member(1L, 2L, "EDITOR"));
        assertThat(guard.canManage(page(9L, 1L, "DRAFT", 0), 2L)).isFalse();

        when(memberMapper.selectOne(any())).thenReturn(null);
        assertThat(guard.canManage(page(9L, 1L, "DRAFT", 0), 2L)).isFalse();
    }

    @Test
    void isPublicAndActiveShouldRequirePublicVisibilityAndActiveStatus() {
        assertThat(guard.isPublicAndActive(space(1L, "PUBLIC", 1))).isTrue();
        assertThat(guard.isPublicAndActive(space(1L, "PUBLIC", 0))).isFalse();
        assertThat(guard.isPublicAndActive(space(1L, "MEMBER", 1))).isFalse();
        assertThat(guard.isPublicAndActive(space(1L, "PUBLIC", null))).isFalse();
    }

    /**
     * 构造空间。
     *
     * @param id         空间ID
     * @param visibility 可见性
     * @param status     状态
     * @return 空间实体
     */
    private DocSpace space(Long id, String visibility, Integer status) {
        DocSpace space = new DocSpace();
        space.setId(id);
        space.setVisibility(visibility);
        space.setStatus(status);
        return space;
    }

    /**
     * 构造成员。
     *
     * @param spaceId 空间ID
     * @param userId  用户ID
     * @param role    角色文本
     * @return 成员实体
     */
    private DocSpaceMember member(Long spaceId, Long userId, String role) {
        DocSpaceMember member = new DocSpaceMember();
        member.setSpaceId(spaceId);
        member.setUserId(userId);
        member.setMemberRole(role);
        return member;
    }

    /**
     * 构造页面。
     *
     * @param id           页面ID
     * @param spaceId      空间ID
     * @param status       状态
     * @param isRestricted 页面级权限开关
     * @return 页面实体
     */
    private DocPage page(Long id, Long spaceId, String status, Integer isRestricted) {
        DocPage page = new DocPage();
        page.setId(id);
        page.setSpaceId(spaceId);
        page.setStatus(status);
        page.setIsRestricted(isRestricted);
        return page;
    }

    /**
     * 构造页面 ACL。
     *
     * @param pageId 页面ID
     * @param userId 用户ID
     * @param role   ACL 角色
     * @return ACL 实体
     */
    private DocPageAcl acl(Long pageId, Long userId, String role) {
        DocPageAcl acl = new DocPageAcl();
        acl.setPageId(pageId);
        acl.setUserId(userId);
        acl.setAclRole(role);
        return acl;
    }
}
