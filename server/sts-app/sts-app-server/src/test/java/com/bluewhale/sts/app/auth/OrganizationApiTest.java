package com.bluewhale.sts.app.auth;

import com.bluewhale.sts.auth.common.AuthProperties;
import com.bluewhale.sts.auth.organization.OrganizationMapper;
import com.bluewhale.sts.auth.relation.RelOrgUserMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 组织域接口的端到端测试 —— 组织树、移动、成员归属、主岗，以及它们与数据范围求值的接线。
 *
 * <p><b>刻意不加 {@code @Transactional}</b>，理由与 {@code ResourceApiTest} 相同：
 * 本类里最要紧的两条性质（软删后同码复活、移动后整棵子树的派生列被重写）
 * 验证的恰恰是"库里真正留下了什么"，测试事务会把未提交的写在同一事务内变得可见，
 * 把这类缺陷完全掩盖。
 *
 * <p>代价是用例会真的写库（h2 内存库，随测试进程消失），因此编码一律带时间戳后缀，
 * 并且每条用例**自己收尾**：先撤关系、再删组织、最后删用户。
 * 组织表与关系表是本轮新接进来的共享数据，留脏数据会直接影响同一次构建里
 * {@code DataScopeApiTest} 与 {@code tree} 类的用例。
 *
 * <p><b>种子账号的口令从 {@link AuthProperties} 读，不写死</b>
 * （见 {@code ADR-0029}）。本类自己造的用户用 {@link #TEST_USER_PASSWORD}，
 * 它是一个**只活在测试进程里**的一次性口令 —— 与被保护的种子口令不是一回事。
 *
 * <p>本类的断言分三层，值得说清楚它们各自在防什么：
 * <ol>
 *   <li><b>契约</b>：路径、错误码、字段名。这一层最便宜，也最先失效；</li>
 *   <li><b>派生列的一致性</b>：{@code orgLevel} / {@code codePath} / {@code fullName}
 *       必须与 {@code parentId} 和各级名称始终一致。它们在库里有三个副本，
 *       任何一次"只改了一半"的写入都只能靠这里的断言看出来；</li>
 *   <li><b>与数据范围求值的接线</b>：{@code DEPT} / {@code DEPT_AND_CHILD}
 *       从"算不出"变成"算得出"是本轮改动的**目的**，而它唯一可见的出口是
 *       {@code GET /api/auth/users/{id}/data-scope}。不测这一层，
 *       整轮改动就只是把两张空表填上了数据。</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrganizationApiTest {

    /**
     * 测试自建用户的口令。只在本进程内使用，用完随 h2 一起消失。
     *
     * <p>不写成 {@code properties.getSeedAdmin().getPassword()} 那样"跟着种子走"，
     * 是因为本类要验证的其中一条正是"这个用户**不是**管理员"——
     * 共用凭据会让两类账号在出问题时无法区分。
     */
    private static final String TEST_USER_PASSWORD = "E2eOrg#Pass2026x";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthProperties properties;

    @Autowired
    private OrganizationMapper organizationMapper;

    @Autowired
    private RelOrgUserMapper relOrgUserMapper;

    /* ==================== 层级：派生列与移动 ==================== */

    @Test
    @DisplayName("建三层 → 派生列与 parentId 一致 → 移动中节点 → 整棵子树的路径/层级/全称被重写")
    void shouldRewriteSubtreeOnMove() throws Exception {
        String token = loginAsAdmin();

        String rootCode = uniqueCode("r");
        String midCode = uniqueCode("m");
        String leafCode = uniqueCode("l");
        long rootId = createOrg(token, rootCode, "端到端根", null);
        long midId = createOrg(token, midCode, "端到端中", rootId);
        long leafId = createOrg(token, leafCode, "端到端叶", midId);

        // 三列派生字段：根为 1 级、路径是"各级祖先主键加斜杠"、全称按层级拼
        mockMvc.perform(get("/api/auth/orgs/" + rootId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orgLevel").value(1))
                .andExpect(jsonPath("$.data.codePath").value("/"))
                .andExpect(jsonPath("$.data.fullName").value("端到端根"))
                // hasChild 是服务端维护的：树之外的分页列表要靠它决定画不画展开箭头
                .andExpect(jsonPath("$.data.hasChild").value(1));

        mockMvc.perform(get("/api/auth/orgs/" + leafId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.orgLevel").value(3))
                .andExpect(jsonPath("$.data.codePath").value("/" + rootId + "/" + midId + "/"))
                .andExpect(jsonPath("$.data.fullName").value("端到端根/端到端中/端到端叶"))
                .andExpect(jsonPath("$.data.hasChild").value(0));

        // 树里确实嵌着：子树是"选层级"用的那份数据，它若与 codePath 不一致，
        // DEPT_AND_CHILD 的求值边界就会与界面上看到的边界脱节
        MvcResult tree = mockMvc.perform(get("/api/auth/orgs/tree")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].orgCode", hasItem(rootCode)))
                .andReturn();
        List<String> childrenOfMid = JsonPath.read(tree.getResponse().getContentAsString(),
                "$..[?(@.orgCode == '" + midCode + "')].children[*].orgCode");
        assertTrue(childrenOfMid.contains(leafCode),
                "叶节点应嵌在中节点之下，实际：" + childrenOfMid);

        // 把中节点提到顶级 —— 这会把叶节点连同一起搬走
        mockMvc.perform(MockMvcRequestBuilders.put("/api/auth/orgs/" + midId + "/parent")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orgLevel").value(1))
                .andExpect(jsonPath("$.data.parentId").value(nullValue()))
                .andExpect(jsonPath("$.data.codePath").value("/"))
                .andExpect(jsonPath("$.data.fullName").value("端到端中"));

        // 关键是**后代**也被重写：层级减 1、路径前缀换掉、全称重新拼
        mockMvc.perform(get("/api/auth/orgs/" + leafId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.orgLevel").value(2))
                .andExpect(jsonPath("$.data.parentId").value((int) midId))
                .andExpect(jsonPath("$.data.codePath").value("/" + midId + "/"))
                .andExpect(jsonPath("$.data.fullName").value("端到端中/端到端叶"));

        // 原父级已经没有下级了 —— 这一步最容易漏，漏了的症状是一个永远为 1 的展开箭头。
        // 它同时是"parent_id 真的被写了"的证据：hasChild 是按 parent_id 数出来的，
        // 若移动只改了三个派生列而没落 parent_id，这里会留在 1，
        // 而症状是"树里还在原地、数据范围却已经搬家"
        mockMvc.perform(get("/api/auth/orgs/" + rootId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.parentId").value(nullValue()))
                .andExpect(jsonPath("$.data.hasChild").value(0));

        mockMvc.perform(get("/api/auth/orgs/" + midId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.hasChild").value(1));

        // 搬回去，证明重写是双向可逆的（增量为负、全称重新拼回来）
        mockMvc.perform(MockMvcRequestBuilders.put("/api/auth/orgs/" + midId + "/parent")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":" + rootId + "}"))
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/auth/orgs/" + leafId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.orgLevel").value(3))
                .andExpect(jsonPath("$.data.codePath").value("/" + rootId + "/" + midId + "/"))
                .andExpect(jsonPath("$.data.fullName").value("端到端根/端到端中/端到端叶"));

        mockMvc.perform(get("/api/auth/orgs/" + rootId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.hasChild").value(1));

        deleteOrg(token, leafId);
        deleteOrg(token, midId);
        deleteOrg(token, rootId);
    }

    @Test
    @DisplayName("改名会重写整棵子树的 fullName —— 全称是名称在树上的确定函数")
    void shouldRewriteFullNameWhenRenamed() throws Exception {
        String token = loginAsAdmin();

        String parentCode = uniqueCode("rn");
        long parentId = createOrg(token, parentCode, "改名前", null);
        long childId = createOrg(token, uniqueCode("cn"), "子部门", parentId);

        // 修改接口的入参里 orgCode 仍是 @NotBlank（与资源、角色两侧同一形状）：
        // 它不被修改，但调用方要把它原样带回来 —— 少传会被 DTO 校验挡成 40000
        mockMvc.perform(MockMvcRequestBuilders.put("/api/auth/orgs/" + parentId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orgBody(parentCode, "改名后", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.fullName").value("改名后"));

        // 交给调用方传 fullName 的话，这里留下的就是"名字已改、全称还是旧的"这种半更新状态
        mockMvc.perform(get("/api/auth/orgs/" + childId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.fullName").value("改名后/子部门"));

        deleteOrg(token, childId);
        deleteOrg(token, parentId);
    }

    @Test
    @DisplayName("移动不能挂到自己或自己的后代下（40913）")
    void shouldRejectCycleOnMove() throws Exception {
        String token = loginAsAdmin();

        long rootId = createOrg(token, uniqueCode("cyr"), "环根", null);
        long childId = createOrg(token, uniqueCode("cyc"), "环子", rootId);

        // 挂自己
        mockMvc.perform(MockMvcRequestBuilders.put("/api/auth/orgs/" + rootId + "/parent")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":" + rootId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40913));

        // 挂到自己的下级下面：这是真正的"成环"，也是只做"不能挂自己"时最容易漏掉的一半
        mockMvc.perform(MockMvcRequestBuilders.put("/api/auth/orgs/" + rootId + "/parent")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":" + childId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40913));

        // 被拒之后层级必须原样不动 —— 拒绝不能只是"报个错然后照样改了"
        mockMvc.perform(get("/api/auth/orgs/" + rootId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.orgLevel").value(1))
                .andExpect(jsonPath("$.data.parentId").value(nullValue()));

        deleteOrg(token, childId);
        deleteOrg(token, rootId);
    }

    /* ==================== 修改路径上的两处"拒绝而不是忽略" ==================== */

    @Test
    @DisplayName("修改接口不接受层级变更：带上不同的 parentId 返回 40001 并指向移动接口")
    void shouldRejectParentChangeOnUpdate() throws Exception {
        String token = loginAsAdmin();

        String aCode = uniqueCode("ua");
        long aId = createOrg(token, aCode, "A 部门", null);
        long bId = createOrg(token, uniqueCode("ub"), "B 部门", null);

        // 静默忽略的表现是"保存成功、层级没变"，调用方会以为改好了 ——
        // 而移动的真实影响面远大于一次字段更新（见 move 的注释），不能藏在 200 里
        mockMvc.perform(MockMvcRequestBuilders.put("/api/auth/orgs/" + aId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orgBody(aCode, "A 部门", bId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("移动")));

        mockMvc.perform(get("/api/auth/orgs/" + aId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.parentId").value(nullValue()));

        // 反过来：不传 parentId 表示"这次不改层级"，修改资料照常成功
        mockMvc.perform(MockMvcRequestBuilders.put("/api/auth/orgs/" + aId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orgBody(aCode, "A 部门改", null)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orgName").value("A 部门改"));

        deleteOrg(token, aId);
        deleteOrg(token, bId);
    }

    @Test
    @DisplayName("三态标志位只接受 0 / 1：isPrimaryPosition 传 7 返回 40001")
    void shouldRejectNonBinaryPrimaryFlag() throws Exception {
        String token = loginAsAdmin();

        long orgId = createOrg(token, uniqueCode("fb"), "标志位部门", null);
        long userId = createUser(token, uniqueUsername("fb"));

        mockMvc.perform(post("/api/auth/orgs/" + orgId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":" + userId + ",\"isPrimaryPosition\":7}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("0 或 1")));

        deleteOrg(token, orgId);
        deleteUser(token, userId);
    }

    /* ==================== 删除守卫与复活 ==================== */

    @Test
    @DisplayName("有下级（40913）或有成员归属（40919）时拒绝删除；两种原因不混为一谈")
    void shouldRejectDeleteWithChildrenOrMembers() throws Exception {
        String token = loginAsAdmin();

        long parentId = createOrg(token, uniqueCode("dp"), "待删父", null);
        long childId = createOrg(token, uniqueCode("dc"), "待删子", parentId);

        deleteOrg(token, parentId)
                .andExpect(jsonPath("$.code").value(40913))
                .andExpect(jsonPath("$.message").value(containsString("下级")));

        // 有成员时是**另一个**错误码：两者的处置完全不同（先删下级 / 先移成员），
        // 合成一个码会让界面只能给一句"删不掉"
        long orgId = createOrg(token, uniqueCode("dm"), "有人部门", null);
        long userId = createUser(token, uniqueUsername("dm"));
        bindMember(token, orgId, memberBody(userId, null, null));

        deleteOrg(token, orgId)
                .andExpect(jsonPath("$.code").value(40919))
                .andExpect(jsonPath("$.message").value(containsString("成员")));

        // 拒绝之后它必须还在
        mockMvc.perform(get("/api/auth/orgs/" + orgId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));

        revokeMember(token, orgId, userId).andExpect(jsonPath("$.code").value(0));
        deleteOrg(token, orgId).andExpect(jsonPath("$.code").value(0));

        deleteOrg(token, childId);
        deleteOrg(token, parentId);
        deleteUser(token, userId);
    }

    @Test
    @DisplayName("同码软删重建：复活原记录而不是撞唯一索引，且复活出来的是空部门")
    void shouldReviveSoftDeletedOrgAndClearMembers() throws Exception {
        String token = loginAsAdmin();
        String code = uniqueCode("rv");

        long orgId = createOrg(token, code, "初版部门", null);
        long userId = createUser(token, uniqueUsername("rv"));
        long grantId = bindMemberAndGetGrantId(token, orgId, memberBody(userId, null, null));

        // 有成员时删不掉，先把人移走 —— 这也顺带证明关系行确实落库了
        revokeMember(token, orgId, userId).andExpect(jsonPath("$.code").value(0));
        deleteOrg(token, orgId).andExpect(jsonPath("$.code").value(0));

        // uk_bw_auth_org_tenant_code 不含 deleted，软删的那行仍然占着这个编码；
        // 而 MP 的每次查询都自动追加 deleted = 0，看不见它 —— "判重通过 → insert → 撞键"。
        // 修法是认出软删行并复活它（OrganizationService#create）。
        MvcResult revived = mockMvc.perform(post("/api/auth/orgs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orgBody(code, "重建部门", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        Number revivedId = JsonPath.read(revived.getResponse().getContentAsString(), "$.data.id");
        assertEquals(orgId, revivedId.longValue(),
                "应复活原记录而不是插入新行（插不进去，只可能报错）");

        // 复活出来的必须是**空部门**：成员归属一并清掉。
        // 不清的话，一个已被删除的部门名下的历史归属会在它被重建时突然复活，
        // 那些人会静默重获一个自己都不知道的 DEPT 范围。
        mockMvc.perform(get("/api/auth/orgs/" + orgId + "/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0));
        assertNull(relOrgUserMapper.selectById(grantId),
                "原先那条归属应当是逻辑删除状态（selectById 自动带 deleted = 0）");

        deleteOrg(token, orgId);
        deleteUser(token, userId);
    }

    /* ==================== 成员归属与主岗 ==================== */

    @Test
    @DisplayName("主岗：第一个归属自动成为主岗，之后不静默搬动，且每人至多一个")
    void shouldKeepSinglePrimaryPositionWithoutStealthMove() throws Exception {
        String token = loginAsAdmin();

        long orgA = createOrg(token, uniqueCode("pa"), "A 部", null);
        long orgB = createOrg(token, uniqueCode("pb"), "B 部", null);
        long userId = createUser(token, uniqueUsername("pp"));

        // 第一个归属：不传 isPrimaryPosition → 自动成为主岗。
        // 否则刚建好的账号挂进部门后会 DEPT 算不出范围、被兜底成"仅本人"，
        // 而管理员看到的却只是"他挂进去了"
        bindMember(token, orgA, memberBody(userId, null, null))
                .andExpect(jsonPath("$.data.primaryPosition").value(true))
                .andExpect(jsonPath("$.data.isPrimaryPosition").value(1));

        // 第二个归属：此人已有主岗 → 保持原样，**绝不静默搬主岗**。
        // 静默搬动的后果是他的数据范围悄悄变了，而他本人的部门归属看起来毫无变化
        bindMember(token, orgB, memberBody(userId, null, null))
                .andExpect(jsonPath("$.data.primaryPosition").value(false))
                .andExpect(jsonPath("$.data.isPrimaryPosition").value(0));

        mockMvc.perform(get("/api/auth/orgs/" + orgA + "/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.records[0].isPrimaryPosition").value(1));

        // 显式要求：这次是调用方的明确意图，允许把主岗搬到 B，并把 A 降级
        bindMember(token, orgB, memberBody(userId, 1, null))
                .andExpect(jsonPath("$.data.isPrimaryPosition").value(1));

        mockMvc.perform(get("/api/auth/orgs/" + orgA + "/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.records[0].isPrimaryPosition").value(0));

        // 每人每租户至多一个主岗：这条没有数据库唯一约束兜底，全靠这一次"先降级再设置"
        List<Integer> primariesInB = JsonPath.read(
                mockMvc.perform(get("/api/auth/orgs/" + orgB + "/members")
                                .header("Authorization", "Bearer " + token)
                                .param("primaryOnly", "true"))
                        .andReturn().getResponse().getContentAsString(),
                "$.data.records[*].isPrimaryPosition");
        assertEquals(List.of(1), primariesInB, "B 部应当且只有一个主岗");

        // 独立接口改主岗：不改变任何计数，却直接改变这个人的数据范围 ——
        // 所以它必须能单独被调用、单独留痕
        mockMvc.perform(MockMvcRequestBuilders.put(
                        "/api/auth/orgs/" + orgA + "/members/" + userId + "/primary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.primaryPosition").value(true));

        mockMvc.perform(get("/api/auth/orgs/" + orgB + "/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.records[0].isPrimaryPosition").value(0));

        // 反查：这个人在哪些组织里，以及哪一条是主岗
        MvcResult byUser = mockMvc.perform(get("/api/auth/users/" + userId + "/organizations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        List<String> orgNames = JsonPath.read(byUser.getResponse().getContentAsString(),
                "$.data[*].orgName");
        assertEquals(2, orgNames.size(), "应有两条归属，实际：" + orgNames);

        // 收尾：撤关系 → 删组织 → 删用户
        revokeMember(token, orgA, userId).andExpect(jsonPath("$.code").value(0));
        revokeMember(token, orgB, userId).andExpect(jsonPath("$.code").value(0));
        deleteOrg(token, orgA);
        deleteOrg(token, orgB);
        deleteUser(token, userId);
    }

    @Test
    @DisplayName("重复绑定不堆行；撤销与设主岗对不存在的归属返回 40410")
    void shouldNotDuplicateMembership() throws Exception {
        String token = loginAsAdmin();

        long orgId = createOrg(token, uniqueCode("du"), "重复部门", null);
        long userId = createUser(token, uniqueUsername("du"));

        bindMember(token, orgId, memberBody(userId, null, null));
        bindMember(token, orgId, memberBody(userId, null, null));

        // bw_auth_rel_org_user 连 (organization_id, user_id) 唯一索引都没有，
        // "重复增加不撞键"—— 挡重复行的只有服务层的先查后写，所以必须在这里钉住
        mockMvc.perform(get("/api/auth/orgs/" + orgId + "/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1));

        revokeMember(token, orgId, userId).andExpect(jsonPath("$.code").value(0));

        // 本来就不在时返回 40410 而不是成功：两种情况在前端看起来一样，
        // 混淆会把"为什么这个人还看得见"的排查方向带偏
        revokeMember(token, orgId, userId)
                .andExpect(jsonPath("$.code").value(40410));

        // 主岗是那段归属的一个属性，关系不存在时它无处安放 —— 也不该悄悄新建一条
        mockMvc.perform(MockMvcRequestBuilders.put(
                        "/api/auth/orgs/" + orgId + "/members/" + userId + "/primary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40410));

        deleteOrg(token, orgId);
        deleteUser(token, userId);
    }

    /* ==================== 与数据范围求值的接线 ==================== */

    @Test
    @DisplayName("DEPT_AND_CHILD：无主岗时 precise=false，挂进部门后精确求值并下发启用子树")
    void shouldEvaluateDeptAndChildScope() throws Exception {
        String token = loginAsAdmin();

        long userId = createUser(token, uniqueUsername("ds"));
        String roleCode = uniqueCode("ds");
        long roleId = createRole(token, roleCode, "端到端数据范围角色", "DEPT_AND_CHILD");
        bindRole(token, roleId, userId);

        // "已配置"是表里那一列，"已生效"是能不能翻译成一组组织 —— 两者必须分开报
        dataScope(token, userId)
                .andExpect(jsonPath("$.data.scope").value("DEPT_AND_CHILD"))
                .andExpect(jsonPath("$.data.precise").value(false))
                .andExpect(jsonPath("$.data.primaryOrgId").value(nullValue()));

        String rootCode = uniqueCode("sr");
        long rootId = createOrg(token, rootCode, "范围根", null);
        long childId = createOrg(token, uniqueCode("sc"), "范围子", rootId);
        long grandId = createOrg(token, uniqueCode("sg"), "范围孙", childId);
        long otherId = createOrg(token, uniqueCode("so"), "范围外人", null);

        bindMember(token, rootId, memberBody(userId, null, null));

        MvcResult result = dataScope(token, userId)
                .andExpect(jsonPath("$.data.precise").value(true))
                .andExpect(jsonPath("$.data.scope").value("DEPT_AND_CHILD"))
                .andExpect(jsonPath("$.data.primaryOrgId").value((int) rootId))
                .andExpect(jsonPath("$.data.primaryOrgName").value("范围根"))
                .andReturn();

        List<Integer> visible = JsonPath.read(result.getResponse().getContentAsString(),
                "$.data.visibleOrgIds");
        assertTrue(visible.contains((int) rootId), "自身必须在内：" + visible);
        assertTrue(visible.contains((int) childId), "子树必须在内：" + visible);
        assertTrue(visible.contains((int) grandId), "孙节点必须在内：" + visible);
        assertTrue(!visible.contains((int) otherId), "另一棵树的节点不能在内：" + visible);

        // DEPT 只有主岗那一个 —— 与 DEPT_AND_CHILD 的差别正是这一档存在的理由
        updateRole(token, roleId, roleCode, "端到端数据范围角色", "DEPT");
        MvcResult narrowed = dataScope(token, userId)
                .andExpect(jsonPath("$.data.scope").value("DEPT"))
                .andExpect(jsonPath("$.data.precise").value(true))
                .andReturn();
        List<Integer> onlySelf = JsonPath.read(narrowed.getResponse().getContentAsString(),
                "$.data.visibleOrgIds");
        assertEquals(List.of((int) rootId), onlySelf, "DEPT 档位只含主岗所在组织");

        // 停用主岗所在组织 → 这一档整体算不出，往窄兜底（而不是"除了它自己都算得出"）
        updateOrgStatus(token, rootId, rootCode, "范围根", "DISABLED");
        dataScope(token, userId)
                .andExpect(jsonPath("$.data.precise").value(false))
                .andExpect(jsonPath("$.data.primaryOrgId").value(nullValue()));

        updateOrgStatus(token, rootId, rootCode, "范围根", "ENABLED");
        dataScope(token, userId).andExpect(jsonPath("$.data.precise").value(true));

        revokeRole(token, roleId, userId);
        revokeMember(token, rootId, userId);
        deleteOrg(token, grandId);
        deleteOrg(token, childId);
        deleteOrg(token, rootId);
        deleteOrg(token, otherId);
        deleteRole(token, roleId);
        deleteUser(token, userId);
    }

    @Test
    @DisplayName("过期的组织归属不参与求值 —— 它看起来和有效归属一模一样")
    void shouldIgnoreExpiredMembership() throws Exception {
        String token = loginAsAdmin();

        long userId = createUser(token, uniqueUsername("ex"));
        long roleId = createRole(token, uniqueCode("ex"), "过期用例角色", "DEPT");
        bindRole(token, roleId, userId);

        long orgId = createOrg(token, uniqueCode("eo"), "过期部门", null);
        // 一段已经结束的归属：库里是有效的行、列表里也显示得出来，
        // 但它不该让这个人的 DEPT 档位算出任何范围（与角色/用户组的时态判定同一条）
        long past = System.currentTimeMillis() - 60_000L;
        bindMember(token, orgId, memberBody(userId, 1, past))
                .andExpect(jsonPath("$.data.effectiveNow").value(false))
                .andExpect(jsonPath("$.data.isPrimaryPosition").value(1));

        dataScope(token, userId)
                .andExpect(jsonPath("$.data.scope").value("DEPT"))
                .andExpect(jsonPath("$.data.precise").value(false))
                .andExpect(jsonPath("$.data.primaryOrgId").value(nullValue()));

        revokeRole(token, roleId, userId);
        revokeMember(token, orgId, userId);
        deleteOrg(token, orgId);
        deleteRole(token, roleId);
        deleteUser(token, userId);
    }

    @Test
    @DisplayName("树节点带上 managerId：负责人是跨域引用，姓名不唯一，光有姓名答不出\"是谁\"")
    void shouldCarryManagerIdOnTreeNode() throws Exception {
        String token = loginAsAdmin();

        long managerId = createUser(token, uniqueUsername("mgr"));
        String orgCode = uniqueCode("mo");
        // 带上负责人建一个部门。managerName 由服务端从用户表取（入参不接受它），
        // 这里连 managerId 一起写进请求体
        long orgId = createOrgWithManager(token, orgCode, "带负责人的部门", managerId);

        // 详情接口本就有 managerId / managerName 两列；树是"选层级、看归属"的那一份数据，
        // 它同样要能回答"这个负责人是谁"。少了 managerId，两个重名的人在这里长得一模一样，
        // 而前端正是靠它把标识渲在姓名旁边（见 decisions/0061）。
        mockMvc.perform(get("/api/auth/orgs/tree")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$..[?(@.orgCode == '" + orgCode + "')].managerId",
                        hasItem((int) managerId)))
                .andExpect(jsonPath("$..[?(@.orgCode == '" + orgCode + "')].managerName",
                        hasItem("组织用例用户")));

        // 详情与树给的是同一个东西：两处不一致会让"树上看的人"与"详情看的人"对不上
        mockMvc.perform(get("/api/auth/orgs/" + orgId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.managerId").value(managerId));

        deleteUser(token, managerId);
        deleteOrg(token, orgId);
    }

    /* ==================== 入参与鉴权守卫 ==================== */

    @Test
    @DisplayName("组织编码形状不合法、引用不存在的父级都被拒")
    void shouldRejectBadCodeAndMissingParent() throws Exception {
        String token = loginAsAdmin();

        // 下划线开头不合法：编码是对外稳定标识，形状错了会让"按码匹配"变成悬案
        mockMvc.perform(post("/api/auth/orgs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgCode\":\"_bad\",\"orgName\":\"形状错\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000));

        mockMvc.perform(post("/api/auth/orgs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orgBody(uniqueCode("op"), "孤儿", 999999999L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002));
    }

    @Test
    @DisplayName("审计员没有组织管理权限：读也读不到（40301）；未认证 40100")
    void shouldDenyAuditorAndAnonymous() throws Exception {
        String token = loginAsAuditor();

        // 审计员的权限是 USER_READ / TENANT_READ / AUDIT_READ / 三个读码，不含 auth:org:*。
        // 读接口一并拒绝是刻意的：组织树 + 成员归属等价于一份花名册，
        // 能看到它就能推出"谁在哪个部门"，这不该默认对一个只读账号开放
        mockMvc.perform(get("/api/auth/orgs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(get("/api/auth/orgs/tree")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(get("/api/auth/orgs/1/members")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(post("/api/auth/orgs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orgBody(uniqueCode("no"), "越权新建", null)))
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(get("/api/auth/orgs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40100));
    }

    /* ==================== 辅助 ==================== */

    private String loginAsAdmin() throws Exception {
        return login(properties.getSeedAdmin().getTenantCode(),
                properties.getSeedAdmin().getUsername(),
                properties.getSeedAdmin().getPassword());
    }

    private String loginAsAuditor() throws Exception {
        return login(properties.getSeedAdmin().getTenantCode(),
                properties.getSeedAuditor().getUsername(),
                properties.getSeedAuditor().getPassword());
    }

    private String login(String tenantCode, String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"tenantCode":"%s","username":"%s","password":"%s"}
                                """.formatted(tenantCode, username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
    }

    private long createOrg(String token, String code, String name, Long parentId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/orgs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orgBody(code, name, parentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    /** 带负责人建组织。{@code managerName} 不入参 —— 服务端从用户表取，这里只给 {@code managerId} */
    private long createOrgWithManager(String token, String code, String name, long managerId)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/orgs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orgCode\":\"" + code + "\",\"orgName\":\"" + name
                                + "\",\"managerId\":" + managerId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private ResultActions deleteOrg(String token, long id) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.delete("/api/auth/orgs/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    /** 只改状态，编码与名称原样带过去（修改接口要求 orgCode、orgName 非空，并会拒绝层级变更） */
    private void updateOrgStatus(String token, long id, String code, String name, String status)
            throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/api/auth/orgs/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(orgBody(code, name, null, status)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private long createUser(String token, String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","displayName":"组织用例用户","password":"%s"}
                                """.formatted(username, TEST_USER_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private ResultActions deleteUser(String token, long id) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.delete("/api/auth/users/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private long createRole(String token, String code, String name, String dataScope) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/roles")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody(code, name, dataScope)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private void updateRole(String token, long id, String code, String name, String dataScope)
            throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.put("/api/auth/roles/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(roleBody(code, name, dataScope)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.dataScope").value(dataScope));
    }

    private ResultActions deleteRole(String token, long id) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.delete("/api/auth/roles/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void bindRole(String token, long roleId, long userId) throws Exception {
        mockMvc.perform(post("/api/auth/roles/" + roleId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberType\":\"USER\",\"memberId\":" + userId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void revokeRole(String token, long roleId, long userId) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .delete("/api/auth/roles/" + roleId + "/members/USER/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private ResultActions bindMember(String token, long orgId, String body) throws Exception {
        return mockMvc.perform(post("/api/auth/orgs/" + orgId + "/members")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /** 与 {@link #bindMember} 同一条调用，但把归属关系主键取回来 —— 断言"它真的被清掉了"要用 */
    private long bindMemberAndGetGrantId(String token, long orgId, String body) throws Exception {
        MvcResult result = bindMember(token, orgId, body).andReturn();
        Number grantId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.grantId");
        return grantId.longValue();
    }

    private ResultActions revokeMember(String token, long orgId, long userId) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders
                        .delete("/api/auth/orgs/" + orgId + "/members/" + userId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private ResultActions dataScope(String token, long userId) throws Exception {
        return mockMvc.perform(get("/api/auth/users/" + userId + "/data-scope")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /**
     * 组织入参。字段可选时**不写出这个键**，而不是写 {@code null}：
     * 在本域里两者语义不同（{@code parentId} 不传 = 顶级 / 不传 = 这次不改层级，
     * 取决于走的是新增还是修改路径），拼出"键不存在"才测得到真实形状。
     */
    private static String orgBody(String code, String name, Long parentId) {
        return orgBody(code, name, parentId, null);
    }

    private static String orgBody(String code, String name, Long parentId, String status) {
        StringBuilder json = new StringBuilder("{\"orgCode\":\"").append(code)
                .append("\",\"orgName\":\"").append(name).append('"');
        if (parentId != null) {
            json.append(",\"parentId\":").append(parentId);
        }
        if (status != null) {
            json.append(",\"status\":\"").append(status).append('"');
        }
        return json.append('}').toString();
    }

    private static String roleBody(String code, String name, String dataScope) {
        return """
                {"roleCode":"%s","roleName":"%s","dataScope":"%s"}
                """.formatted(code, name, dataScope);
    }

    /**
     * 组织成员入参。
     *
     * <p>用拼接而不是文本块，是因为这三个字段都是**可选**的，而"不传某个字段"
     * 与"传 null"在本域里语义不同（{@code isPrimaryPosition} 不传 = 不静默搬主岗，
     * 传 0 = 明确摘掉主岗）。文本块拼不出"字段根本不存在"，这里必须拼出来。
     */
    private static String memberBody(long userId, Integer primary, Long effectiveTo) {
        StringBuilder json = new StringBuilder("{\"memberId\":").append(userId);
        if (primary != null) {
            json.append(",\"isPrimaryPosition\":").append(primary);
        }
        if (effectiveTo != null) {
            json.append(",\"effectiveTo\":").append(effectiveTo);
        }
        return json.append('}').toString();
    }

    /**
     * 生成一个**合法**的组织编码：{@code E2E<前缀><base36 时间戳>}。
     *
     * <p>首字母固定为 {@code E}：{@code Long.toString(n, 36)} 可能以数字开头，
     * 而编码要求以字母起 —— 直接用时间戳会偶尔被自己的 DTO 校验拦下，
     * 变成一个"大部分时候通过、偶尔失败"的假测试。
     */
    private static String uniqueCode(String prefix) {
        return "E2E" + prefix + Long.toString(System.nanoTime(), 36);
    }

    /** 用户名的字符集比编码窄：只允许字母开头 + 字母数字与 {@code _.-}，本处不用下划线 */
    private static String uniqueUsername(String prefix) {
        return "e2e" + prefix + Long.toString(System.nanoTime(), 36);
    }

}
