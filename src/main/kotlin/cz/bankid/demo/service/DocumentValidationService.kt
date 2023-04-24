package cz.bankid.demo.service

import cz.bankid.demo.exception.DocumentNotSignedException
import org.apache.logging.log4j.kotlin.Logging
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.core.io.Resource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.getForEntity
import org.springframework.web.client.postForEntity
import java.io.File

@Service
class DocumentValidationService(
    val restTemplate: RestTemplate,
    @Value("\${client.issuer_uri}") val issuer : String
) : Logging {
    companion object {
        val url_fragment = "verification"
    }
    fun uploadDocumnet(file : Resource) :String {

        val validationReqeust = restTemplate.getForEntity<ValidationReqeust>(issuer+ url_fragment, mapOf(Pair("document_id", "")))
        uploadFile(file, validationReqeust.body!!.upload_uri)
        return validationReqeust.body!!.request_id
    }

    private fun uploadFile(
        file: Resource,
        uri: String
    ): HttpStatus {
        val fileStream = file.inputStream

        val headers = HttpHeaders().apply {
            contentType = MediaType.MULTIPART_FORM_DATA
        }
        val filename = file.filename
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
    fun getValidationResult(request_id: String) : Result {
        logger.info("Found request_id $request_id")
        val verificationResult = try {
            restTemplate.postForEntity(issuer + url_fragment, ResultRequest(request_id), Result::class.java).also {  }

        } catch (e: Exception) {
            when {
                e is HttpClientErrorException -> {
                    if (e.statusCode == HttpStatus.BAD_REQUEST)
                        throw DocumentNotSignedException()
                    else
                        throw e
                }
                else -> throw e
            }        }
        logger.info("Got result ${verificationResult.body}")
        return verificationResult.body!!

    }
    data class ValidationReqeust(
        val request_id: String,
        val upload_uri: String
    )
    data class ResultRequest(
        val request_id: String
    )
    data class Result (
        val signatures: List<Signature>? = null
            )
    class Signature(
        val description: String? = null,
        val revision: Int? = null,
        val signature_name:String? = null,
        val is_bankid : Boolean? = null,
        val valid : String? = null
    )
}