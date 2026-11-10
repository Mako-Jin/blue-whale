package com.bluewhale.contract.esign;

import com.bluewhale.contract.config.ContractEsignProperties;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;
import org.bouncycastle.tsp.TimeStampTokenInfo;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Security;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;

/**
 * RFC 3161 时间戳客户端与校验器。
 *
 * <p>签署时向 {@code whale.contract.esign.tsa-url} 提交签名值的 SHA-256 指纹，
 * 取得时间戳令牌后以 CMS 未签名属性 {@code id-aa-signatureTimeStampToken} 写入签名；
 * 验签时校验令牌指纹、令牌签名与 TSA 证书有效期。</p>
 *
 * <p><b>失败语义为 fail-closed</b>：配置了 TSA 后请求失败、响应非法或签名值不匹配
 * 一律抛出异常，不静默降级为「无时间戳」；{@code tsa-required=true} 时验签缺少时间戳
 * 也判定为不通过。</p>
 *
 * @author BlueWhale
 */
public class TimestampService {

    /** BouncyCastle Provider 名称 */
    private static final String BC_PROVIDER = "BC";

    /** 时间戳查询 MIME 类型 */
    private static final String CONTENT_TYPE_QUERY = "application/timestamp-query";

    /** 时间戳响应 MIME 类型 */
    private static final String CONTENT_TYPE_REPLY = "application/timestamp-reply";

    /** 安全随机数（生成 nonce） */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ContractEsignProperties properties;

    private final HttpClient httpClient;

    /**
     * 构造时间戳服务。
     *
     * @param properties 电子签配置
     */
    public TimestampService(ContractEsignProperties properties) {
        this.properties = properties;
        if (Security.getProvider(BC_PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(properties.getTsaTimeoutMillis(), 1000)))
                .build();
    }

    /**
     * 是否已配置 TSA。
     *
     * @return 配置了 {@code tsa-url} 时返回 {@code true}
     */
    public boolean isEnabled() {
        return properties.isTimestampEnabled();
    }

    /**
     * 为签名值申请 RFC 3161 时间戳令牌。
     *
     * @param signatureValue CMS 签名值字节
     * @return 时间戳令牌 DER 编码
     */
    public byte[] timestamp(byte[] signatureValue) {
        try {
            byte[] imprint = sha256(signatureValue);
            BigInteger nonce = new BigInteger(64, RANDOM);
            TimeStampRequestGenerator generator = new TimeStampRequestGenerator();
            generator.setCertReq(true);
            TimeStampRequest request = generator.generate(NISTObjectIdentifiers.id_sha256, imprint, nonce);

            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.getTsaUrl().trim()))
                    .timeout(Duration.ofMillis(Math.max(properties.getTsaTimeoutMillis(), 1000)))
                    .header("Content-Type", CONTENT_TYPE_QUERY)
                    .header("Accept", CONTENT_TYPE_REPLY)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(request.getEncoded()));
            applyBasicAuth(builder);
            HttpResponse<byte[]> response = httpClient.send(builder.build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("TSA 时间戳服务返回 HTTP " + response.statusCode());
            }
            TimeStampResponse timestampResponse = new TimeStampResponse(response.body());
            timestampResponse.validate(request);
            TimeStampToken token = timestampResponse.getTimeStampToken();
            if (token == null) {
                throw new IllegalStateException("TSA 未返回时间戳令牌: status="
                        + timestampResponse.getStatus() + ", " + timestampResponse.getStatusString());
            }
            return token.getEncoded();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("申请 RFC 3161 时间戳失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 校验时间戳令牌：指纹与签名值一致、TSA 签名有效、证书在签发时有效。
     *
     * @param tokenBytes      时间戳令牌 DER
     * @param signatureValue  CMS 签名值字节
     * @return 校验说明（签发时间与 TSA 主题）
     */
    public String verify(byte[] tokenBytes, byte[] signatureValue) {
        try {
            TimeStampToken token = new TimeStampToken(
                    ContentInfo.getInstance(ASN1Primitive.fromByteArray(tokenBytes)));
            TimeStampTokenInfo info = token.getTimeStampInfo();
            byte[] expected = sha256(signatureValue);
            if (!Arrays.equals(expected, info.getMessageImprintDigest())) {
                throw new IllegalStateException("时间戳指纹与被签签名值不一致");
            }
            Collection<X509CertificateHolder> matches = token.getCertificates().getMatches(token.getSID());
            if (matches.isEmpty()) {
                throw new IllegalStateException("时间戳令牌缺少匹配的 TSA 证书");
            }
            X509CertificateHolder holder = matches.iterator().next();
            token.validate(new JcaSimpleSignerInfoVerifierBuilder()
                    .setProvider(BC_PROVIDER).build(holder));
            return "RFC3161 时间戳 " + info.getGenTime() + " 由 " + holder.getSubject() + " 签发";
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("RFC 3161 时间戳校验失败: " + ex.getMessage(), ex);
        }
    }

    /**
     * 追加 TSA Basic 认证头（仅在配置用户名时）。
     *
     * @param builder 请求构建器
     */
    private void applyBasicAuth(HttpRequest.Builder builder) {
        String username = properties.getTsaUsername();
        if (username == null || username.isBlank()) {
            return;
        }
        String password = properties.getTsaPassword() == null ? "" : properties.getTsaPassword();
        String credential = username + ":" + password;
        builder.header("Authorization", "Basic "
                + Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 计算 SHA-256 摘要。
     *
     * @param data 原始数据
     * @return 摘要字节
     * @throws IOException 摘要异常
     */
    private static byte[] sha256(byte[] data) throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception ex) {
            throw new IOException("SHA-256 摘要计算失败: " + ex.getMessage(), ex);
        }
    }
}
