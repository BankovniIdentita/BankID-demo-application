package cz.bankid.demo.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "issuer")
class IssuerConfiguration {
    var uri: String = ""
}