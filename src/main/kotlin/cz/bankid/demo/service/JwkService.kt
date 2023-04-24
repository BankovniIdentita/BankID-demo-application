package cz.bankid.demo.service

import com.nimbusds.jose.jwk.*
import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.FileInputStream
import java.security.KeyStore

@Component
class JwkService (
    @Value("\${keystore.file}") val ksFile : String,
    @Value("\${keystore.password}") val ksPassword : String
        )  : InitializingBean {
    var fullJwks : JWKSet? = null

    companion object {
        const val CLASSPATH_PREFIX="classpath:"
    }

    override fun afterPropertiesSet() {
        val ks = KeyStore.getInstance("PKCS12")
        if (ksFile.startsWith(CLASSPATH_PREFIX)) {
            val filename = ksFile.substring(CLASSPATH_PREFIX.length)
            try {
                javaClass.classLoader.getResourceAsStream(filename)
                    .use { `is` -> ks.load(`is`, ksPassword.toCharArray()) }
            } catch (e: Exception) {
                throw RuntimeException("Could not load keys from classpath keystore $ksFile", e)
            }
        } else {

            try {
                FileInputStream(ksFile).use { `is` -> ks.load(`is`, ksPassword.toCharArray()) }
            } catch (e: Exception) {
                throw RuntimeException("Could not load keys from filesystem keystore $ksFile", e)
            }
        }
         fullJwks = JWKSet.load(ks) { ksPassword.toCharArray() }
    }

}