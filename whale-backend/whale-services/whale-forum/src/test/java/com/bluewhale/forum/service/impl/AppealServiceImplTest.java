package com.bluewhale.forum.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.AppealCreateRequest;
import com.bluewhale.forum.dto.AppealHandleRequest;
import com.bluewhale.forum.dto.AppealVO;
import com.bluewhale.forum.entity.ForumAppeal;
import com.bluewhale.forum.entity.ForumAppealMaterial;
import com.bluewhale.forum.entity.ForumPost;
import com.bluewhale.forum.entity.ForumThread;
import com.bluewhale.forum.entity.ForumUserBan;
import com.bluewhale.forum.mapper.ForumAppealMapper;
import com.bluewhale.forum.mapper.ForumAppealMaterialMapper;
import com.bluewhale.forum.mapper.ForumPostMapper;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.service.BanService;
import com.bluewhale.forum.service.ForumNotifyService;
import com.bluewhale.forum.support.ForumFileMetadataClient;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 申诉单测：受理范围、归属校验、重复待审、复核与解禁。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class AppealServiceImplTest {

    @Mock
    private ForumAppealMapper appealMapper;

    @Mock
    private ForumAppealMaterialMapper materialMapper;

    @Mock
    private ForumFileMetadataClient fileMetadataClient;

    @Mock
    private ForumThreadMapper threadMapper;

    @Mock
    private ForumPostMapper postMapper;

    @Mock
    private BanService banService;

    @Mock
    private ForumNotifyService notifyService;

    @Spy
    private ForumProperties properties = new ForumProperties();

    @InjectMocks
    private AppealServiceImpl appealService;

    @BeforeAll
    static void initTableInfo() {
        // 纯单测没有 MyBatis 容器，LambdaQueryWrapper 解析字段前需手动登记表元数据
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ForumAppeal.class);
        TableInfoHelper.initTableInfo(assistant, ForumAppealMaterial.class);
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
    @DisplayName("已处置帖子：本人可申诉，落 PENDING 工单")
    void submitsRemovedOwnThread() {
        when(threadMapper.selectById(11L)).thenReturn(thread(11L, 100L, "REMOVED"));
        when(appealMapper.selectCount(any())).thenReturn(0L);
        when(appealMapper.insert(any(ForumAppeal.class))).thenReturn(1);

        AppealVO vo = appealService.submit(request("THREAD", 11L), actor());

        assertThat(vo.getStatus()).isEqualTo("PENDING");
        assertThat(vo.getTargetType()).isEqualTo("THREAD");
        ArgumentCaptor<ForumAppeal> captor = ArgumentCaptor.forClass(ForumAppeal.class);
        verify(appealMapper).insert(captor.capture());
        ForumAppeal saved = captor.getValue();
        assertThat(saved.getTenantId()).isEqualTo(1L);
        assertThat(saved.getUserId()).isEqualTo(100L);
        assertThat(saved.getTargetId()).isEqualTo(11L);
        assertThat(saved.getHandlerId()).isNull();
    }

    @Test
    @DisplayName("他人的帖子：拒绝申诉且不落库")
    void rejectsOthersThread() {
        when(threadMapper.selectById(11L)).thenReturn(thread(11L, 200L, "REMOVED"));

        assertThatThrownBy(() -> appealService.submit(request("THREAD", 11L), actor()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("本人");
        verify(appealMapper, never()).insert(any(ForumAppeal.class));
    }

    @Test
    @DisplayName("未处置帖子：返回无需申诉")
    void rejectsUnremovedThread() {
        when(threadMapper.selectById(11L)).thenReturn(thread(11L, 100L, "PUBLISHED"));

        assertThatThrownBy(() -> appealService.submit(request("THREAD", 11L), actor()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无需申诉");
    }

    @Test
    @DisplayName("已处置楼层：本人可申诉")
    void submitsRemovedOwnPost() {
        ForumPost post = new ForumPost();
        post.setId(21L);
        post.setAuthorId(100L);
        post.setStatus("REMOVED");
        when(postMapper.selectById(21L)).thenReturn(post);
        when(appealMapper.selectCount(any())).thenReturn(0L);
        when(appealMapper.insert(any(ForumAppeal.class))).thenReturn(1);

        assertThat(appealService.submit(request("POST", 21L), actor()).getTargetType())
                .isEqualTo("POST");
    }

    @Test
    @DisplayName("同一对象已有待审申诉：拒绝重复提交")
    void rejectsDuplicatePendingAppeal() {
        when(threadMapper.selectById(11L)).thenReturn(thread(11L, 100L, "REMOVED"));
        when(appealMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> appealService.submit(request("THREAD", 11L), actor()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("重复");
        verify(appealMapper, never()).insert(any(ForumAppeal.class));
    }

    @Test
    @DisplayName("禁言申诉：无生效处置时拒绝，有生效处置可提交")
    void banAppealRequiresActiveBan() {
        when(banService.activeBan(100L)).thenReturn(null);
        assertThatThrownBy(() -> appealService.submit(request("BAN", 100L), actor()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无需申诉");

        ForumUserBan ban = new ForumUserBan();
        ban.setBanType("MUTE");
        when(banService.activeBan(100L)).thenReturn(ban);
        when(appealMapper.selectCount(any())).thenReturn(0L);
        when(appealMapper.insert(any(ForumAppeal.class))).thenReturn(1);
        assertThat(appealService.submit(request("BAN", 100L), actor()).getStatus())
                .isEqualTo("PENDING");
    }

    @Test
    @DisplayName("禁言申诉通过：自动解禁并写通知")
    void approvingBanAppealReleasesBan() {
        when(appealMapper.selectById(31L)).thenReturn(appeal(31L, "BAN", 100L));
        when(appealMapper.updateById(any(ForumAppeal.class))).thenReturn(1);

        AppealVO vo = appealService.handle(31L, handle(true, "材料属实"), manager());

        assertThat(vo.getStatus()).isEqualTo("APPROVED");
        verify(banService).release(100L, 900L);
        verify(notifyService).enqueue(eq(100L), eq("APPEAL"), eq(31L),
                eq("forum:appeal:31:APPROVED"), anyString(), anyString());
    }

    @Test
    @DisplayName("帖子申诉通过：只改工单，不自动恢复内容、不解禁")
    void approvingThreadAppealHasNoSideEffect() {
        when(appealMapper.selectById(32L)).thenReturn(appeal(32L, "THREAD", 100L));
        when(appealMapper.updateById(any(ForumAppeal.class))).thenReturn(1);

        assertThat(appealService.handle(32L, handle(true, "误判"), manager()).getStatus())
                .isEqualTo("APPROVED");
        verify(banService, never()).release(anyLong(), anyLong());
    }

    @Test
    @DisplayName("已复核工单：拒绝二次处理")
    void rejectsAlreadyHandledAppeal() {
        ForumAppeal handled = appeal(33L, "THREAD", 100L);
        handled.setStatus("REJECTED");
        when(appealMapper.selectById(33L)).thenReturn(handled);

        assertThatThrownBy(() -> appealService.handle(33L, handle(true, null), manager()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已处理");
        verify(appealMapper, never()).updateById(any(ForumAppeal.class));
    }

    @Test
    @DisplayName("我的申诉：按提交时间倒序分页并回填总数")
    void myAppealsMapsPage() {
        Page<ForumAppeal> page = new Page<>(1, 20);
        page.setRecords(List.of(appeal(41L, "THREAD", 100L)));
        page.setTotal(1);
        when(appealMapper.selectPage(any(), any())).thenReturn(page);

        var result = appealService.myAppeals(1, 20, actor());

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("申诉材料：按文件服务元数据落库并回填下载地址")
    void materialSnapshotsComeFromFileService() {
        when(threadMapper.selectById(11L)).thenReturn(thread(11L, 100L, "REMOVED"));
        when(appealMapper.selectCount(any())).thenReturn(0L);
        when(appealMapper.insert(any(ForumAppeal.class))).thenReturn(1);
        when(fileMetadataClient.requireOwned(801L, 100L)).thenReturn(meta(801L));
        AppealCreateRequest body = request("THREAD", 11L);
        body.setMaterialFileIds(List.of(801L));

        AppealVO vo = appealService.submit(body, actor());

        assertThat(vo.getMaterials()).hasSize(1);
        assertThat(vo.getMaterials().get(0).getFileName()).isEqualTo("证据.png");
        assertThat(vo.getMaterials().get(0).getDownloadUrl()).isEqualTo("/file/801/download");
        ArgumentCaptor<ForumAppealMaterial> captor = ArgumentCaptor.forClass(ForumAppealMaterial.class);
        verify(materialMapper).insert(captor.capture());
        assertThat(captor.getValue().getFileId()).isEqualTo(801L);
        assertThat(captor.getValue().getSortNo()).isZero();
        assertThat(captor.getValue().getFileSize()).isEqualTo(2048L);
        assertThat(captor.getValue().getContentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("申诉材料：重复文件ID只登记一次")
    void duplicateMaterialIdsAreDeduplicated() {
        when(threadMapper.selectById(11L)).thenReturn(thread(11L, 100L, "REMOVED"));
        when(appealMapper.selectCount(any())).thenReturn(0L);
        when(appealMapper.insert(any(ForumAppeal.class))).thenReturn(1);
        when(fileMetadataClient.requireOwned(801L, 100L)).thenReturn(meta(801L));
        AppealCreateRequest body = request("THREAD", 11L);
        body.setMaterialFileIds(List.of(801L, 801L));

        appealService.submit(body, actor());

        verify(fileMetadataClient).requireOwned(801L, 100L);
        verify(materialMapper).insert(any(ForumAppealMaterial.class));
    }

    @Test
    @DisplayName("申诉材料：超过数量上限时拒绝且不校验文件、不落工单")
    void rejectsTooManyMaterials() {
        when(threadMapper.selectById(11L)).thenReturn(thread(11L, 100L, "REMOVED"));
        when(appealMapper.selectCount(any())).thenReturn(0L);
        properties.setAppealMaterialLimit(1);
        AppealCreateRequest body = request("THREAD", 11L);
        body.setMaterialFileIds(List.of(801L, 802L));

        assertThatThrownBy(() -> appealService.submit(body, actor()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能超过");
        verify(fileMetadataClient, never()).requireOwned(anyLong(), anyLong());
        verify(appealMapper, never()).insert(any(ForumAppeal.class));
    }

    @Test
    @DisplayName("申诉材料：文件校验失败时整个提交回滚（不落工单）")
    void invalidMaterialFailsWholeSubmission() {
        when(threadMapper.selectById(11L)).thenReturn(thread(11L, 100L, "REMOVED"));
        when(appealMapper.selectCount(any())).thenReturn(0L);
        when(fileMetadataClient.requireOwned(801L, 100L))
                .thenThrow(new BizException(ErrorCode.PARAM_ERROR, "只能上传本人的文件作为申诉材料"));
        AppealCreateRequest body = request("THREAD", 11L);
        body.setMaterialFileIds(List.of(801L));

        assertThatThrownBy(() -> appealService.submit(body, actor()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("本人");
        verify(appealMapper, never()).insert(any(ForumAppeal.class));
        verify(materialMapper, never()).insert(any(ForumAppealMaterial.class));
    }

    /**
     * 构造文件元数据。
     *
     * @param fileId 文件ID
     * @return 元数据
     */
    private ForumFileMetadataClient.FileMeta meta(Long fileId) {
        return new ForumFileMetadataClient.FileMeta(fileId, "证据.png", 2048L, "image/png", 100L, 0, 1);
    }

    /**
     * 构造帖子。
     *
     * @param id 帖子ID
     * @param authorId 作者ID
     * @param status 状态
     * @return 帖子
     */
    private ForumThread thread(Long id, Long authorId, String status) {
        ForumThread thread = new ForumThread();
        thread.setId(id);
        thread.setAuthorId(authorId);
        thread.setStatus(status);
        return thread;
    }

    /**
     * 构造工单。
     *
     * @param id 工单ID
     * @param type 对象类型
     * @param userId 申诉人
     * @return 工单
     */
    private ForumAppeal appeal(Long id, String type, Long userId) {
        ForumAppeal appeal = new ForumAppeal();
        appeal.setId(id);
        appeal.setUserId(userId);
        appeal.setTargetType(type);
        appeal.setTargetId(type.equals("BAN") ? userId : 11L);
        appeal.setReason("误判");
        appeal.setStatus("PENDING");
        return appeal;
    }

    /**
     * 构造申诉请求。
     *
     * @param type 对象类型
     * @param targetId 对象ID
     * @return 请求
     */
    private AppealCreateRequest request(String type, Long targetId) {
        AppealCreateRequest body = new AppealCreateRequest();
        body.setTargetType(type);
        body.setTargetId(targetId);
        body.setReason("没有违反版规，请复核");
        return body;
    }

    /**
     * 构造复核请求。
     *
     * @param approved 是否通过
     * @param remark 复核意见
     * @return 请求
     */
    private AppealHandleRequest handle(boolean approved, String remark) {
        AppealHandleRequest body = new AppealHandleRequest();
        body.setApproved(approved);
        body.setRemark(remark);
        return body;
    }

    /**
     * 构造普通用户。
     *
     * @return 操作者
     */
    private ForumActor actor() {
        return new ForumActor(100L, Set.of(ForumPermissions.BROWSE), null);
    }

    /**
     * 构造管理员。
     *
     * @return 操作者
     */
    private ForumActor manager() {
        return new ForumActor(900L, Set.of(ForumPermissions.BROWSE, ForumPermissions.MANAGE_DISPOSE), null);
    }
}
