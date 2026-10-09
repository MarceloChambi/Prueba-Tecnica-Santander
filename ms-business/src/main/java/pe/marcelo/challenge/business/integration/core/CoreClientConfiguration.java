package pe.marcelo.challenge.business.integration.core;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class CoreClientConfiguration {
    @Bean
    RestClient coreRestClient(@Value("${app.core.url}") String url,
                              @Value("${app.core.timeout-ms}") long timeoutMs) {
        var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(timeoutMs));
        return RestClient.builder().baseUrl(url).requestFactory(requestFactory).build();
    }
}
