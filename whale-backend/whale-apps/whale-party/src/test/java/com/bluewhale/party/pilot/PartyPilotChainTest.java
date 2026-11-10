package com.bluewhale.party.pilot;

import com.bluewhale.party.activity.application.command.ActivitySaveCommand;
import com.bluewhale.party.activity.application.command.ActivitySigninCommand;
import com.bluewhale.party.activity.application.service.impl.ActivityAppServiceImpl;
import com.bluewhale.party.activity.application.view.ActivityView;
import com.bluewhale.party.activity.domain.model.ActivitySignin;
import com.bluewhale.party.activity.domain.model.PartyActivity;
import com.bluewhale.party.activity.domain.repository.PartyActivityRepository;
import com.bluewhale.party.branch.application.command.BranchSaveCommand;
import com.bluewhale.party.branch.application.service.impl.BranchAppServiceImpl;
import com.bluewhale.party.branch.application.view.BranchView;
import com.bluewhale.party.branch.domain.model.Branch;
import com.bluewhale.party.branch.domain.model.BranchStatus;
import com.bluewhale.party.branch.domain.repository.BranchRepository;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.sequence.PartySequenceService;
import com.bluewhale.party.config.PartyProperties;
import com.bluewhale.party.fee.application.command.FeePayCommand;
import com.bluewhale.party.fee.application.service.impl.FeeAppServiceImpl;
import com.bluewhale.party.fee.application.view.FeeBillView;
import com.bluewhale.party.fee.application.view.FeeGenerateResultView;
import com.bluewhale.party.fee.application.view.FeeMetricView;
import com.bluewhale.party.fee.domain.model.FeeBill;
import com.bluewhale.party.fee.domain.model.FeeRule;
import com.bluewhale.party.fee.domain.repository.FeeBillRepository;
import com.bluewhale.party.fee.domain.repository.FeeRuleRepository;
import com.bluewhale.party.member.application.command.MemberSaveCommand;
import com.bluewhale.party.member.application.service.impl.MemberAppServiceImpl;
import com.bluewhale.party.member.application.view.MemberMetricView;
import com.bluewhale.party.member.application.view.MemberView;
import com.bluewhale.party.member.domain.model.PartyMember;
import com.bluewhale.party.member.domain.repository.PartyMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 试点全链：建支部 → 录党员 → 组织生活 → 收党费。
 *
 * <p>四个应用服务串起来跑一遍，验证跨上下文的字段约定（支部 ID、党员 ID、
 * 缴费月份 yyyyMM）在真实调用链上闭合，而不是各自单测里自洽。</p>
 *
 * @author BlueWhale
 */
class PartyPilotChainTest {

    private BranchRepository branchRepository;

    private PartyMemberRepository memberRepository;

    private PartyActivityRepository partyActivityRepository;

    private FeeRuleRepository feeRuleRepository;

    private FeeBillRepository feeBillRepository;

    private BranchAppServiceImpl branchAppService;

    private MemberAppServiceImpl memberAppService;

    private ActivityAppServiceImpl activityAppService;

    private FeeAppServiceImpl feeAppService;

    private PartyActor admin;

    private final AtomicReference<PartyActivity> activityHolder = new AtomicReference<>();

    private final List<ActivitySignin> signins = new ArrayList<>();

    private final AtomicReference<FeeBill> billHolder = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        branchRepository = mock(BranchRepository.class);
        memberRepository = mock(PartyMemberRepository.class);
        partyActivityRepository = mock(PartyActivityRepository.class);
        feeRuleRepository = mock(FeeRuleRepository.class);
        feeBillRepository = mock(FeeBillRepository.class);
        PartySequenceService sequenceService = mock(PartySequenceService.class);
        PartyProperties properties = new PartyProperties();
        branchAppService = new BranchAppServiceImpl(branchRepository, memberRepository, sequenceService, properties);
        memberAppService = new MemberAppServiceImpl(memberRepository, branchRepository, sequenceService, properties);
        activityAppService = new ActivityAppServiceImpl(partyActivityRepository, branchRepository,
                memberRepository, properties);
        feeAppService = new FeeAppServiceImpl(feeRuleRepository, feeBillRepository, memberRepository, properties);
        admin = new PartyActor(9L, "padmin", Set.of("party_admin"), Set.of());
        when(sequenceService.nextBranchCode()).thenReturn("BR20260001");
        when(sequenceService.nextMemberNo()).thenReturn("MB20260001");
        stubFeeRule();
    }

    @Test
    @DisplayName("单支部全链走通并产出可对账的看板数据")
    void oneBranchShouldCompleteFullChain() {
        BranchView branch = branchAppService.create(branchCommand(), admin);
        assertEquals("BR20260001", branch.getBranchCode());
        stubBranch();

        stubMember(1L);
        MemberView member = memberAppService.create(memberCommand(), admin);
        assertEquals("MB20260001", member.getMemberNo());
        assertEquals(1L, member.getId());

        stubActivity(1L);
        ActivityView activity = activityAppService.create(activityCommand(), admin);
        assertEquals(1L, activity.getId());
        ActivityView signed = activityAppService.signin(activity.getId(), signinCommand(), admin);
        assertEquals(1L, signed.getPresentCount());

        FeeGenerateResultView generated = feeAppService.generate("202611", 1L, admin);
        assertEquals(1, generated.getCreated());
        FeeBillView paid = feeAppService.pay(billHolder.get().getId(), payCommand(), admin);
        assertEquals("PAID", paid.getStatus());

        MemberMetricView structures = memberAppService.metrics(LocalDate.of(2026, 11, 6), 1L);
        assertEquals(1L, structures.getTotal());
        FeeMetricView fee = feeAppService.metrics("202611", 1L);
        assertEquals(0, fee.getDueAmount().compareTo(new BigDecimal("5.00")));
        assertEquals(0, fee.getPaidAmount().compareTo(new BigDecimal("5.00")));
    }

    /**
     * 桩定支部查询与保存。
     */
    private void stubBranch() {
        Branch branch = new Branch();
        branch.setId(1L);
        branch.setBranchCode("BR20260001");
        branch.setBranchName("第一党支部");
        branch.setBranchLevel(1);
        branch.setParentId(0L);
        branch.setStatus(BranchStatus.ACTIVE);
        when(branchRepository.findById(1L)).thenReturn(Optional.of(branch));
        when(branchRepository.listAll()).thenReturn(List.of(branch));
        when(branchRepository.save(any(Branch.class))).thenAnswer(invocation -> {
            Branch saved = invocation.getArgument(0);
            saved.setId(1L);
            return 1L;
        });
    }

    /**
     * 桩定党员查询与保存。
     *
     * @param memberId 党员 ID
     */
    private void stubMember(Long memberId) {
        when(memberRepository.findByEmployeeNo("E1001")).thenReturn(Optional.empty());
        when(memberRepository.findById(memberId)).thenAnswer(invocation -> {
            PartyMember stored = new PartyMember();
            stored.setId(memberId);
            stored.setMemberNo("MB20260001");
            stored.setMemberName("张三");
            stored.setBranchId(1L);
            stored.setStatus(com.bluewhale.party.member.domain.model.MemberStatus.ACTIVE);
            stored.setDuesBase(new BigDecimal("1000.00"));
            stored.setJoinPartyDate(LocalDate.of(2020, 6, 1));
            return Optional.of(stored);
        });
        when(memberRepository.listPayable(1L)).thenAnswer(invocation -> memberRepository.findById(memberId)
                .map(List::of).orElse(List.of()));
        when(memberRepository.listActive(1L)).thenAnswer(invocation -> memberRepository.findById(memberId)
                .map(List::of).orElse(List.of()));
        when(memberRepository.save(any(PartyMember.class))).thenAnswer(invocation -> {
            PartyMember saved = invocation.getArgument(0);
            saved.setId(memberId);
            return memberId;
        });
    }

    /**
     * 桩定组织生活查询、保存与签到。
     *
     * @param activityId 活动 ID
     */
    private void stubActivity(Long activityId) {
        when(partyActivityRepository.save(any(PartyActivity.class))).thenAnswer(invocation -> {
            PartyActivity saved = invocation.getArgument(0);
            saved.setId(activityId);
            activityHolder.set(saved);
            return activityId;
        });
        when(partyActivityRepository.findById(activityId))
                .thenAnswer(invocation -> Optional.ofNullable(activityHolder.get()));
        when(partyActivityRepository.listSignins(activityId)).thenAnswer(invocation -> signins);
        when(partyActivityRepository.saveSignin(any(ActivitySignin.class))).thenAnswer(invocation -> {
            signins.add(invocation.getArgument(0));
            return 1L;
        });
        when(partyActivityRepository.countPresent(activityId)).thenAnswer(invocation ->
                signins.stream().filter(ActivitySignin::isPresent).count());
    }

    /**
     * 构造支部命令。
     *
     * @return 命令
     */
    private BranchSaveCommand branchCommand() {
        BranchSaveCommand command = new BranchSaveCommand();
        command.setBranchName("第一党支部");
        command.setSecretaryName("张三");
        command.setSecretaryUserId(101L);
        command.setEstablishedDate(LocalDate.of(2022, 6, 1));
        return command;
    }

    /**
     * 构造党员命令。
     *
     * @return 命令
     */
    private MemberSaveCommand memberCommand() {
        MemberSaveCommand command = new MemberSaveCommand();
        command.setEmployeeNo("E1001");
        command.setMemberName("张三");
        command.setBranchId(1L);
        command.setUserId(101L);
        command.setPoliticalStatus("FULL_MEMBER");
        command.setJoinPartyDate(LocalDate.of(2020, 6, 1));
        command.setDuesBase(new BigDecimal("1000.00"));
        return command;
    }

    /**
     * 构造组织生活命令。
     *
     * @return 命令
     */
    private ActivitySaveCommand activityCommand() {
        ActivitySaveCommand command = new ActivitySaveCommand();
        command.setBranchId(1L);
        command.setTitle("十一月份党员大会");
        command.setActivityType("MEMBER_ASSEMBLY");
        command.setPlannedDate(LocalDate.of(2026, 11, 10));
        command.setExpectedCount(1);
        return command;
    }

    /**
     * 构造签到命令。
     *
     * @return 命令
     */
    private ActivitySigninCommand signinCommand() {
        ActivitySigninCommand command = new ActivitySigninCommand();
        ActivitySigninCommand.SigninItem item = new ActivitySigninCommand.SigninItem();
        item.setMemberId(1L);
        item.setPresent(Boolean.TRUE);
        command.setItems(List.of(item));
        return command;
    }

    /**
     * 构造缴费命令。
     *
     * @return 命令
     */
    private FeePayCommand payCommand() {
        FeePayCommand command = new FeePayCommand();
        command.setAmount(new BigDecimal("5.00"));
        command.setPaidDate(LocalDate.of(2026, 11, 12));
        return command;
    }

    /**
     * 桩定党费档位与账单。
     */
    private void stubFeeRule() {
        FeeRule rule = new FeeRule();
        rule.setId(1L);
        rule.setRuleName("3000 元以下");
        rule.setMinBase(BigDecimal.ZERO);
        rule.setMaxBase(BigDecimal.valueOf(3000));
        rule.setRate(new BigDecimal("0.005"));
        rule.setStatus("ACTIVE");
        rule.setSortNo(1);
        when(feeRuleRepository.listActive()).thenReturn(List.of(rule));
        when(feeBillRepository.findByMemberAndPeriod(any(), any())).thenReturn(Optional.empty());
        when(feeBillRepository.save(any(FeeBill.class))).thenAnswer(invocation -> {
            FeeBill saved = invocation.getArgument(0);
            saved.setId(1L);
            billHolder.set(saved);
            return 1L;
        });
        when(feeBillRepository.findById(1L))
                .thenAnswer(invocation -> Optional.ofNullable(billHolder.get()));
        when(feeBillRepository.listByPeriod(any(), any()))
                .thenAnswer(invocation -> billHolder.get() == null ? List.of() : List.of(billHolder.get()));
    }
}
