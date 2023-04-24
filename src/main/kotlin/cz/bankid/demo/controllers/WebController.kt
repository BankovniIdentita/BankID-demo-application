package cz.bankid.demo.controllers

import com.nimbusds.openid.connect.sdk.claims.UserInfo
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata
import com.nimbusds.openid.connect.sdk.token.OIDCTokens
import cz.bankid.demo.config.OIDCConfiguration
import cz.bankid.demo.exception.DocumentNotSignedException
import cz.bankid.demo.oidc.OIDCProvider
import cz.bankid.demo.service.DocumentValidationService
import cz.bankid.demo.service.KeyProvider
import cz.bankid.demo.service.RosService
import net.minidev.json.JSONObject
import org.apache.logging.log4j.kotlin.Logging
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.SessionAttribute
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import org.springframework.web.servlet.view.RedirectView
import javax.servlet.http.HttpSession


@Controller
class WebController(
        private val oidcConfig: OIDCConfiguration,
        private val oidcProvider: OIDCProvider = OIDCProvider(oidcConfig),
        private val rosService: RosService,
        private val keyProvider: KeyProvider,
        private val documentValidationService: DocumentValidationService,
        @Value("\${client.plausible}")private val plausibleDomain: String
) : Logging {

    companion object {
        const val SESSION_CONFIG = "CONFIGURATION"
        const val SESSION_TOKENS = "TOKENS"
        const val SESSION_USERINFO = "USERINFO"

        const val VIEW_LOGIN = "login"
        const val VIEW_ERROR = "error"
        const val VIEW_INFO = "info"
        const val VIEW_PREVIEW = "preview"
        const val VIEW_SIGN_FINAL = "document"
        const val VIEW_REDIRECT = "redirect"
        const val VIEW_UPLOAD_DOCUMENT = "documentUpload"
        const val VIEW_DOCUMNET_INFO = "documentInfo"
        const val VIEW_DOCUMNET_ERROR = "documentError"

        const val STRUCTURED_SCOPES = "structured_scope"
        const val DOCUMENT_OBJECT = "documentObject"
        const val DOCUMENT_URI = "document_uri"
        const val REQUEST_URI = "request_uri"
    }

    @GetMapping("/")
    fun login(model: Model, session: HttpSession): String {
        val configuration = oidcProvider.getConfig()
        configuration.let {
            session.setAttribute(SESSION_CONFIG, configuration)
            model["title"] = "Login"
            model["loginurl"] = oidcProvider.getAuthURI(configuration)
            model["previewurl"] = "/${VIEW_PREVIEW}"
            model["documentUpload" ] = "/${VIEW_UPLOAD_DOCUMENT}"
            model["plausible_domain"] = plausibleDomain
        }
        return VIEW_LOGIN
    }

    @GetMapping("/callback")
    fun callback(
            model: Model,
            session: HttpSession,
            redirectAttributes: RedirectAttributes,
            @RequestParam(required = false) code: String?,
            @RequestParam state: String,
            @RequestParam(required = false) error: String?
    ): RedirectView {
        code?.let {
            var configuration = session.getAttribute(SESSION_CONFIG) as OIDCProviderMetadata
            configuration.let { configuration = oidcProvider.getConfig() }
            val tokens = oidcProvider.getTokens(code, configuration)
            val documentUri = if ( tokens!!.idToken.jwtClaimsSet.getJSONObjectClaim(STRUCTURED_SCOPES) != null ) {
                val documentObject = tokens.idToken.jwtClaimsSet.getJSONObjectClaim(STRUCTURED_SCOPES).get(DOCUMENT_OBJECT) as JSONObject
                documentObject[DOCUMENT_URI].toString()
            } else null

            documentUri?.let {
                session.setAttribute(DOCUMENT_URI, documentUri)
                model["documentUri"] = documentUri
                return RedirectView(VIEW_SIGN_FINAL)
            }
            tokens.let {
                session.setAttribute(SESSION_TOKENS, tokens)
                return RedirectView(VIEW_INFO)
            }
        }
        val redirectView = RedirectView(VIEW_ERROR, true)
        error?.let {
            if (error.equals("access_denied")) {
                redirectAttributes.addFlashAttribute("errorStatus","403")
                redirectAttributes.addFlashAttribute("errorMessage","Uživatel nedokončil přihlášení!")
            }
        }
        return redirectView
    }

    @GetMapping("/${VIEW_INFO}")
    fun form(model: Model,
             @SessionAttribute(name = SESSION_TOKENS) tokens: OIDCTokens?,
             @SessionAttribute(name = SESSION_CONFIG) config: OIDCProviderMetadata?,
             @SessionAttribute(name = SESSION_USERINFO) userinfo: UserInfo?
    ): String {
        var configuration: OIDCProviderMetadata? = config
        configuration?.let { configuration = oidcProvider.getConfig() }

        tokens?.let {
            val userInfo = oidcProvider.getUserInfo(tokens, configuration)

            model["logouturl"] = configuration?.endSessionEndpointURI?.toASCIIString() +
                    "?id_token_hint=${tokens.idToken.parsedString}"

            userInfo?.let {
                userInfo.name?.let { model["name"] = userInfo.name }
                userInfo.emailAddress?.let { model["email"] = userInfo.emailAddress }
                userInfo.phoneNumber?.let { model["phone_number"] = userInfo.phoneNumber }
                userInfo.subject?.let { model["sepSub"] = userInfo.subject }
                return VIEW_INFO
            }
        }
        return VIEW_ERROR
    }
    @GetMapping("/${VIEW_PREVIEW}")
    fun previewDocument(model: Model, session: HttpSession) : String    {
        val request_uri = rosService.postRos(oidcConfig.clientScopes)

        model["redirecturl"] = "/${VIEW_REDIRECT}"
        model["plausible_domain"] = plausibleDomain
        session.setAttribute(REQUEST_URI, request_uri)

        return VIEW_PREVIEW
    }

    @GetMapping("/${VIEW_REDIRECT}")
    fun redirectToBankID(model: Model, session: HttpSession) : ResponseEntity<Void>    {
        val uri = oidcProvider.getSignAuthUri( session.getAttribute(REQUEST_URI).toString())
        return ResponseEntity.status(HttpStatus.FOUND).location(uri).build()
    }

    @GetMapping("/${VIEW_SIGN_FINAL}")
    fun showDocumentLink(model: Model, session: HttpSession) : String    {
        model["downloadurl"] = session.getAttribute(DOCUMENT_URI)
        return VIEW_SIGN_FINAL
    }
    
    @GetMapping("/jwk", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun jwk() : ResponseEntity< String> {
        return ResponseEntity.ok(keyProvider.getPublicKeySet().toJSONObject().toString())
    }

    @GetMapping("/${VIEW_UPLOAD_DOCUMENT}")
    fun uploadDocument(model:Model, session: HttpSession) : String {
        return VIEW_UPLOAD_DOCUMENT
    }

    @PostMapping("/${VIEW_UPLOAD_DOCUMENT}")
    fun handleFileUpload(
        @RequestParam("file") file: MultipartFile,
        redirectAttributes: RedirectAttributes,
        model: Model,
        session: HttpSession
    ): String {
        //storageService.store(file)
        redirectAttributes.addFlashAttribute(
            "message",
            "You successfully uploaded " + file.originalFilename + "!"
        )
        session.setAttribute("request_id", documentValidationService.uploadDocumnet(file.resource))
        logger.info("Got request_id ${session.getAttribute("request_id")} on response")
        Thread.sleep(5000)
        val signatures =
            try {
            documentValidationService.getValidationResult(request_id = session.getAttribute("request_id").toString()).signatures!!
        } catch (e : DocumentNotSignedException) {
            return VIEW_DOCUMNET_ERROR
        }
        model["signatures"] = signatures!!
        return VIEW_DOCUMNET_INFO
    }
    @GetMapping("/${VIEW_DOCUMNET_INFO}")
    fun displayDocumentInfo(model:Model, session: HttpSession) : String {

        val signatures = documentValidationService.getValidationResult(request_id = session.getAttribute("request_id").toString()).signatures
        model["signatures"] = signatures!!
        return VIEW_DOCUMNET_INFO
    }
}