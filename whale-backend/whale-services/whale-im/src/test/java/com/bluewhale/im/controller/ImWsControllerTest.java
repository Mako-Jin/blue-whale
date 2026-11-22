package com.bluewhale.im.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.web.WebHeaders;
import com.bluewhale.im.service.WsTicketService;
import com.bluewhale.im.vo.WsTicketVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ImWsController} 控制器层单元测试（standalone MockMvc）。
 *
 * @author BlueWhale
 */
class ImWsControllerTest {

    private static final long USER_ID = 10L;

    private final WsTicketService ticketService = mock(WsTicketService.class);

    private final ImWsController controller = new ImWsController(ticketService);

    private MockMvc mockMvc;

    /**
     * 构建 standalone MockMvc
     */
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setApiVersionStrategy(ImApiVersionTestSupport.strategy())
                .build();
    }

    @Test
    @DisplayName("类级权限：要求 sys:im:chat")
    void classShouldRequireImChatPermission() {
        RequirePermission annotation = ImWsController.class.getAnnotation(RequirePermission.class);

        assertNotNull(annotation);
        assertEquals("sys:im:chat", annotation.value());
    }

    @Test
    @DisplayName("POST /im/ws-ticket：返回一次性 ticket 与有效期")
    void ticketShouldReturnTicket() throws Exception {
        when(ticketService.issue(USER_ID)).thenReturn(WsTicketVO.builder().ticket("t-1").expiresIn(60L).build());

        mockMvc.perform(post("/im/v1/ws-ticket").header(WebHeaders.USER_ID, String.valueOf(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ticket").value("t-1"))
                .andExpect(jsonPath("$.data.expiresIn").value(60));
    }
}
