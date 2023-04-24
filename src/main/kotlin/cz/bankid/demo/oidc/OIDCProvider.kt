package cz.bankid.demo.oidc

import com.nimbusds.oauth2.sdk.*
import com.nimbusds.oauth2.sdk.auth.ClientSecretPost
import com.nimbusds.oauth2.sdk.auth.Secret
import com.nimbusds.oauth2.sdk.id.ClientID
import com.nimbusds.oauth2.sdk.id.Issuer
import com.nimbusds.oauth2.sdk.id.State
import com.nimbusds.openid.connect.sdk.*
import com.nimbusds.openid.connect.sdk.claims.UserInfo
import com.nimbusds.openid.connect.sdk.op.OIDCProviderConfigurationRequest
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata
import com.nimbusds.openid.connect.sdk.token.OIDCTokens
import cz.bankid.demo.config.OIDCConfiguration
import org.springframework.stereotype.Component
import java.net.URI

@Component
class OIDCProvider(
    private var odicConf: OIDCConfiguration
) {
    private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')

    companion object {
        const val RESPONSE_TYPE = "code"
        const val CONNECTION_TIMEOUT = 5000
    }

    fun getConfig(): OIDCProviderMetadata {
        val issuer = Issuer(odicConf.issuerUri)
        val request = OIDCProviderConfigurationRequest(issuer)
        val httpRequest = request.toHTTPRequest()
        httpRequest.connectTimeout = CONNECTION_TIMEOUT
        val httpResponse = httpRequest.send()
        return OIDCProviderMetadata.parse(httpResponse.contentAsJSONObject)
    }

    fun getAuthURI(configuration: OIDCProviderMetadata): URI {
        val callback = URI(odicConf.redirectUri)
        val state = State(getRandomState())
        val nonce = Nonce()
        val clientID = ClientID(odicConf.clientId)
        val prompt = Prompt(Prompt.Type.CONSENT)
        val authRequest = AuthenticationRequest.Builder(
            ResponseType(RESPONSE_TYPE),
            Scope.parse(odicConf.clientScopes),
            clientID,
            callback
        )
            .endpointURI(configuration.authorizationEndpointURI)
            .state(state)
            .nonce(nonce)
            .prompt(prompt)
            .build()
        //configuration.getCustomParameter("ros_endpoint")
        //configuration.getCustomParameter("authorize_endpoint")
        return authRequest.toURI()
    }

    fun getSignAuthUri(
    request_uri: String ): URI {
        val callback = URI(odicConf.redirectUri)
        val authRequest = AuthenticationRequest.Builder(URI(request_uri)).endpointURI(getConfig().authorizationEndpointURI)
            .redirectionURI(callback).build()
        return authRequest.toURI()
    }

    fun getTokens(codeIn: String, configuration: OIDCProviderMetadata): OIDCTokens? {
        val codeGrant = AuthorizationCodeGrant(
            AuthorizationCode(codeIn),
            URI(odicConf.redirectUri)
        )
        val clientAuth = ClientSecretPost(ClientID(odicConf.clientId), Secret(odicConf.clientSecret))
        val request = TokenRequest(configuration.tokenEndpointURI, clientAuth, codeGrant)
        val httpRequest = request.toHTTPRequest()
        httpRequest.connectTimeout = CONNECTION_TIMEOUT
        val tokenResponse = OIDCTokenResponseParser.parse(httpRequest.send())
        if (!tokenResponse.indicatesSuccess()) {
            val errorResponse: TokenErrorResponse = tokenResponse.toErrorResponse()
            System.err.println("errorResponse.toJSONObject().toString() = ${errorResponse.toJSONObject()}")
            return null
        }
        val successResponse = tokenResponse.toSuccessResponse()
        return successResponse.tokens.toOIDCTokens()
    }

    fun getUserInfo(tokens: OIDCTokens?, configuration: OIDCProviderMetadata?): UserInfo? {
        if (tokens == null || configuration == null) return null

        val httpResponse = UserInfoRequest(configuration.userInfoEndpointURI, tokens.bearerAccessToken)
            .toHTTPRequest()
            .send()
        val userInfoResponse = UserInfoResponse.parse(httpResponse)
        if (!userInfoResponse.indicatesSuccess()) {
            return null
        }
        return userInfoResponse.toSuccessResponse().userInfo

    }

    private fun getRandomState(): String = (1..10)
        .map { kotlin.random.Random.nextInt(0, charPool.size) }
        .map(charPool::get)
        .joinToString("");
}