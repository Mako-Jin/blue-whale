package com.bluewhale.contract.controller;

import com.bluewhale.contract.dto.SignExecuteRequest;
import com.bluewhale.contract.service.SignService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 本地签署意愿确认：缺失或未勾选必须在接口层拒绝。
 *
 * @author BlueWhale
 */
class ContractSignControllerWillingnessTest {

    /** 签署服务桩 */
    private SignService signService;

    /** 被测 MVC */
    private MockMvc mockMvc;

    /**
     * 构建控制器与桩服务。
     */
    @BeforeEach
    void setUp() {
        signService = mock(SignService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ContractSignController(signService))
                .setApiVersionStrategy(ApiVersionTestSupport.strategy())
                .build();
    }

    /**
     * 未提交 confirmWillingness 时返回 400。
     *
     * @throws Exception 请求异常
     */
    @Test
    void rejectsMissingWillingness() throws Exception {
        mockMvc.perform(post("/contract/v1/sign-task/1/sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sealId\":1}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * confirmWillingness=false 时返回 400。
     *
     * @throws Exception 请求异常
     */
    @Test
    void rejectsUncheckedWillingness() throws Exception {
        mockMvc.perform(post("/contract/v1/sign-task/1/sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sealId\":1,\"confirmWillingness\":false}"))
                .andExpect(status().isBadRequest());
    }

    /**
     * confirmWillingness=true 时进入服务层。
     *
     * @throws Exception 请求异常
     */
    @Test
    void acceptsConfirmedWillingness() throws Exception {
        when(signService.sign(anyLong(), any(SignExecuteRequest.class), any(), any())).thenReturn(null);

        mockMvc.perform(post("/contract/v1/sign-task/1/sign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sealId\":1,\"confirmWillingness\":true}"))
                .andExpect(status().isOk());
    }
}
