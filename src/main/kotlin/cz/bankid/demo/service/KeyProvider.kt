package cz.bankid.demo.service



import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSAlgorithm.*
import com.nimbusds.jose.jwk.*
import com.nimbusds.jose.util.Base64
import org.apache.logging.log4j.kotlin.Logging
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.stereotype.Component
import java.io.FileInputStream
import java.security.*
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey

@Component
class KeyProvider(
    @Value("\${keystore.password}") val ksPassword: String,
    @Value("\${keystore.file}") val ksFile: String
) {

    class ECKeyPair(val publicKey: ECPublicKey, val privateKey: ECPrivateKey)
    class RSAKeyPair(val publicKey: RSAPublicKey, val privateKey: RSAPrivateKey)

    companion object {
        const val SIGN_KEY_ALIAS = "rp-sign"
        const val ENCRYPT_KEY_ALIAS = "rp-encrypt"
    }

    val rpKeystore = KeyStore.getInstance("PKCS12")

    init {

        if (ksFile.startsWith(JwkService.CLASSPATH_PREFIX)) {
            val filename = ksFile.substring(JwkService.CLASSPATH_PREFIX.length)
            try {
                javaClass.classLoader.getResourceAsStream(filename)
                    .use { `is` -> rpKeystore.load(`is`, ksPassword.toCharArray()) }
            } catch (e: Exception) {
                throw RuntimeException("Could not load keys from classpath keystore $ksFile", e)
            }
        } else {

            try {
                FileInputStream(ksFile).use { `is` -> rpKeystore.load(`is`, ksPassword.toCharArray()) }
            } catch (e: Exception) {
                throw RuntimeException("Could not load keys from filesystem keystore $ksFile", e)
            }
        }
    }

    fun getRpSignKeyPair(): ECKeyPair {
        val publicKey = this.rpKeystore.getCertificate(SIGN_KEY_ALIAS).publicKey as ECPublicKey
        val privateKey = this.rpKeystore.getKey(SIGN_KEY_ALIAS, this.ksPassword.toCharArray()) as ECPrivateKey

        return ECKeyPair(publicKey, privateKey)
    }

    fun getRpEncryptKeyPair(): RSAKeyPair {
        val privateKey = this.rpKeystore.getKey(ENCRYPT_KEY_ALIAS, this.ksPassword.toCharArray())
        val publicKey = this.rpKeystore.getCertificate(ENCRYPT_KEY_ALIAS).publicKey

        if (publicKey !is RSAPublicKey || privateKey !is RSAPrivateKey) {
            throw InvalidKeyException("The RP encryption keys are not RSA keys")
        }

        return RSAKeyPair(publicKey, privateKey)
    }

    fun getPublicKeySet(): JWKSet {
        val signCert = this.rpKeystore.getCertificate(SIGN_KEY_ALIAS)
        val signPublicKey = signCert.publicKey as ECPublicKey

        val signJWK = ECKey.Builder(Curve.P_521, signPublicKey)
            .keyUse(KeyUse.SIGNATURE)
            .keyID(SIGN_KEY_ALIAS)
            .x509CertChain(listOf(Base64.encode(signCert.encoded)))
            .build()

        val encryptCert = this.rpKeystore.getCertificate(ENCRYPT_KEY_ALIAS)
        val encryptPublicKey = encryptCert.publicKey as RSAPublicKey

        val encryptJwk = RSAKey.Builder(encryptPublicKey)
            .keyUse(KeyUse.ENCRYPTION)
            .keyID(ENCRYPT_KEY_ALIAS)
            .x509CertChain(listOf(Base64.encode(encryptCert.encoded)))
            .build()

        val keys: List<JWK> = listOf(signJWK, encryptJwk)
        return JWKSet(keys)
    }


}