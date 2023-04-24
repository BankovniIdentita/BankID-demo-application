package cz.bankid.demo.jwks

import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import cz.bankid.demo.oidc.OIDCProvider
import org.springframework.stereotype.Component

@Component
class IdpJwks (
    oidcProvider: OIDCProvider ) {
    val url  = oidcProvider.getConfig().jwkSetURI.toURL();

    fun getKey() : RSAKey {
        val rsaKey = JWKSet.load(url).keys.let { it.find { it.keyUse == KeyUse.ENCRYPTION && it.keyID.contains("rp-encrypt")} }!!.toRSAKey()
        return rsaKey
    }
    fun getSignKey() : ECKey? {
        val rsaKey = JWKSet.load(url).keys.let { it.find { it.keyUse == KeyUse.SIGNATURE } }!!.toECKey()
        return rsaKey
    }

}