package com.bluewhale.mes.process.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.bluewhale.mes.process.dto.MesProcessRouteReceiveRequest;
import com.bluewhale.mes.process.dto.MesProcessRouteWithdrawRequest;
import com.bluewhale.mes.process.entity.MesProcessRoute;
import com.bluewhale.mes.process.mapper.MesProcessRouteMapper;
import com.bluewhale.mes.process.vo.MesProcessRouteVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MES 工艺路线接收服务单元测试。
 *
 * @author BlueWhale
 */
@ExtendWith(MockitoExtension.class)
class MesProcessRouteServiceImplTest {

    @Mock
    private MesProcessRouteMapper routeMapper;

    @InjectMocks
    private MesProcessRouteServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                MesProcessRoute.class);
    }

    @Test
    void receiveFirstTimeInsertsActiveRoute() {
        when(routeMapper.selectList(any())).thenReturn(List.of());
        doAnswer(invocation -> {
            MesProcessRoute entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        }).when(routeMapper).insert(any(MesProcessRoute.class));

        MesProcessRouteReceiveRequest request = request("RT-P-001", "V1.0", "sha-1");

        MesProcessRouteVO vo = service.receive(request);

        assertEquals("ACTIVE", vo.getStatus());
        assertEquals(2, vo.getStepCount());
        assertFalse(vo.getIdempotent());
        verify(routeMapper).insert(any(MesProcessRoute.class));
    }

    @Test
    void receiveSameChecksumIsIdempotent() {
        MesProcessRoute existing = route("RT-P-001", "V1.0", "ACTIVE", "sha-1");
        when(routeMapper.selectList(any())).thenReturn(List.of(existing));

        MesProcessRouteVO vo = service.receive(request("RT-P-001", "V1.0", "sha-1"));

        assertTrue(vo.getIdempotent());
        verify(routeMapper, never()).insert(any(MesProcessRoute.class));
        verify(routeMapper, never()).updateById(any(MesProcessRoute.class));
    }

    @Test
    void receiveChangedChecksumOverwritesContent() {
        MesProcessRoute existing = route("RT-P-001", "V1.0", "ACTIVE", "sha-1");
        when(routeMapper.selectList(any())).thenReturn(List.of(existing));

        MesProcessRouteVO vo = service.receive(request("RT-P-001", "V1.0", "sha-2"));

        assertFalse(vo.getIdempotent());
        assertEquals("sha-2", vo.getChecksum());
        verify(routeMapper).updateById(any(MesProcessRoute.class));
    }

    @Test
    void withdrawUnreceivedWritesTombstone() {
        when(routeMapper.selectList(any())).thenReturn(List.of());
        doAnswer(invocation -> {
            MesProcessRoute entity = invocation.getArgument(0);
            entity.setId(2L);
            return 1;
        }).when(routeMapper).insert(any(MesProcessRoute.class));

        MesProcessRouteVO vo = service.withdraw(withdrawRequest("RT-P-001", "V1.0"));

        assertEquals("WITHDRAWN", vo.getStatus());
        assertNotNull(vo.getWithdrawnAt());
        ArgumentCaptor<MesProcessRoute> captor = ArgumentCaptor.forClass(MesProcessRoute.class);
        verify(routeMapper).insert(captor.capture());
        assertEquals("WITHDRAWN", captor.getValue().getStatus());
    }

    /**
     * 构造下发请求
     *
     * @param routeNo  路线编号
     * @param version  版本
     * @param checksum 校验和
     * @return 请求
     */
    private MesProcessRouteReceiveRequest request(String routeNo, String version, String checksum) {
        MesProcessRouteReceiveRequest request = new MesProcessRouteReceiveRequest();
        request.setRouteNo(routeNo);
        request.setVersionNo(version);
        request.setPartCode("P-001");
        request.setPartName("测试零件");
        request.setChecksum(checksum);
        MesProcessRouteReceiveRequest.Step first = new MesProcessRouteReceiveRequest.Step();
        first.setStepNo(1);
        first.setName("车削");
        MesProcessRouteReceiveRequest.Step second = new MesProcessRouteReceiveRequest.Step();
        second.setStepNo(2);
        second.setName("铣削");
        request.setSteps(List.of(first, second));
        return request;
    }

    /**
     * 构造撤回请求
     *
     * @param routeNo 路线编号
     * @param version 版本
     * @return 请求
     */
    private MesProcessRouteWithdrawRequest withdrawRequest(String routeNo, String version) {
        MesProcessRouteWithdrawRequest request = new MesProcessRouteWithdrawRequest();
        request.setRouteNo(routeNo);
        request.setVersionNo(version);
        return request;
    }

    /**
     * 构造既有记录
     *
     * @param routeNo  路线编号
     * @param version  版本
     * @param status   状态
     * @param checksum 校验和
     * @return 记录
     */
    private MesProcessRoute route(String routeNo, String version, String status, String checksum) {
        MesProcessRoute route = new MesProcessRoute();
        route.setId(1L);
        route.setPdmRouteNo(routeNo);
        route.setVersionNo(version);
        route.setStatus(status);
        route.setChecksum(checksum);
        route.setStepCount(2);
        return route;
    }
}
