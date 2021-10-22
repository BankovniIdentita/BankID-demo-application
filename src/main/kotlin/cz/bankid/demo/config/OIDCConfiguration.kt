package cz.bankid.demo.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "client")
data class OIDCConfiguration(
    var issuerUri: String = "",
    var redirectUri: String = "",
    var clientId: String = "",
    var clientSecret: String = "",
    var clientScopes: String = ""
)