package org.yuezhikong.utils.cert;

import lombok.Data;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class Certificate {
    /**
     * 密钥算法
     */
    public static final String KEY_ALGORITHM = "RSA";
    /**
     * 签名算法
     */
    public static final String SIGN_ALGORITHM = "SHA256WITHRSA";
    /**
     * 私钥类型
     */
    public static final String PRIVATE_KEY_TYPE = "PRIVATE KEY";
    /**
     * 公钥类型
     */
    public static final String PUBLIC_KEY_TYPE = "PUBLIC KEY";
    /**
     * 证书类型
     */
    public static final String CERTIFICATE_TYPE = "CERTIFICATE";

    public class SubjectBuilder {
        /**
         * 证书通用名称（Common Name）
         */
        private String cn;
        /**
         * 组织名称（Organization）
         */
        private String o;
        /**
         * 部门（Organizational Unit）
         */
        private String ou;
        /**
         * 国家（Country）
         */
        private String c;
        /**
         * 省份（State）
         */
        private String st;
        /**
         * 城市（Locality）
         */
        private String l;

        public SubjectBuilder setCn(String cn) {
            this.cn = cn;
            return this;
        }

        public SubjectBuilder setO(String o) {
            this.o = o;
            return this;
        }

        public SubjectBuilder setOu(String ou) {
            this.ou = ou;
            return this;
        }

        public SubjectBuilder setC(String c) {
            this.c = c;
            return this;
        }

        public SubjectBuilder setSt(String st) {
            this.st = st;
            return this;
        }

        public SubjectBuilder setL(String l) {
            this.l = l;
            return this;
        }

        public X500Name build() {
            X500NameBuilder x500NameBuilder = new X500NameBuilder();
            if (!cn.isBlank()) {
                x500NameBuilder.addRDN(BCStyle.CN, cn);
            }
            if (!o.isBlank()) {
                x500NameBuilder.addRDN(BCStyle.O, o);
            }
            if (!ou.isBlank()) {
                x500NameBuilder.addRDN(BCStyle.OU, ou);
            }
            if (!c.isBlank()) {
                x500NameBuilder.addRDN(BCStyle.C, c);
            }
            if (!st.isBlank()) {
                x500NameBuilder.addRDN(BCStyle.ST, st);
            }
            if (l.isBlank()) {
                x500NameBuilder.addRDN(BCStyle.L, l);
            }
            return x500NameBuilder.build();
        }
    }

    @Data
    public class KeyAndCertificate {
        /**
         * 私钥
         */
        private PrivateKey privateKey;
        /**
         * 公钥
         */
        private PublicKey publicKey;
        /**
         * 证书
         */
        private X509Certificate certificate;

        public KeyAndCertificate(PrivateKey privateKey, PublicKey publicKey, X509Certificate certificate) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
            this.certificate = certificate;
        }
    }

    public record keyAndCertificate(
            PrivateKey privateKey,
            org.bouncycastle.asn1.x509.Certificate certificate
    ){}

    public static keyAndCertificate generateCertificate(CertificateInfo info) throws Throwable{
        // 生成证书所需密钥对，RSA 算法，密钥长度 2048 字节
        KeyPair keyPair;
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();

        PrivateKey privateKey = keyPair.getPrivate();
        PublicKey publicKey = keyPair.getPublic();

        //证书信息
        X500Name subject = info.getSubject();
        SubjectPublicKeyInfo subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());
        JcaContentSignerBuilder jcaContentSignerBuilder = new JcaContentSignerBuilder(info.getSignAlgorithm());
        // 创建签名
        ContentSigner contentSigner = jcaContentSignerBuilder.build(privateKey);
        X509CertificateHolder x509CertificateHolder = new X509v3CertificateBuilder(
                info.getIssuer(),
                info.getSerial(),
                info.getNotBefore(),
                info.getNotAfter(),
                subject,
                subjectPublicKeyInfo
        )
                .addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
                .build(contentSigner);
        org.bouncycastle.asn1.x509.Certificate certificate = x509CertificateHolder.toASN1Structure();
        return new keyAndCertificate(privateKey, certificate);
    }

    public static X509Certificate generateSSLCertificate(CertificateInfo info, keyAndCertificate keyAndCertificate) throws Throwable{
        org.bouncycastle.asn1.x509.Certificate certificate = keyAndCertificate.certificate();
        PrivateKey privateKey = keyAndCertificate.privateKey();
        CertificateFactory factory = CertificateFactory.getInstance("X.509", "BC");
        KeyPair keypair;
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keypair = generator.generateKeyPair();
        PublicKey publicKey = keypair.getPublic();
        PrivateKey privateKey1 = keypair.getPrivate();
        long currentTimeMillis = System.currentTimeMillis();

        X500Name subject = info.getSubject();
        SubjectPublicKeyInfo subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(publicKey.getEncoded());
        JcaContentSignerBuilder jcaContentSignerBuilder = new JcaContentSignerBuilder(info.getSignAlgorithm());
        // 创建签名
        ContentSigner contentSigner = jcaContentSignerBuilder.build(privateKey);
        // 创建证书
        return new JcaX509CertificateConverter().getCertificate(
                new X509v3CertificateBuilder(
                        info.getSubject(), // 证书颁发者
                        BigInteger.valueOf(currentTimeMillis), // 证书序列号
                        new Date(currentTimeMillis),//证书生效时间
                        new Date(currentTimeMillis + TimeUnit.DAYS.toMillis(90)),//证书失效时间
                        subject, // 证书主体
                        SubjectPublicKeyInfo.getInstance(publicKey.getEncoded()) // 设置证书的公钥信息
                )
                        .addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment))
                        .addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(new KeyPurposeId[]{KeyPurposeId.id_kp_serverAuth, KeyPurposeId.id_kp_clientAuth}))
                        .addExtension(Extension.basicConstraints, true, new BasicConstraints(false))
                        .build(contentSigner)
        );
    }
}
