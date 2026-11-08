package com.bluewhale.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.job.config.JobProperties;
import com.bluewhale.job.dto.ExecutorBeatRequest;
import com.bluewhale.job.dto.ExecutorRegisterRequest;
import com.bluewhale.job.entity.JobExecutor;
import com.bluewhale.job.mapper.JobExecutorMapper;
import com.bluewhale.job.service.JobExecutorService;
import com.bluewhale.job.vo.JobExecutorVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 执行器注册表服务实现。
 *
 * <p>在线判定不依赖定时清理：查询时用「状态在线 + 心跳不早于阈值」双条件过滤，
 * 实例异常退出后自然不再被寻址。{@code status=0} 仅用于优雅停机的显式注销。</p>
 *
 * <p>注意：本表唯一键 {@code (tenant_id, app_name, address)} 不含 {@code deleted}，
 * 因此**不得**对执行器记录做逻辑删除后重插（会撞唯一键）；注销一律置 {@code status=0}。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
public class JobExecutorServiceImpl implements JobExecutorService {

    /** 默认租户ID */
    private static final long DEFAULT_TENANT_ID = 1L;

    /** 状态：在线 */
    private static final int STATUS_ONLINE = 1;

    /** 状态：已注销 */
    private static final int STATUS_OFFLINE = 0;

    /** 任务编码串长度上限（与列宽一致） */
    private static final int MAX_JOB_CODES_LENGTH = 2048;

    private final JobExecutorMapper jobExecutorMapper;

    private final JobProperties jobProperties;

    private final ObjectProvider<DiscoveryClient> discoveryClientProvider;

    /**
     * 构造服务：服务发现客户端允许缺失（单机部署或测试环境只用注册表寻址）
     *
     * @param jobExecutorMapper      执行器注册表 Mapper
     * @param jobProperties          任务服务配置
     * @param discoveryClientProvider 服务发现客户端
     */
    public JobExecutorServiceImpl(JobExecutorMapper jobExecutorMapper,
                                  JobProperties jobProperties,
                                  ObjectProvider<DiscoveryClient> discoveryClientProvider) {
        this.jobExecutorMapper = jobExecutorMapper;
        this.jobProperties = jobProperties;
        this.discoveryClientProvider = discoveryClientProvider;
    }

    @Override
    public void register(ExecutorRegisterRequest request) {
        Long tenantId = tenantIdOf(request.getTenantId());
        String jobCodes = joinCodes(request.getJobCodes());
        LocalDateTime now = LocalDateTime.now();
        JobExecutor existing = findByAddress(tenantId, request.getAppName(), request.getAddress());
        if (existing == null) {
            JobExecutor entity = new JobExecutor();
            entity.setTenantId(tenantId);
            entity.setAppName(request.getAppName());
            entity.setAddress(request.getAddress());
            entity.setJobCodes(jobCodes);
            entity.setLastBeat(now);
            entity.setStatus(STATUS_ONLINE);
            jobExecutorMapper.insert(entity);
            log.info("执行器注册: app={}, address={}, codes={}", request.getAppName(), request.getAddress(), jobCodes);
            return;
        }
        existing.setJobCodes(jobCodes);
        existing.setLastBeat(now);
        existing.setStatus(STATUS_ONLINE);
        jobExecutorMapper.updateById(existing);
        log.info("执行器重新注册: app={}, address={}, codes={}", request.getAppName(), request.getAddress(), jobCodes);
    }

    @Override
    public boolean beat(ExecutorBeatRequest request) {
        Long tenantId = tenantIdOf(request.getTenantId());
        int rows = jobExecutorMapper.update(null, new LambdaUpdateWrapper<JobExecutor>()
                .eq(JobExecutor::getTenantId, tenantId)
                .eq(JobExecutor::getAppName, request.getAppName())
                .eq(JobExecutor::getAddress, request.getAddress())
                .set(JobExecutor::getLastBeat, LocalDateTime.now())
                .set(JobExecutor::getStatus, STATUS_ONLINE));
        return rows > 0;
    }

    @Override
    public void deregister(ExecutorBeatRequest request) {
        Long tenantId = tenantIdOf(request.getTenantId());
        int rows = jobExecutorMapper.update(null, new LambdaUpdateWrapper<JobExecutor>()
                .eq(JobExecutor::getTenantId, tenantId)
                .eq(JobExecutor::getAppName, request.getAppName())
                .eq(JobExecutor::getAddress, request.getAddress())
                .set(JobExecutor::getStatus, STATUS_OFFLINE));
        log.info("执行器注销: app={}, address={}, rows={}", request.getAppName(), request.getAddress(), rows);
    }

    @Override
    public List<String> onlineAddresses(Long tenantId, String appName) {
        if (!StringUtils.hasText(appName)) {
            return List.of();
        }
        return listOnline(tenantIdOf(tenantId), appName).stream()
                .map(JobExecutor::getAddress)
                .toList();
    }

    @Override
    public List<String> onlineAddresses(Long tenantId, String appName, String jobCode) {
        if (!StringUtils.hasText(jobCode)) {
            return onlineAddresses(tenantId, appName);
        }
        if (!StringUtils.hasText(appName)) {
            return List.of();
        }
        return listOnline(tenantIdOf(tenantId), appName).stream()
                .filter(executor -> supports(executor.getJobCodes(), jobCode))
                .map(JobExecutor::getAddress)
                .toList();
    }

    /**
     * 判定实例上报的任务编码集合是否包含目标编码。
     *
     * @param codes 逗号分隔的编码串
     * @param code  目标编码
     * @return 支持返回 {@code true}
     */
    private boolean supports(String codes, String code) {
        if (!StringUtils.hasText(codes)) {
            return false;
        }
        for (String candidate : codes.split(",")) {
            if (candidate.trim().equals(code)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> dispatchableAddresses(Long tenantId, String appName, String jobCode) {
        List<String> registered = onlineAddresses(tenantId, appName, jobCode);
        if (registered.isEmpty()) {
            return registered;
        }
        DiscoveryClient discoveryClient = discoveryClientProvider.getIfAvailable();
        if (discoveryClient == null) {
            return registered;
        }
        Set<String> live = new LinkedHashSet<>();
        try {
            for (ServiceInstance instance : discoveryClient.getInstances(appName)) {
                live.add(instance.getHost() + ":" + instance.getPort());
            }
        } catch (RuntimeException e) {
            log.warn("服务发现不可用，回退注册表寻址: app={}, reason={}", appName, e.getMessage());
            return registered;
        }
        if (live.isEmpty()) {
            log.warn("Nacos 无该服务实例，回退注册表寻址: app={}, registry={}", appName, registered);
            return registered;
        }
        List<String> matched = registered.stream().filter(live::contains).toList();
        if (matched.isEmpty()) {
            log.warn("注册表与 Nacos 实例不一致，回退注册表寻址: app={}, registry={}, nacos={}",
                    appName, registered, live);
            return registered;
        }
        return matched;
    }

    @Override
    public void markUnreachable(Long tenantId, String appName, String address) {
        if (!StringUtils.hasText(appName) || !StringUtils.hasText(address)) {
            return;
        }
        int rows = jobExecutorMapper.update(null, new LambdaUpdateWrapper<JobExecutor>()
                .eq(JobExecutor::getTenantId, tenantIdOf(tenantId))
                .eq(JobExecutor::getAppName, appName)
                .eq(JobExecutor::getAddress, address)
                .set(JobExecutor::getStatus, STATUS_OFFLINE));
        if (rows > 0) {
            log.warn("执行器连接失败，临时摘除: app={}, address={}（心跳恢复后自动回到在线）", appName, address);
        }
    }

    @Override
    public List<JobExecutorVO> list(Long tenantId, String appName) {
        LambdaQueryWrapper<JobExecutor> wrapper = new LambdaQueryWrapper<JobExecutor>()
                .eq(JobExecutor::getTenantId, tenantIdOf(tenantId))
                .eq(StringUtils.hasText(appName), JobExecutor::getAppName, appName)
                .orderByAsc(JobExecutor::getAppName)
                .orderByAsc(JobExecutor::getAddress);
        LocalDateTime threshold = heartbeatThreshold();
        return jobExecutorMapper.selectList(wrapper).stream()
                .map(entity -> toVo(entity, threshold))
                .toList();
    }

    /**
     * 查询在线实例实体（状态在线且心跳未超时）
     *
     * @param tenantId 租户ID
     * @param appName  执行器应用名
     * @return 在线实例列表，按地址升序
     */
    private List<JobExecutor> listOnline(Long tenantId, String appName) {
        return jobExecutorMapper.selectList(new LambdaQueryWrapper<JobExecutor>()
                .eq(JobExecutor::getTenantId, tenantId)
                .eq(JobExecutor::getAppName, appName)
                .eq(JobExecutor::getStatus, STATUS_ONLINE)
                .ge(JobExecutor::getLastBeat, heartbeatThreshold())
                .orderByAsc(JobExecutor::getAddress));
    }

    /**
     * 按唯一键查询实例
     *
     * @param tenantId 租户ID
     * @param appName  执行器应用名
     * @param address  实例地址
     * @return 实例；不存在返回 {@code null}
     */
    private JobExecutor findByAddress(Long tenantId, String appName, String address) {
        return jobExecutorMapper.selectOne(new LambdaQueryWrapper<JobExecutor>()
                .eq(JobExecutor::getTenantId, tenantId)
                .eq(JobExecutor::getAppName, appName)
                .eq(JobExecutor::getAddress, address));
    }

    /**
     * 心跳有效下限
     *
     * @return 心跳早于该时间的实例视为离线
     */
    private LocalDateTime heartbeatThreshold() {
        return LocalDateTime.now().minusSeconds(jobProperties.getExecutor().getHeartbeatTimeoutSeconds());
    }

    @Override
    public int purgeStale() {
        int multiplier = Math.max(jobProperties.getExecutor().getStaleMultiplier(), 1);
        long staleSeconds = (long) jobProperties.getExecutor().getHeartbeatTimeoutSeconds() * multiplier;
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(staleSeconds);
        int rows = jobExecutorMapper.deleteStale(threshold);
        if (rows > 0) {
            log.info("清理过期执行器行: rows={}, threshold={}, staleSeconds={}", rows, threshold, staleSeconds);
        }
        return rows;
    }

    /**
     * 归一租户ID
     *
     * @param tenantId 入参租户ID
     * @return 有效租户ID，为空时依次回落到上下文与默认租户
     */
    private Long tenantIdOf(Long tenantId) {
        if (tenantId != null) {
            return tenantId;
        }
        Long current = TenantContext.get();
        return current == null ? DEFAULT_TENANT_ID : current;
    }

    /**
     * 任务编码列表拼为列值（去空、去重、截断到列宽）
     *
     * @param codes 任务编码列表
     * @return 逗号分隔的编码串；空列表返回 {@code null}
     */
    private String joinCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return null;
        }
        List<String> normalized = new ArrayList<>();
        for (String code : codes) {
            if (StringUtils.hasText(code) && !normalized.contains(code.trim())) {
                normalized.add(code.trim());
            }
        }
        if (normalized.isEmpty()) {
            return null;
        }
        String joined = String.join(",", normalized);
        return joined.length() > MAX_JOB_CODES_LENGTH ? joined.substring(0, MAX_JOB_CODES_LENGTH) : joined;
    }

    /**
     * 实体转视图
     *
     * @param entity    执行器实体
     * @param threshold 心跳有效下限
     * @return 视图对象
     */
    private JobExecutorVO toVo(JobExecutor entity, LocalDateTime threshold) {
        JobExecutorVO vo = new JobExecutorVO();
        vo.setAppName(entity.getAppName());
        vo.setAddress(entity.getAddress());
        vo.setJobCodes(StringUtils.hasText(entity.getJobCodes())
                ? Arrays.asList(entity.getJobCodes().split(",")) : List.of());
        vo.setLastBeat(entity.getLastBeat());
        boolean online = entity.getStatus() != null && entity.getStatus() == STATUS_ONLINE
                && entity.getLastBeat() != null && !entity.getLastBeat().isBefore(threshold);
        vo.setOnline(online);
        return vo;
    }
}
