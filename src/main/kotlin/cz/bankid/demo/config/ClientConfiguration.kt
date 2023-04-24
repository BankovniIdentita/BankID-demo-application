package cz.bankid.demo.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "client")
class ClientConfiguration {
    var clientiId: String = ""
    var clientSecret: String = ""
    var redirectUri: String = ""
    var scopes: String = ""
}