package com.bluewhale.contract.esign;

import com.bluewhale.contract.config.ContractEsignProperties;
import org.bouncycastle.cert.X509CRLHolder;
import org.bouncycastle.cert.jcajce.JcaX509CRLConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Security;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509CRL;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CA 证书链校验器：信任根加载、PKIX 路径校验与 CRL 吊销检查。
 *
 * <p>未配置 {@code whale.contract.esign.ca-cert-path} 时 {@link #isConfigured()} 为
 * {@code false}，保持本地自签证书可用（仅校验签名本身）；配置后签署与验签都必须
 * 把签名证书链接到信任根，否则拒绝，避免「自签证书被当作可信证书」。</p>
 *
 * <p>证书链来源：签署时取 PKCS#12 中的证书链，验签时取 CMS 证书集合；
 * 信任根取 {@code ca-cert-path} 中的 PEM 证书。{@code ca-crl-path} 可选，
 * 配置后要求 CRL 由信任根签发且不包含签名证书。</p>
 *
 * @author BlueWhale
 */
public class CertificateChainValidator {

    /** BouncyCastle Provider 名称 */
    private static final String BC_PROVIDER = "BC";

    /** 证书链最大长度（防御异常数据） */
    private static final int MAX_CHAIN_LENGTH = 8;

    private final ContractEsignProperties properties;

    /**
     * 构造校验器。
     *
     * @param properties 电子签配置
     */
    public CertificateChainValidator(ContractEsignProperties properties) {
        this.properties = properties;
        if (Security.getProvider(BC_PROVIDER) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * 是否配置了信任根。
     *
     * @return 配置了 {@code ca-cert-path} 时返回 {@code true}
     */
    public boolean isConfigured() {
        return properties.isChainValidationEnabled();
    }

    /**
     * 加载信任根证书。
     *
     * @return CA 证书列表
     */
    public List<X509Certificate> caCertificates() {
        if (!isConfigured()) {
            return List.of();
        }
        Path path = Path.of(properties.getCaCertPath().trim());
        try (InputStream input = Files.newInputStream(path)) {
            Collection<? extends java.security.cert.Certificate> parsed =
                    CertificateFactory.getInstance("X.509").generateCertificates(input);
            List<X509Certificate> certificates = new ArrayList<>();
            for (java.security.cert.Certificate certificate : parsed) {
                if (certificate instanceof X509Certificate x509) {
                    certificates.add(x509);
                }
            }
            if (certificates.isEmpty()) {
                throw new IllegalStateException("CA 证书文件未包含任何 X.509 证书: " + path);
            }
            return certificates;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("CA 证书文件读取失败: " + path + "（" + ex.getMessage() + "）", ex);
        }
    }

    /**
     * 校验签名证书可链接到信任根，并检查 CRL 吊销状态。
     *
     * @param leaf       签名证书
     * @param candidates 可用的证书链/证书集合（含中间证书与根证书）
     */
    public void validate(X509Certificate leaf, Collection<X509Certificate> candidates) {
        if (!isConfigured()) {
            return;
        }
        List<X509Certificate> anchors = caCertificates();
        if (!isAnchor(leaf, anchors)) {
            List<X509Certificate> pool = new ArrayList<>(candidates);
            pool.addAll(anchors);
            List<X509Certificate> path = buildPath(leaf, pool, anchors);
            if (path.size() <= 1) {
                throw new IllegalStateException("签名证书无法链接到配置的 CA 信任根: "
                        + leaf.getSubjectX500Principal().getName());
            }
            if (isAnchor(path.get(path.size() - 1), anchors)) {
                path = path.subList(0, path.size() - 1);
            }
            validatePkix(path, anchors);
        }
        checkRevocation(leaf, anchors);
    }

    /**
     * 执行 PKIX 路径校验。
     *
     * @param path    不含信任根的证书链
     * @param anchors 信任根
     */
    private void validatePkix(List<X509Certificate> path, List<X509Certificate> anchors) {
        try {
            Set<TrustAnchor> trustAnchors = new HashSet<>();
            for (X509Certificate anchor : anchors) {
                trustAnchors.add(new TrustAnchor(anchor, null));
            }
            CertPath certPath = CertificateFactory.getInstance("X.509").generateCertPath(path);
            PKIXParameters parameters = new PKIXParameters(trustAnchors);
            parameters.setRevocationEnabled(false);
            CertPathValidator.getInstance("PKIX").validate(certPath, parameters);
        } catch (Exception ex) {
            throw new IllegalStateException("证书链未通过 PKIX 信任根校验: " + ex.getMessage(), ex);
        }
    }

    /**
     * 按 issuer/subject 关系排序证书链。
     *
     * @param leaf       签名证书
     * @param candidates 候选证书
     * @param anchors    信任根
     * @return 以签名证书开头的证书链
     */
    private List<X509Certificate> buildPath(X509Certificate leaf, Collection<X509Certificate> candidates,
                                            List<X509Certificate> anchors) {
        List<X509Certificate> path = new ArrayList<>();
        path.add(leaf);
        X509Certificate current = leaf;
        while (path.size() < MAX_CHAIN_LENGTH && !isAnchor(current, anchors)) {
            X509Certificate issuer = findIssuer(current, candidates);
            if (issuer == null || contains(path, issuer)) {
                break;
            }
            path.add(issuer);
            current = issuer;
        }
        return path;
    }

    /**
     * 在候选中查找签发者。
     *
     * @param certificate 当前证书
     * @param candidates  候选证书
     * @return 签发者证书；未找到返回 {@code null}
     */
    private X509Certificate findIssuer(X509Certificate certificate,
                                       Collection<X509Certificate> candidates) {
        for (X509Certificate candidate : candidates) {
            if (!sameCertificate(certificate, candidate)
                    && certificate.getIssuerX500Principal()
                    .equals(candidate.getSubjectX500Principal())) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * 判断证书是否为信任根之一。
     *
     * @param certificate 证书
     * @param anchors     信任根
     * @return 命中返回 {@code true}
     */
    private boolean isAnchor(X509Certificate certificate, List<X509Certificate> anchors) {
        for (X509Certificate anchor : anchors) {
            if (sameCertificate(certificate, anchor)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断证书链中是否已存在同一证书。
     *
     * @param certificates 证书链
     * @param target       目标证书
     * @return 存在返回 {@code true}
     */
    private boolean contains(List<X509Certificate> certificates, X509Certificate target) {
        for (X509Certificate certificate : certificates) {
            if (sameCertificate(certificate, target)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 按主题与序列号判断同一证书。
     *
     * @param left  证书
     * @param right 证书
     * @return 相同返回 {@code true}
     */
    private boolean sameCertificate(X509Certificate left, X509Certificate right) {
        return left.getSerialNumber().equals(right.getSerialNumber())
                && left.getSubjectX500Principal().equals(right.getSubjectX500Principal());
    }

    /**
     * 检查 CRL 吊销状态（未配置 CRL 时跳过）。
     *
     * @param leaf    签名证书
     * @param anchors 信任根
     */
    private void checkRevocation(X509Certificate leaf, List<X509Certificate> anchors) {
        String crlPath = properties.getCaCrlPath();
        if (crlPath == null || crlPath.isBlank()) {
            return;
        }
        X509CRL crl = loadCrl(Path.of(crlPath.trim()));
        X509Certificate issuer = null;
        for (X509Certificate anchor : anchors) {
            if (crl.getIssuerX500Principal().equals(anchor.getSubjectX500Principal())) {
                issuer = anchor;
                break;
            }
        }
        if (issuer == null) {
            throw new IllegalStateException("CRL 签发者不在配置的 CA 信任根中: "
                    + crl.getIssuerX500Principal().getName());
        }
        try {
            crl.verify(issuer.getPublicKey());
        } catch (Exception ex) {
            throw new IllegalStateException("CRL 签名校验失败: " + ex.getMessage(), ex);
        }
        if (crl.isRevoked(leaf)) {
            throw new IllegalStateException("签名证书已被 CRL 吊销: "
                    + leaf.getSubjectX500Principal().getName());
        }
    }

    /**
     * 加载 CRL：优先按 DER 解析，失败后按 PEM 解析。
     *
     * @param path CRL 文件路径
     * @return CRL
     */
    private X509CRL loadCrl(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            try {
                return (X509CRL) CertificateFactory.getInstance("X.509")
                        .generateCRL(new ByteArrayInputStream(bytes));
            } catch (Exception derFailure) {
                return parsePemCrl(path);
            }
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("CRL 文件读取失败: " + path + "（" + ex.getMessage() + "）", ex);
        }
    }

    /**
     * 解析 PEM 格式 CRL。
     *
     * @param path CRL 文件路径
     * @return CRL
     */
    private X509CRL parsePemCrl(Path path) {
        try (PEMParser parser = new PEMParser(new InputStreamReader(
                Files.newInputStream(path), StandardCharsets.US_ASCII))) {
            Object object;
            while ((object = parser.readObject()) != null) {
                if (object instanceof X509CRLHolder holder) {
                    return new JcaX509CRLConverter().setProvider(BC_PROVIDER).getCRL(holder);
                }
            }
        } catch (Exception ex) {
            throw new IllegalStateException("CRL 文件解析失败: " + path + "（" + ex.getMessage() + "）", ex);
        }
        throw new IllegalStateException("CRL 文件中未找到 X.509 CRL: " + path);
    }
}
