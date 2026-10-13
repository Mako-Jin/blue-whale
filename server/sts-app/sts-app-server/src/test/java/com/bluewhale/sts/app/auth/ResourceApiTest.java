package com.bluewhale.sts.app.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.sts.auth.common.AuthPermissions;
import com.bluewhale.sts.auth.common.AuthProperties;
import com.bluewhale.sts.auth.common.enums.ResourceType;
import com.bluewhale.sts.auth.resource.ResourceEntity;
import com.bluewhale.sts.auth.resource.ResourceMapper;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 资源目录接口的端到端测试。
 *
 * <p><b>刻意不加 {@code @Transactional}</b>，与 {@code PasswordHardeningApiTest} 同一理由：
 * 本类里最要紧的一条用例（软删后重建同一标识）验证的恰恰是"库里真正留下了什么"。
 * 测试事务会让未提交的写在同一事务内可见，把这类缺陷完全掩盖 ——
 * 而这条缺陷的表现形式是"返回 500 而不是业务码"，只有真的走一遍落库才看得见。
 *
 * <p>代价是用例会真的写库（h2 内存库，随测试进程消失），因此资源标识一律带时间戳后缀。
 * 命名仍是合法的三段式（{@code e2e:res:xxx}）—— 否则会被 DTO 上的形状校验挡下，
 * 那样测到的就不是被测逻辑了。
 *
 * <p><b>种子账号的口令从 {@link AuthProperties} 读，不写死。</b>
 * 种子口令与登录页提示文案之间有一条"必须一致"的约定（见 {@code AuthProperties} 的注释），
 * 在测试里再抄一份，等于给这条约定加了第三个会漂的副本。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ResourceApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResourceMapper resourceMapper;

    @Autowired
    private AuthProperties properties;

    /* ==================== 主链路 ==================== */

    @Test
    @DisplayName("建目录 → 建子项 → 树里可见 → 改名 → 删子项 → 删目录")
    void shouldManageResourceLifecycle() throws Exception {
        String token = loginAsAdmin();

        String parentCode = uniqueCode("menu");
        String childCode = uniqueCode("btn");
        long parentId = createResource(token, parentCode, "端到端菜单", "MENU", null);

        // 父是 MENU：requestScoped 必须是 false —— 菜单只影响前端可见性，不参与后端准入。
        // 这条断言实际上在钉住 ResourceType.isRequestScoped() 与出参的接线，
        // 而不是枚举本身（枚举由 AuthTaxonomyTest 级别的单元测试覆盖）
        mockMvc.perform(get("/api/auth/resources/" + parentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.resourceType").value("MENU"))
                .andExpect(jsonPath("$.data.requestScoped").value(false))
                // 平台级资源（tenant_id 留空）。本仓没有配 Jackson 的 NON_NULL，
                // 因此 null 字段会出现在响应里，值是 null 而不是"字段不存在"
                .andExpect(jsonPath("$.data.tenantId").value(nullValue()));

        long childId = createResource(token, childCode, "端到端按钮", "BUTTON", parentCode);

        // BUTTON 相反：它对应一次请求，参与判定
        mockMvc.perform(get("/api/auth/resources/" + childId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.requestScoped").value(true));

        // 树里子项挂在父项下 —— 这是"菜单权限"将来要消费的那份数据
        MvcResult treeResult = mockMvc.perform(get("/api/auth/resources/tree")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].resourceCode", hasItem(parentCode)))
                .andReturn();

        // 用 Jayway 直接读过滤后的路径，而不是把它塞进 MockMvc 的 jsonPath 表达式：
        // 过滤表达式里要嵌一个引号包起来的标识，两种解析器对转义的处理并不一致
        List<String> childrenOfParent = JsonPath.read(
                treeResult.getResponse().getContentAsString(),
                "$.data[?(@.resourceCode == '" + parentCode + "')].children[*].resourceCode");
        assertTrue(childrenOfParent.contains(childCode),
                "子资源应挂在父资源下，实际子节点：" + childrenOfParent);

        // 改名（标识不动）
        mockMvc.perform(MockMvcRequestBuilders.put("/api/auth/resources/" + childId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(childCode, "改过名的按钮", "BUTTON", parentCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.resourceName").value("改过名的按钮"))
                .andExpect(jsonPath("$.data.resourceCode").value(childCode));

        deleteResource(token, childId).andExpect(jsonPath("$.code").value(0));
        deleteResource(token, parentId).andExpect(jsonPath("$.code").value(0));
    }

    /* ==================== 标识：形状、唯一性、复活 ==================== */

    @Test
    @DisplayName("资源标识必须是「域:资源:动作」三段小写，两段式被拒")
    void shouldRejectMalformedResourceCode() throws Exception {
        String token = loginAsAdmin();

        // 两段的 "auth:resource" 是**资源标识**，不是权限码。它是这个接口最容易犯的错：
        // 两者形状相近，而库里存的必须是权限码（三段）——
        // 登记成两段之后，@RequiresPermission 永远匹配不到它，权限静默失效
        mockMvc.perform(post("/api/auth/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceCode":"auth:resource","resourceName":"写错了的","resourceType":"MENU"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").value(containsString("域:资源:动作")));

        // 大写同样被拒 —— 权限码全小写是无一例外的约定，
        // 放行 Auth:X 会让"按码匹配"变成一个要不要忽略大小写的悬案
        mockMvc.perform(post("/api/auth/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceCode":"E2e:Res:X","resourceName":"大写","resourceType":"MENU"}
                                """))
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    @DisplayName("标识重复返回 40912")
    void shouldRejectDuplicateResourceCode() throws Exception {
        String token = loginAsAdmin();
        String code = uniqueCode("dup");
        long id = createResource(token, code, "第一次", "PAGE", null);

        mockMvc.perform(post("/api/auth/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(code, "第二次", "PAGE", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40912));

        deleteResource(token, id);
    }

    @Test
    @DisplayName("软删后重建同一标识：复活原记录而不是撞唯一索引（撞了会被兜成 50000）")
    void shouldReviveSoftDeletedResourceInsteadOfFailing() throws Exception {
        String token = loginAsAdmin();
        String code = uniqueCode("revive");

        long firstId = createResource(token, code, "初版", "PAGE", null);
        deleteResource(token, firstId).andExpect(jsonPath("$.code").value(0));

        // 这条是本次改动的回归点。唯一索引 uk_bw_auth_resource_code 建在 resource_code 单列上、
        // 不含 deleted，所以软删的那行仍然占着这个标识；而 MyBatis-Plus 的每一次查询都会
        // 自动追加 deleted = 0，看不见它 —— "唯一性检查通过 → insert → 撞键"。
        // GlobalExceptionHandler 里没有 DuplicateKeyException 的处理器，于是用户拿到 50000。
        // 修法是认出软删行并复活它（ResourceService#create）。
        MvcResult result = mockMvc.perform(post("/api/auth/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(code, "重建", "PAGE", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        Number revivedId = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        assertEquals(firstId, revivedId.longValue(),
                "应复活原记录而不是插入新行（插不进去，只可能报错）");

        // 复活后必须真的可用：能查到、且 deleted 已被清掉
        mockMvc.perform(get("/api/auth/resources/" + firstId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.resourceName").value("重建"));

        ResourceEntity reloaded = resourceMapper.selectById(firstId);
        assertNotNull(reloaded, "复活后的记录应当能被正常查询到");
        assertEquals(0, reloaded.getDeleted(), "deleted 应已清为 0");

        deleteResource(token, firstId);
    }

    /* ==================== 层级：父存在性、成环、删除拒绝 ==================== */

    @Test
    @DisplayName("父资源不存在返回 40002")
    void shouldRejectMissingParent() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(post("/api/auth/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueCode("orphan"), "孤儿", "MENU", "e2e:nonexistent:parent")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.message").value(containsString("父资源不存在")));
    }

    @Test
    @DisplayName("父资源不能是自身，也不能是自己的下级（40913）")
    void shouldRejectCycle() throws Exception {
        String token = loginAsAdmin();
        String rootCode = uniqueCode("root");
        String leafCode = uniqueCode("leaf");
        long rootId = createResource(token, rootCode, "根", "MENU", null);
        long leafId = createResource(token, leafCode, "叶", "MENU", rootCode);

        // 自己挂自己
        mockMvc.perform(MockMvcRequestBuilders.put("/api/auth/resources/" + rootId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(rootCode, "根", "MENU", rootCode)))
                .andExpect(jsonPath("$.code").value(40913));

        // 挂到自己的下级下面 —— 这是真正的"成环"，也是只做"不能挂自己"时最容易漏掉的一半
        mockMvc.perform(MockMvcRequestBuilders.put("/api/auth/resources/" + rootId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(rootCode, "根", "MENU", leafCode)))
                .andExpect(jsonPath("$.code").value(40913))
                .andExpect(jsonPath("$.message").value(containsString("下级")));

        deleteResource(token, leafId);
        deleteResource(token, rootId);
    }

    @Test
    @DisplayName("有子资源时拒绝删除（40913）")
    void shouldRejectDeleteWithChildren() throws Exception {
        String token = loginAsAdmin();
        String parentCode = uniqueCode("par");
        String childCode = uniqueCode("chi");
        long parentId = createResource(token, parentCode, "父", "PAGE", null);
        long childId = createResource(token, childCode, "子", "PAGE", parentCode);

        deleteResource(token, parentId)
                .andExpect(jsonPath("$.code").value(40913))
                .andExpect(jsonPath("$.message").value(containsString("子资源")));

        // 子项删掉之后父项才能删 —— 证明拒的是"还有子节点"这个状态，不是父节点本身
        deleteResource(token, childId).andExpect(jsonPath("$.code").value(0));
        deleteResource(token, parentId).andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("已被角色引用时拒绝删除（40915）—— 避免授权静默失效")
    void shouldRejectDeleteWhenGrantedToRole() throws Exception {
        String token = loginAsAdmin();

        // 种子数据把 AuthPermissions.ALL 全部授给了 SUPER_ADMIN，
        // 因此随便挑一个内置权限码，它必定是被引用的
        MvcResult result = mockMvc.perform(get("/api/auth/resources")
                        .header("Authorization", "Bearer " + token)
                        .param("keyword", "auth:user:create"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        List<Integer> ids = readIds(result);
        assertEquals(1, ids.size(), "内置权限 auth:user:create 应当唯一存在");

        deleteResource(token, ids.get(0).longValue())
                .andExpect(jsonPath("$.code").value(40915))
                .andExpect(jsonPath("$.message").value(containsString("已被")));

        // 拒绝之后它必须还在 —— 拒绝不能只是"报个错然后照删"
        mockMvc.perform(get("/api/auth/resources/" + ids.get(0))
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("种子把菜单建成了 MENU / PAGE 并带父子关系 —— 类型写错会让资源目录显示一句反话")
    void shouldSeedMenuResourcesWithHierarchy() {
        // 应用入口：MENU、无父级
        ResourceEntity menu = resourceMapper.selectOne(Wrappers.<ResourceEntity>lambdaQuery()
                .eq(ResourceEntity::getResourceCode, AuthPermissions.UI_ADMIN_VIEW));
        assertNotNull(menu, "菜单码必须在种子里建出资源行 —— 否则前端的那个入口永远等不到权限下发");
        assertEquals(ResourceType.MENU.wireName(), menu.getResourceType(),
                "应用入口应当是 MENU；建成 API 会让它在资源目录里显示成'参与后端准入'");
        assertNull(menu.getParentCode());

        // 它下面的页面：PAGE、父指向它
        List<ResourceEntity> pages = resourceMapper.selectList(Wrappers.<ResourceEntity>lambdaQuery()
                .eq(ResourceEntity::getParentCode, AuthPermissions.UI_ADMIN_VIEW));
        assertEquals(6, pages.size(), "权限管理的六个标签页各有一条 PAGE 资源");
        assertTrue(pages.stream().allMatch(page -> ResourceType.PAGE.wireName().equals(page.getResourceType())),
                "页面级一律 PAGE —— 与 MENU 混用会让'菜单结构'变成'应用彼此是父子'");
        assertTrue(pages.stream().anyMatch(page ->
                        AuthPermissions.UI_ADMIN_ORGANIZATION_VIEW.equals(page.getResourceCode())),
                "组织标签页的码也要在 —— 它是本轮新增的那个入口");

        // 对照：接口资源一律 API 且没有父级（两类资源的形状本来就不同）
        ResourceEntity api = resourceMapper.selectOne(Wrappers.<ResourceEntity>lambdaQuery()
                .eq(ResourceEntity::getResourceCode, AuthPermissions.USER_READ));
        assertNotNull(api);
        assertEquals(ResourceType.API.wireName(), api.getResourceType());
        assertNull(api.getParentCode(), "接口资源不该被挂进菜单树里");
    }

    /* ==================== 入参守卫与鉴权 ==================== */

    @Test
    @DisplayName("三态标志位只接受 0 / 1，放过别的值会静默改变语义")
    void shouldRejectNonBinaryFlags() throws Exception {
        String token = loginAsAdmin();

        mockMvc.perform(post("/api/auth/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceCode":"%s","resourceName":"标志位","resourceType":"PAGE","isEnabled":7}
                                """.formatted(uniqueCode("flag"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("0 或 1")));
    }

    @Test
    @DisplayName("资源类型不认识就报错，并把合法取值列出来")
    void shouldRejectUnknownResourceType() throws Exception {
        String token = loginAsAdmin();

        // 写入时严格（对比：查询过滤是宽松的）。不报错的话，管理员只会看到"保存成功"，
        // 然后在若干天后发现某条权限不生效
        mockMvc.perform(post("/api/auth/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueCode("type"), "类型错", "MENUITEM", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("BUTTON")));
    }

    @Test
    @DisplayName("审计员没有资源管理权限：读也读不到（40301）")
    void shouldDenyAuditor() throws Exception {
        String token = loginAsAuditor();

        // 审计员的权限是 USER_READ / TENANT_READ / AUDIT_READ，不含 auth:resource:*。
        // 读接口一并拒绝是刻意的：资源目录等价于"系统里存在哪些权限"的地图，
        // 能给谁看是安全评审的输入，不该默认对只读账号开放
        mockMvc.perform(get("/api/auth/resources")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(get("/api/auth/resources/tree")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.code").value(40301));

        mockMvc.perform(post("/api/auth/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(uniqueCode("deny"), "越权新建", "PAGE", null)))
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    @DisplayName("库里存在孤儿与成环时，树接口降级为平铺而不是打不开")
    void shouldSurviveOrphanAndCyclicData() throws Exception {
        String token = loginAsAdmin();

        // 直接写库制造两种坏数据 —— 这不是"测试走捷径"，而是本仓真实存在的写入路径：
        // DefaultAdminSeeder 就是直接经 Mapper 写表的，人也可以。树接口因此不能假设数据是干净的。
        String orphanCode = uniqueCode("orph");
        ResourceEntity orphan = new ResourceEntity();
        orphan.setResourceCode(orphanCode);
        orphan.setResourceName("孤儿");
        orphan.setResourceType("MENU");
        orphan.setParentCode("e2e:missing:parent");
        orphan.setIsEnabled(1);
        resourceMapper.insert(orphan);

        String aCode = uniqueCode("cyca");
        String bCode = uniqueCode("cycb");
        long aId = createResource(token, aCode, "A", "MENU", null);
        createResource(token, bCode, "B", "MENU", aCode);

        // 把 A 的父改成 B → A→B→A 一个两节点的环
        ResourceEntity a = resourceMapper.selectById(aId);
        a.setParentCode(bCode);
        resourceMapper.updateById(a);

        // 断言的是"返回 200 且这些节点都在"，不是"层级正确"：
        // 面对脏数据，可用的降级（平铺）好过整棵树打不开
        mockMvc.perform(get("/api/auth/resources/tree")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[*].resourceCode", hasItem(orphanCode)))
                .andExpect(jsonPath("$.data[*].resourceCode", hasItem(bCode)));

        // 收尾：把环拆掉，否则残留的环会影响同一次构建里其它读取全量树的用例
        a.setParentCode(null);
        resourceMapper.updateById(a);
        deleteResource(token, aId);
        deleteResource(token, orphan.getId());
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

    private long createResource(String token, String code, String name,
                               String type, String parentCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/resources")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(code, name, type, parentCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private ResultActions deleteResource(String token, long id) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.delete("/api/auth/resources/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private static String body(String code, String name, String type, String parentCode) {
        return """
                {"resourceCode":"%s","resourceName":"%s","resourceType":"%s","parentCode":%s}
                """.formatted(code, name, type, parentCode == null ? "null" : "\"" + parentCode + "\"");
    }

    private static List<Integer> readIds(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.records[*].id");
    }

    /**
     * 生成一个**合法**的权限码：{@code e2e:res:<前缀+base36>}。
     *
     * <p>第三段刻意以前缀字母开头：{@code Long.toString(n, 36)} 可能以数字开头，
     * 而权限码要求每段都以小写字母起 —— 直接用时间戳会偶尔被自己的 DTO 校验拦下，
     * 变成一个"大部分时候通过、偶尔失败"的假测试。
     */
    private static String uniqueCode(String prefix) {
        return "e2e:res:" + prefix + Long.toString(System.nanoTime(), 36);
    }

}
