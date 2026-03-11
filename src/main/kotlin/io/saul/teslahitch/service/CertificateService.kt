package io.saul.teslahitch.service

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.PEMParser
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import jakarta.annotation.PostConstruct
import java.io.File
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Security
import java.security.spec.ECGenParameterSpec
import java.util.Date

@Service
class CertificateService(
    @Value("\${tesla.config.dir:/config}") private val configDir: String
) {
    private val logger = LoggerFactory.getLogger(CertificateService::class.java)

    init {
        Security.addProvider(BouncyCastleProvider())
    }

    @PostConstruct
    fun init() {
        val dir = File(configDir)
        if (!dir.exists()) dir.mkdirs()

        generateFleetKey()
        generateTlsCert()
    }

    /**
     * Generates the EC key pair (prime256v1) used for Tesla Fleet API vehicle commands.
     * Tesla vehicles only support prime256v1 (secp256r1/P-256) keys.
     * The public key must be hosted at /.well-known/appspecific/com.tesla.3p.public-key.pem
     * and registered with Tesla via the partner register endpoint.
     * The private key is used by the vehicle-command proxy to sign commands.
     */
    private fun generateFleetKey() {
        val keyFile = File(configDir, "fleet-key.pem")
        val pubKeyFile = File(configDir, "fleet-key-pub.pem")

        if (keyFile.exists() && pubKeyFile.exists()) {
            logger.info("Fleet key pair already exists, skipping generation.")
            return
        }

        logger.info("Generating EC key pair (prime256v1) for Tesla Fleet API...")
        val kpg = KeyPairGenerator.getInstance("EC", "BC")
        kpg.initialize(ECGenParameterSpec("prime256v1"))
        val fleetKeyPair = kpg.generateKeyPair()

        writePem(keyFile, fleetKeyPair.private)
        writePem(pubKeyFile, fleetKeyPair.public)

        logger.info("Fleet key pair written to {} and {}", keyFile.absolutePath, pubKeyFile.absolutePath)
    }

    /**
     * Generates a self-signed TLS certificate for the Tesla HTTP proxy.
     * Uses secp521r1 curve and includes the extensions Tesla's documentation specifies:
     * extendedKeyUsage=serverAuth, keyUsage=digitalSignature,keyCertSign,keyAgreement
     */
    private fun generateTlsCert() {
        val certFile = File(configDir, "tls-cert.pem")
        val keyFile = File(configDir, "tls-key.pem")

        if (certFile.exists() && keyFile.exists()) {
            logger.info("TLS certificate already exists, skipping generation.")
            return
        }

        logger.info("Generating self-signed TLS certificate (secp521r1) for Tesla HTTP proxy...")
        val kpg = KeyPairGenerator.getInstance("EC", "BC")
        kpg.initialize(ECGenParameterSpec("secp521r1"))
        val tlsKeyPair = kpg.generateKeyPair()

        val now = Date()
        val tenYears = Date(now.time + 10L * 365 * 24 * 60 * 60 * 1000)

        val issuer = X500Name("CN=localhost")
        val serial = BigInteger.valueOf(now.time)
        val subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(tlsKeyPair.public.encoded)

        val certBuilder = X509v3CertificateBuilder(
            issuer, serial, now, tenYears, issuer, subjectPublicKeyInfo
        )

        certBuilder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        certBuilder.addExtension(
            Extension.subjectAlternativeName, false,
            GeneralNames(arrayOf(
                GeneralName(GeneralName.dNSName, "localhost"),
                GeneralName(GeneralName.dNSName, "tesla_http_proxy")
            ))
        )
        certBuilder.addExtension(
            Extension.extendedKeyUsage, false,
            ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth)
        )
        certBuilder.addExtension(
            Extension.keyUsage, true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyCertSign or KeyUsage.keyAgreement)
        )

        val signer = JcaContentSignerBuilder("SHA256withECDSA").setProvider("BC").build(tlsKeyPair.private)
        val certHolder = certBuilder.build(signer)
        val cert = JcaX509CertificateConverter().setProvider("BC").getCertificate(certHolder)

        writePem(certFile, cert)
        writePem(keyFile, tlsKeyPair.private)

        logger.info("TLS certificate written to {} and {}", certFile.absolutePath, keyFile.absolutePath)
    }

    fun getPublicKeyPem(): String {
        val pubKeyFile = File(configDir, "fleet-key-pub.pem")
        if (pubKeyFile.exists()) {
            return pubKeyFile.readText()
        }
        throw IllegalStateException("Fleet public key not found. Certificate generation may have failed.")
    }

    private fun writePem(file: File, obj: Any) {
        file.writer().use { fw ->
            JcaPEMWriter(fw).use { pw ->
                pw.writeObject(obj)
            }
        }
    }
}
