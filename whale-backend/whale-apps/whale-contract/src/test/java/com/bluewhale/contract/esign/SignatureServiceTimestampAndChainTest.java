package com.bluewhale.contract.esign;

import com.bluewhale.contract.config.ContractEsignProperties;
import com.sun.net.httpserver.HttpServer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampResponseGenerator;
import org.bouncycastle.tsp.TimeStampTokenGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CA 证书链与 RFC 3161 时间戳测试：可信链签署、TSA 不可用 fail-closed、非信任根拒绝。
 *
 * @author BlueWhale
 */
class SignatureServiceTimestampAndChainTest {

    /** 测试证书口令 */
    private static final char[] PASSWORD = "changeit".toCharArray();

    /** 签名算法 */
    private static final String ALGORITHM = "SHA256withRSA";

    /** BouncyCastle Provider */
    private static final String BC = "BC";

    /** 临时目录 */
    @TempDir
    Path tempDir;

    private HttpServer tsaServer;

    /**
     * 注册 BouncyCastle Provider。
     */
    @BeforeAll
    static void registerProvider() {
        if (Security.getProvider(BC) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * 关闭测试用 TSA 服务。
     */
    @AfterEach
    void stopTsa() {
        if (tsaServer != null) {
            tsaServer.stop(0);
            tsaServer = null;
        }
    }

    /**
     * 配置持牌 CA 信任根与 TSA 后，签署件验签通过并带时间戳。
     *
     * @throws Exception 证书/时间戳异常
     */
    @Test
    void signsWithTimestampAndTrustedChain() throws Exception {
        TestPki pki = createPki();
        String tsaUrl = startTsa();
        ContractEsignProperties properties = properties(pki, tsaUrl);
        properties.setCaCertPath(writePem("ca.pem", pki.caCertificate()).toString());

        SignatureService service = new SignatureService(properties);
        SignatureService.SignatureOutcome outcome = service.sign(samplePdf());

        SignatureService.VerifyOutcome verify = service.verify(outcome.bytes(), outcome.certSerial());
        assertTrue(verify.valid(), verify.detail());
        assertTrue(verify.detail().contains("证书链已链接到配置的 CA 信任根"), verify.detail());
        assertTrue(verify.detail().contains("RFC3161 时间戳"), verify.detail());
    }

    /**
     * TSA 未启动时签署必须失败，不得静默降级为无时间戳。
     *
     * @throws Exception 证书异常
     */
    @Test
    void failsClosedWhenTsaUnavailable() throws Exception {
        TestPki pki = createPki();
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        ContractEsignProperties properties = properties(pki,
                "http://127.0.0.1:" + closedPort + "/tsa");
        properties.setTsaTimeoutMillis(1000);
        properties.setCaCertPath(writePem("ca.pem", pki.caCertificate()).toString());

        SignatureService service = new SignatureService(properties);
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.sign(samplePdf()));

        assertTrue(exception.getMessage().contains("时间戳") || exception.getMessage().contains("TSA"),
                exception.getMessage());
    }

    /**
     * 签名证书不在配置的信任根下时必须拒绝签署。
     *
     * @throws Exception 证书异常
     */
    @Test
    void rejectsUntrustedCertificateChain() throws Exception {
        TestPki pki = createPki();
        TestPki otherCa = createPki();
        ContractEsignProperties properties = properties(pki, null);
        properties.setCaCertPath(writePem("other-ca.pem", otherCa.caCertificate()).toString());

        SignatureService service = new SignatureService(properties);
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.sign(samplePdf()));

        assertTrue(exception.getMessage().contains("信任根"), exception.getMessage());
    }

    /**
     * 未配置 TSA 时验签不会因缺少时间戳失败。
     *
     * @throws Exception 证书异常
     */
    @Test
    void allowsMissingTimestampWhenNotRequired() throws Exception {
        TestPki pki = createPki();
        ContractEsignProperties properties = properties(pki, null);
        properties.setCaCertPath(writePem("ca.pem", pki.caCertificate()).toString());

        SignatureService service = new SignatureService(properties);
        SignatureService.SignatureOutcome outcome = service.sign(samplePdf());
        SignatureService.VerifyOutcome verify = service.verify(outcome.bytes(), outcome.certSerial());

        assertTrue(verify.valid(), verify.detail());
        assertTrue(verify.detail().contains("未包含 RFC 3161 时间戳"), verify.detail());
    }

    /**
     * 开启 tsa-required 后，缺少时间戳的签名必须判为不通过。
     *
     * @throws Exception 证书异常
     */
    @Test
    void rejectsMissingTimestampWhenRequired() throws Exception {
        TestPki pki = createPki();
        ContractEsignProperties properties = properties(pki, null);
        properties.setCaCertPath(writePem("ca.pem", pki.caCertificate()).toString());
        SignatureService signer = new SignatureService(properties);
        SignatureService.SignatureOutcome outcome = signer.sign(samplePdf());

        properties.setTsaRequired(true);
        SignatureService verifier = new SignatureService(properties);
        SignatureService.VerifyOutcome verify = verifier.verify(outcome.bytes(), outcome.certSerial());

        assertFalse(verify.valid(), verify.detail());
        assertTrue(verify.detail().contains("缺少 RFC 3161 时间戳"), verify.detail());
    }

    /**
     * 构建含 CA 与签名证书的 PKCS#12 配置。
     *
     * @param pki    测试 PKI
     * @param tsaUrl TSA 地址（为空表示不配置）
     * @return 配置
     */
    private ContractEsignProperties properties(TestPki pki, String tsaUrl) {
        ContractEsignProperties properties = new ContractEsignProperties();
        properties.setP12Path(pki.p12().toString());
        properties.setP12Password(new String(PASSWORD));
        if (tsaUrl != null) {
            properties.setTsaUrl(tsaUrl);
        }
        return properties;
    }

    /**
     * 启动 RFC 3161 测试 TSA（loopback）。
     *
     * @return TSA 地址
     * @throws Exception 启动异常
     */
    private String startTsa() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        X500Name subject = new X500Name("CN=BlueWhale Test TSA, O=BlueWhale");
        X509Certificate certificate = tsaCertificate(subject, keyPair);

        SignerInfoGenerator signerInfoGenerator = new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().setProvider(BC).build())
                .build(new JcaContentSignerBuilder(ALGORITHM).setProvider(BC).build(keyPair.getPrivate()),
                        certificate);
        TimeStampTokenGenerator tokenGenerator = new TimeStampTokenGenerator(signerInfoGenerator,
                new JcaDigestCalculatorProviderBuilder().setProvider(BC).build()
                        .get(new AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256)),
                new org.bouncycastle.asn1.ASN1ObjectIdentifier("1.2.3.4.1"));
        tokenGenerator.addCertificates(new JcaCertStore(List.of(certificate)));
        TimeStampResponseGenerator responseGenerator = new TimeStampResponseGenerator(tokenGenerator,
                Set.of(NISTObjectIdentifiers.id_sha256.getId()));

        tsaServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        tsaServer.createContext("/tsa", exchange -> {
            try {
                TimeStampRequest request = new TimeStampRequest(exchange.getRequestBody().readAllBytes());
                TimeStampResponse response = responseGenerator.generate(request,
                        BigInteger.valueOf(System.currentTimeMillis()), new Date());
                byte[] encoded = response.getEncoded();
                exchange.getResponseHeaders().add("Content-Type", "application/timestamp-reply");
                exchange.sendResponseHeaders(200, encoded.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(encoded);
                }
            } catch (Exception ex) {
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        });
        tsaServer.start();
        return "http://127.0.0.1:" + tsaServer.getAddress().getPort() + "/tsa";
    }

    /**
     * 生成带 timestamping 扩展密钥用途的测试 TSA 证书。
     *
     * @param subject 证书主题
     * @param keyPair TSA 密钥对
     * @return 自签 TSA 证书
     * @throws Exception 生成异常
     */
    private X509Certificate tsaCertificate(X500Name subject, KeyPair keyPair) throws Exception {
        Date notBefore = new Date(System.currentTimeMillis() - 86_400_000L);
        Date notAfter = new Date(System.currentTimeMillis() + 30L * 86_400_000L);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(subject,
                BigInteger.valueOf(System.currentTimeMillis()), notBefore, notAfter, subject,
                keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.extendedKeyUsage, true,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
        ContentSigner contentSigner = new JcaContentSignerBuilder(ALGORITHM).setProvider(BC)
                .build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().setProvider(BC)
                .getCertificate(builder.build(contentSigner));
    }

    /**
     * 生成独立测试 PKI：自签 CA + 由 CA 签发的签名证书 + 含证书链的 PKCS#12。
     *
     * @return 测试 PKI
     * @throws Exception 生成异常
     */
    private TestPki createPki() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair caKeyPair = generator.generateKeyPair();
        KeyPair signerKeyPair = generator.generateKeyPair();
        X500Name caName = new X500Name("CN=BlueWhale Test CA, O=BlueWhale");
        X509Certificate caCertificate = certificate(caName, caName, caKeyPair, caKeyPair, true,
                BigInteger.valueOf(System.currentTimeMillis()));

        X500Name signerName = new X500Name("CN=BlueWhale Test Signer, O=BlueWhale");
        X509Certificate signerCertificate = certificate(caName, signerName, signerKeyPair, caKeyPair,
                false, BigInteger.valueOf(System.currentTimeMillis() + 1));

        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);
        keyStore.setKeyEntry("signer", signerKeyPair.getPrivate(), PASSWORD,
                new Certificate[]{signerCertificate, caCertificate});
        Path p12 = tempDir.resolve("chain-" + System.nanoTime() + ".p12");
        try (OutputStream output = Files.newOutputStream(p12)) {
            keyStore.store(output, PASSWORD);
        }
        return new TestPki(caCertificate, p12);
    }

    /**
     * 生成一张由 issuer 签发的证书。
     *
     * @param issuerName  签发者主题
     * @param subjectName 证书主题
     * @param keyPair     证书公钥
     * @param issuerKey   签发者私钥
     * @param ca          是否 CA 证书
     * @param serial      序列号
     * @return 证书
     * @throws Exception 生成异常
     */
    private X509Certificate certificate(X500Name issuerName, X500Name subjectName, KeyPair keyPair,
                                        KeyPair issuerKey, boolean ca, BigInteger serial) throws Exception {
        Date notBefore = new Date(System.currentTimeMillis() - 86_400_000L);
        Date notAfter = new Date(System.currentTimeMillis() + 30L * 86_400_000L);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuerName, serial,
                notBefore, notAfter, subjectName, keyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        ContentSigner contentSigner = new JcaContentSignerBuilder(ALGORITHM).setProvider(BC)
                .build(issuerKey.getPrivate());
        return new JcaX509CertificateConverter().setProvider(BC)
                .getCertificate(builder.build(contentSigner));
    }

    /**
     * 将证书写入 PEM 文件。
     *
     * @param filename 文件名
     * @param certificate 证书
     * @return 文件路径
     * @throws Exception 写入异常
     */
    private Path writePem(String filename, X509Certificate certificate) throws Exception {
        Path path = tempDir.resolve(filename);
        try (JcaPEMWriter writer = new JcaPEMWriter(new OutputStreamWriter(
                Files.newOutputStream(path), StandardCharsets.US_ASCII))) {
            writer.writeObject(certificate);
        }
        return path;
    }

    /**
     * 生成一页测试 PDF。
     *
     * @return PDF 字节
     * @throws Exception 生成异常
     */
    private byte[] samplePdf() throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 12);
                stream.newLineAtOffset(72, 720);
                stream.showText("contract body");
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    /**
     * 测试 PKI。
     *
     * @param caCertificate CA 证书
     * @param p12           签名证书链 PKCS#12
     */
    private record TestPki(X509Certificate caCertificate, Path p12) {
    }
}
