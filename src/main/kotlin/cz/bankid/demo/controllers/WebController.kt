package cz.bankid.demo.controllers

import com.nimbusds.openid.connect.sdk.claims.UserInfo
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata
import com.nimbusds.openid.connect.sdk.token.OIDCTokens
import cz.bankid.demo.config.OIDCConfiguration
import cz.bankid.demo.oidc.OIDCProvider
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.ui.set
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.SessionAttribute
import org.springframework.web.servlet.mvc.support.RedirectAttributes
import org.springframework.web.servlet.view.RedirectView
import javax.servlet.http.HttpSession

@Controller
class WebController(
        private val oidcConfig: OIDCConfiguration,
        private val oidcProvider: OIDCProvider = OIDCProvider(oidcConfig)
) {

    companion object {
        const val SESSION_CONFIG = "CONFIGURATION"
        const val SESSION_TOKENS = "TOKENS"
        const val SESSION_USERINFO = "USERINFO"

        const val VIEW_LOGIN = "login"
        const val VIEW_ERROR = "error"
        const val VIEW_INFO = "info"
    }

    @GetMapping("/")
    fun login(model: Model, session: HttpSession): String {
        val configuration = oidcProvider.getConfig()

        configuration.let {
            session.setAttribute(SESSION_CONFIG, configuration)
            model["title"] = "Login"
            model["loginurl"] = oidcProvider.getAuthURI(configuration)
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
            tokens?.let {
                session.setAttribute(SESSION_TOKENS, tokens)
                return RedirectView(VIEW_INFO)
            }
        }
        var redirectView = RedirectView(VIEW_ERROR, true)
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

                return VIEW_INFO
            }
        }
        return VIEW_ERROR
    }
}