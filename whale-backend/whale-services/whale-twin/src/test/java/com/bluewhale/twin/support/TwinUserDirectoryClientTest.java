package com.bluewhale.twin.support;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.twin.config.TwinProperties;
import com.bluewhale.twin.dto.TwinNotifyDeptVO;
import com.bluewhale.twin.dto.TwinNotifyRecipientVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 接收人检索客户端单测：请求路径、分页/部门参数、字段映射与失败折算为空结果。
 *
 * @author BlueWhale
 */
class TwinUserDirectoryClientTest {

    private static final String AUTH_URL = "http://127.0.0.1:8081";

    private static final Long TENANT_ID = 1L;

    private static final String DIRECTORY_URL = AUTH_URL
            + "/user/v1/internal/directory?keyword=zhang&current=1&size=20";

    private static final String TREE_URL = AUTH_URL + "/dept/v1/internal/tree";

    private MockRestServiceServer server;

    private TwinUserDirectoryClient client;

    @BeforeEach
    void setUp() {
        TwinProperties properties = new TwinProperties();
        properties.getAlertNotify().setAuthBaseUrl(AUTH_URL);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new TwinUserDirectoryClient(properties, builder.build());
    }

    @Test
    void mapsDirectoryRowsToRecipients() {
        server.expect(requestTo(DIRECTORY_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Tenant-Id", "1"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"records\":["
                        + "{\"id\":1900000000000000001,\"username\":\"zhangsan\","
                        + "\"nickname\":\"张三\",\"deptName\":\"设备部\"},"
                        + "{\"id\":2,\"username\":\"zhangsi\"}],\"total\":37}}",
                        MediaType.APPLICATION_JSON));

        PageResult<TwinNotifyRecipientVO> page = client.search(TENANT_ID, "zhang", null, null, null);

        assertEquals(2, page.getRecords().size());
        // total 取自 auth，用于前端提示「共 N 人」而不是「本页 N 条」
        assertEquals(37L, page.getTotal());
        assertEquals(1L, page.getCurrent());
        assertEquals(20L, page.getSize());
        assertEquals("1900000000000000001", page.getRecords().get(0).getId());
        assertEquals("张三", page.getRecords().get(0).getName());
        assertEquals("设备部", page.getRecords().get(0).getDeptName());
        // 没昵称时回落账号名
        assertEquals("zhangsi", page.getRecords().get(1).getName());
        assertNull(page.getRecords().get(1).getDeptName());
        server.verify();
    }

    @Test
    void fallsBackTotalToPageSizeWhenAuthOmitsIt() {
        server.expect(requestTo(DIRECTORY_URL))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"records\":["
                        + "{\"id\":1,\"username\":\"a\"},{\"id\":2,\"username\":\"b\"}]}}",
                        MediaType.APPLICATION_JSON));

        PageResult<TwinNotifyRecipientVO> page = client.search(TENANT_ID, "zhang", null, null, null);

        // 缺 total 时不能显示「共 0 人」却列出两条
        assertEquals(2L, page.getTotal());
    }

    @Test
    void passesDeptAndPageParamsAndClampsSize() {
        server.expect(requestTo(AUTH_URL
                        + "/user/v1/internal/directory?deptId=12&includeChildren=true&current=2&size=50"))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"records\":[],\"total\":0}}",
                        MediaType.APPLICATION_JSON));

        PageResult<TwinNotifyRecipientVO> page = client.search(TENANT_ID, "  ", 12L, 2L, 500);

        assertTrue(page.getRecords().isEmpty());
        // 超过上限按 50 截断
        assertEquals(50L, page.getSize());
        server.verify();
    }

    @Test
    void returnsEmptyPageOnBusinessError() {
        server.expect(requestTo(DIRECTORY_URL))
                .andRespond(withSuccess("{\"code\":500,\"message\":\"服务异常\"}",
                        MediaType.APPLICATION_JSON));

        PageResult<TwinNotifyRecipientVO> page = client.search(TENANT_ID, "zhang", null, null, null);

        assertTrue(page.getRecords().isEmpty());
        assertEquals(0L, page.getTotal());
    }

    @Test
    void returnsEmptyPageOnHttpError() {
        server.expect(requestTo(DIRECTORY_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        // 选择器拉不到人时前端可退回手工输入，不该整页报错
        assertTrue(client.search(TENANT_ID, "zhang", null, null, null).getRecords().isEmpty());
    }

    @Test
    void skipsRowsWithoutId() {
        server.expect(requestTo(DIRECTORY_URL))
                .andRespond(withSuccess("{\"code\":0,\"data\":{\"records\":["
                        + "{\"username\":\"nobody\"},{\"id\":\"3\",\"username\":\"ok\"}]}}",
                        MediaType.APPLICATION_JSON));

        PageResult<TwinNotifyRecipientVO> page = client.search(TENANT_ID, "zhang", null, null, null);

        assertEquals(1, page.getRecords().size());
        assertEquals("3", page.getRecords().get(0).getId());
    }

    @Test
    void mapsDepartmentTreeRecursively() {
        server.expect(requestTo(TREE_URL))
                .andExpect(header("X-Tenant-Id", "1"))
                .andRespond(withSuccess("{\"code\":0,\"data\":["
                        + "{\"id\":10,\"deptName\":\"生产部\",\"children\":["
                        + "  {\"id\":11,\"deptName\":\"设备科\",\"children\":[]},"
                        + "  {\"id\":12,\"deptName\":\"安全科\"}]},"
                        + "{\"id\":20,\"deptName\":\"信息部\"}]}", MediaType.APPLICATION_JSON));

        List<TwinNotifyDeptVO> tree = client.departments(TENANT_ID);

        assertEquals(2, tree.size());
        assertEquals("10", tree.get(0).getId());
        assertEquals("生产部", tree.get(0).getName());
        assertEquals(2, tree.get(0).getChildren().size());
        assertEquals("设备科", tree.get(0).getChildren().get(0).getName());
        // 空 children 归一为 null，前端 TreeSelect 不必判空数组
        assertNull(tree.get(0).getChildren().get(0).getChildren());
        assertNull(tree.get(1).getChildren());
    }

    @Test
    void dropsDepartmentNodeWithoutId() {
        server.expect(requestTo(TREE_URL))
                .andRespond(withSuccess("{\"code\":0,\"data\":["
                        + "{\"deptName\":\"野节点\"},{\"id\":30,\"deptName\":\"财务部\"}]}",
                        MediaType.APPLICATION_JSON));

        List<TwinNotifyDeptVO> tree = client.departments(TENANT_ID);

        // 没有 ID 的部门选了也筛不出人，直接丢
        assertEquals(1, tree.size());
        assertEquals("30", tree.get(0).getId());
    }

    @Test
    void returnsEmptyDepartmentsOnBusinessError() {
        server.expect(requestTo(TREE_URL))
                .andRespond(withSuccess("{\"code\":403,\"message\":\"缺少租户上下文\"}",
                        MediaType.APPLICATION_JSON));

        assertTrue(client.departments(TENANT_ID).isEmpty());
    }

    @Test
    void returnsEmptyDepartmentsOnHttpError() {
        server.expect(requestTo(TREE_URL))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        List<TwinNotifyDeptVO> tree = client.departments(TENANT_ID);

        assertNotNull(tree);
        assertTrue(tree.isEmpty());
    }
}
