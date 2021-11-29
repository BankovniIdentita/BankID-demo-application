package cz.bankid.demo.service

import com.nimbusds.jose.*
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.crypto.RSAEncrypter
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT

import cz.bankid.demo.jwks.IdpJwks
import cz.bankid.demo.service.KeyProvider.Companion.SIGN_KEY_ALIAS
import org.springframework.stereotype.Service

@Service
class TokenService (
    private val keyProvider: KeyProvider,
    private val mockJwks: IdpJwks
    ) {
    fun signJWT(claims: JWTClaimsSet, jwsAlgorithm: JWSAlgorithm): SignedJWT {
        val kid = SIGN_KEY_ALIAS
        val signer = ECDSASigner(keyProvider.getRpSignKeyPair().privateKey)
        val signedJWT = SignedJWT(
            JWSHeader.Builder(jwsAlgorithm).keyID(kid).build(),
            claims
        )
        signedJWT.sign(signer)
        return signedJWT
    }

    fun encryptJWT(jws: SignedJWT, encryptionAlg: JWEAlgorithm, encryptionEnc: EncryptionMethod): JWEObject {
        val jwe = JWEObject(
            JWEHeader.Builder(encryptionAlg, encryptionEnc)
                .contentType("JWT")
                .keyID("rp-encrypt")
                .build(),
            Payload(jws)
        )
        jwe.encrypt(RSAEncrypter(mockJwks.getKey()))
        return jwe
    }
}