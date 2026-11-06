package com.bluewhale.party.branch.application.service.impl;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.party.branch.application.command.BranchSaveCommand;
import com.bluewhale.party.branch.application.view.BranchView;
import com.bluewhale.party.branch.domain.model.Branch;
import com.bluewhale.party.branch.domain.model.BranchQuery;
import com.bluewhale.party.branch.domain.model.BranchStatus;
import com.bluewhale.party.branch.domain.repository.BranchRepository;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyErrorCode;
import com.bluewhale.party.common.sequence.PartySequenceService;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.member.domain.repository.PartyMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支部台账应用服务：编号生成、层级推导、成环拦截、停用前置校验。
 *
 * @author BlueWhale
 */
class BranchAppServiceImplTest {

    private BranchRepository branchRepository;

    private PartyMemberRepository memberRepository;

    private PartySequenceService sequenceService;

    private BranchAppServiceImpl service;

    private PartyActor actor;

    @BeforeEach
    void setUp() {
        branchRepository = mock(BranchRepository.class);
        memberRepository = mock(PartyMemberRepository.class);
        sequenceService = mock(PartySequenceService.class);
        service = new BranchAppServiceImpl(branchRepository, memberRepository, sequenceService,
                new PartyProperties());
        actor = new PartyActor(9L, "padmin", Set.of("party_admin"), Set.of());
    }

    @Test
    @DisplayName("新建一级支部：编码取流水、层级为 1")
    void createShouldGenerateCodeAndRootLevel() {
        when(sequenceService.nextBranchCode()).thenReturn("BR20260001");
        when(branchRepository.findByCode("BR20260001")).thenReturn(Optional.empty());
        when(branchRepository.save(any(Branch.class))).thenAnswer(invocation -> {
            Branch saved = invocation.getArgument(0);
            saved.setId(100L);
            return 100L;
        });

        BranchView view = service.create(command(null), actor);

        assertEquals("BR20260001", view.getBranchCode());
        assertEquals(1, view.getBranchLevel());
        assertEquals("ACTIVE", view.getStatus());
        ArgumentCaptor<Branch> captor = ArgumentCaptor.forClass(Branch.class);
        verify(branchRepository).save(captor.capture());
        assertEquals(0L, captor.getValue().getParentId());
    }

    @Test
    @DisplayName("新建下级支部：层级 = 上级层级 + 1")
    void createShouldDeriveLevelFromParent() {
        when(branchRepository.findById(5L)).thenReturn(Optional.of(branch(5L, 2, BranchStatus.ACTIVE, 0L)));
        when(sequenceService.nextBranchCode()).thenReturn("BR20260002");
        when(branchRepository.save(any(Branch.class))).thenAnswer(invocation -> {
            Branch saved = invocation.getArgument(0);
            saved.setId(101L);
            return 101L;
        });

        BranchView view = service.create(command(5L), actor);

        assertEquals(3, view.getBranchLevel());
        assertEquals(5L, view.getParentId());
    }

    @Test
    @DisplayName("上级支部已停用：不允许在其下新建")
    void createUnderSuspendedParentShouldFail() {
        when(branchRepository.findById(5L)).thenReturn(Optional.of(branch(5L, 1, BranchStatus.SUSPENDED, 0L)));

        BizException exception = assertThrows(BizException.class, () -> service.create(command(5L), actor));
        assertEquals(PartyErrorCode.BRANCH_INACTIVE, exception.getCode());
        verify(branchRepository, never()).save(any(Branch.class));
    }

    @Test
    @DisplayName("层级超过上限被拒")
    void createShouldRejectExceededDepth() {
        PartyProperties properties = new PartyProperties();
        properties.setBranchMaxDepth(2);
        service = new BranchAppServiceImpl(branchRepository, memberRepository, sequenceService, properties);
        when(branchRepository.findById(5L)).thenReturn(Optional.of(branch(5L, 2, BranchStatus.ACTIVE, 0L)));

        BizException exception = assertThrows(BizException.class, () -> service.create(command(5L), actor));
        assertEquals(PartyErrorCode.BRANCH_PARENT_CYCLE, exception.getCode());
    }

    @Test
    @DisplayName("把支部挂到自己的下级上：识别为成环")
    void updateShouldRejectCycle() {
        Branch self = branch(1L, 1, BranchStatus.ACTIVE, 0L);
        Branch child = branch(2L, 2, BranchStatus.ACTIVE, 1L);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(self));
        when(branchRepository.findById(2L)).thenReturn(Optional.of(child));

        BizException exception = assertThrows(BizException.class, () -> service.update(1L, command(2L), actor));
        assertEquals(PartyErrorCode.BRANCH_PARENT_CYCLE, exception.getCode());
    }

    @Test
    @DisplayName("调整上级后层级变更，下级层级级联跟随")
    void updateShouldCascadeLevelToDescendants() {
        Branch self = branch(1L, 1, BranchStatus.ACTIVE, 0L);
        Branch newParent = branch(9L, 3, BranchStatus.ACTIVE, 0L);
        Branch child = branch(2L, 2, BranchStatus.ACTIVE, 1L);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(self));
        when(branchRepository.findById(9L)).thenReturn(Optional.of(newParent));
        when(branchRepository.listByParent(1L)).thenReturn(List.of(child));
        when(branchRepository.listByParent(2L)).thenReturn(List.of());

        service.update(1L, command(9L), actor);

        assertEquals(4, self.getBranchLevel());
        // 上级从 1 层移到 3 层，自身变 4 层；下级原 2 层随增量 +3 变 5 层
        assertEquals(5, child.getBranchLevel());
        verify(branchRepository).save(child);
    }

    @Test
    @DisplayName("停用前必须转出党员")
    void suspendShouldFailWhenMembersRemain() {
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch(1L, 1, BranchStatus.ACTIVE, 0L)));
        when(branchRepository.countByParent(1L)).thenReturn(0L);
        when(memberRepository.countActiveByBranch(1L)).thenReturn(3L);

        BizException exception = assertThrows(BizException.class, () -> service.changeStatus(1L, false, actor));
        assertEquals(PartyErrorCode.BRANCH_STATE_CONFLICT, exception.getCode());
        verify(branchRepository, never()).save(any(Branch.class));
    }

    @Test
    @DisplayName("停用前必须有下级支部处理干净")
    void suspendShouldFailWhenChildrenRemain() {
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch(1L, 1, BranchStatus.ACTIVE, 0L)));
        when(branchRepository.countByParent(1L)).thenReturn(2L);

        BizException exception = assertThrows(BizException.class, () -> service.changeStatus(1L, false, actor));
        assertEquals(PartyErrorCode.BRANCH_STATE_CONFLICT, exception.getCode());
    }

    @Test
    @DisplayName("停用成功后状态为 SUSPENDED")
    void suspendShouldSucceedWhenEmpty() {
        Branch target = branch(1L, 1, BranchStatus.ACTIVE, 0L);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(target));
        when(branchRepository.countByParent(1L)).thenReturn(0L);
        when(memberRepository.countActiveByBranch(1L)).thenReturn(0L);

        BranchView view = service.changeStatus(1L, false, actor);

        assertEquals("SUSPENDED", view.getStatus());
        assertEquals(BranchStatus.SUSPENDED, target.getStatus());
    }

    @Test
    @DisplayName("分页回填上级名称")
    void pageShouldFillParentName() {
        Branch parent = branch(1L, 1, BranchStatus.ACTIVE, 0L);
        parent.setBranchName("一级支部");
        Branch child = branch(2L, 2, BranchStatus.ACTIVE, 1L);
        when(branchRepository.page(any(BranchQuery.class), anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(child));
        when(branchRepository.count(any(BranchQuery.class))).thenReturn(1L);
        when(branchRepository.listAll()).thenReturn(List.of(parent, child));

        PageResult<BranchView> page = service.page(new BranchQuery(), actor);

        assertEquals(1, page.getRecords().size());
        assertEquals("一级支部", page.getRecords().get(0).getParentName());
        assertTrue(page.getTotal() == 1L);
    }

    /**
     * 构造保存命令。
     *
     * @param parentId 上级支部 ID
     * @return 命令
     */
    private BranchSaveCommand command(Long parentId) {
        BranchSaveCommand command = new BranchSaveCommand();
        command.setBranchName("第一党支部");
        command.setParentId(parentId);
        command.setSortNo(0);
        return command;
    }

    /**
     * 构造支部。
     *
     * @param id 主键
     * @param level 层级
     * @param status 状态
     * @param parentId 上级 ID
     * @return 支部
     */
    private Branch branch(Long id, int level, BranchStatus status, Long parentId) {
        Branch branch = new Branch();
        branch.setId(id);
        branch.setBranchCode("BR2026" + id);
        branch.setBranchName("支部" + id);
        branch.setBranchLevel(level);
        branch.setStatus(status);
        branch.setParentId(parentId);
        return branch;
    }
}
