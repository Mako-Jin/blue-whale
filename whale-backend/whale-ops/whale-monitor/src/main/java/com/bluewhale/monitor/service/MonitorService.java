package com.bluewhale.monitor.service;

import com.bluewhale.monitor.vo.ServiceInstanceVO;
import com.bluewhale.monitor.vo.ServiceSummaryVO;

import java.util.List;

/**
 * 服务监控查询服务
 *
 * <p>数据来源为 Nacos 注册中心 {@code DiscoveryClient}，本服务无数据库，仅做采集与聚合。</p>
 *
 * @author BlueWhale
 */
public interface MonitorService {

    /**
     * 查询全部已注册服务的聚合概览
     *
     * @return 服务名 + 实例总数 + 健康实例数列表
     */
    List<ServiceSummaryVO> listServices();

    /**
     * 查询指定服务的实例列表
     *
     * @param serviceName 服务名
     * @return 实例列表；服务名未注册时抛出 {@code NOT_FOUND}
     */
    List<ServiceInstanceVO> listInstances(String serviceName);
}
