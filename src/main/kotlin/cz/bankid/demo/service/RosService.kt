package cz.bankid.demo.service

import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jwt.JWTClaimsSet
import cz.bankid.demo.config.OIDCConfiguration
import cz.bankid.demo.model.RosResponse
import cz.bankid.demo.oidc.OIDCProvider
import cz.bankid.demo.utils.CustomStringUtils
import org.apache.logging.log4j.kotlin.Logging
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.crypto.codec.Hex
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestTemplate
import java.net.URI
import java.security.MessageDigest
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

@Service
class RosService( @Value("\${client.client_id}") val clientId: String,
                  @Value("\${file.path}") val fileResource: Resource,
                  val oidcConfiguration: OIDCConfiguration,
                  val oidcProvider: OIDCProvider,
                  val tokenService: TokenService,
                  val restTemplate: RestTemplate
) : Logging {
    fun postRos(scopes: String): String {
        val ros = getRos(
            CustomStringUtils.getRandomString(),
            CustomStringUtils.getRandomString(),
            oidcConfiguration.clientScopes
        )
        val encRos = tokenService.encryptJWT(
            tokenService.signJWT(ros, JWSAlgorithm.ES512),
            oidcProvider.getConfig().requestObjectJWEAlgs.first(),
            EncryptionMethod.A256GCM
        )
        val mediaType = MediaType("application", "jwe")
        val headers = HttpHeaders().apply {
            contentType = mediaType
        }
        val response = restTemplate.postForEntity(
            URI.create(oidcProvider.getConfig().getCustomParameter("ros_endpoint").toString()),
            HttpEntity(encRos.serialize(), headers),
            RosResponse::class.java
        )
        uploadFile(response.body!!.upload_uri!!)
        logger.debug("Found requestUri ${response.body!!.request_uri!!}")
        return response.body!!.request_uri!!
    }

    private fun getRos(
        nonce: String,
        state: String,
        scopes: String
    ): JWTClaimsSet {
        val documentBuilder = JWTClaimsSet.Builder();

        val formatter = DateTimeFormatter.ISO_INSTANT
            .withZone(ZoneId.systemDefault())
        val file = fileResource.inputStream
        val signArea = JWTClaimsSet.Builder()
            .claim("page", 0)
            .claim("x-coordinate", 300)
            .claim("x-dist", 150)
            .claim("y-coordinate", 600)
            .claim("y-dist", 50)
            .build()
        val messageDigest = MessageDigest.getInstance("SHA-512")
        val docHash = String(Hex.encode(messageDigest.digest(file.readBytes())))
        val documentClaimsSet = documentBuilder.claim("document_id", "ID123456789")
            .claim("document_hash", docHash)
            .claim("hash_alg", "2.16.840.1.101.3.4.2.3")
            .claim("document_title", "BankID Demo document")
            .claim("document_subject", "Testovaci dokument BankID")
            .claim("document_language", "CS")
            .claim("document_created", "2021-11-16T00:00:00.0000Z")
            .claim("document_author", "Bankovni identita, a.s.")
            .claim("document_size", fileResource.contentLength())
            .claim("document_pages", 1)
            .claim("document_read_by_enduser", true)
            .claim("document_uri", "https://demo.bankid.cz/document/test/document")
            .claim("sign_area", signArea.toJSONObject())
            .build();

        val structedScopeClaims = JWTClaimsSet.Builder()
            .claim("documentObject", documentClaimsSet.toJSONObject())
            .build();

        val claimSet = JWTClaimsSet.Builder().claim("txn", UUID.randomUUID().toString())
            .claim("client_id", clientId)
            .claim("response_type", "code")
            .claim("max_age", 3000)
            .claim("nonce", nonce)
            .claim("state", state)
            .claim("scope", scopes)
            .claim("structured_scope", structedScopeClaims.toJSONObject()).build();

        return claimSet
    }

    private fun uploadFile(
        uri: String
    ): HttpStatus {
        val fileStream = fileResource.inputStream

        val headers = HttpHeaders().apply {
            contentType = MediaType.MULTIPART_FORM_DATA
        }
        val filename = fileResource.filename
        val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
        val resource: ByteArrayResource = object : ByteArrayResource(fileStream.readAllBytes()) {
            override fun getFilename(): String {
                return filename!!
            }
        }
        body.add("file", resource)
        val response = restTemplate.postForEntity(
            uri,
            HttpEntity(body, headers),
            Void::class.java
        )
        logger.debug("PDF file is uploaded")
        return response.statusCode
    }
}