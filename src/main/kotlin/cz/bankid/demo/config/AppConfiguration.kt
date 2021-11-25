package cz.bankid.demo.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate

@Configuration
class AppConfiguration {
    @Bean
    fun getRestTemplate() : RestTemplate {
        return RestTemplate()
    }
}