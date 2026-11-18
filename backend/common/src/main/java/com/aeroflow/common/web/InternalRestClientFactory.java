package com.aeroflow.common.web;

import com.aeroflow.common.security.ServiceTokenProvider;
import java.time.Duration;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Builds RestClients for internal calls: timeouts, service JWT and correlation-id propagation. */
@Component
public class InternalRestClientFactory {
    private final ServiceTokenProvider tokens;

    public InternalRestClientFactory(ServiceTokenProvider tokens) {
        this.tokens = tokens;
    }

    public RestClient create(String baseUrl) {
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(2));
        rf.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(rf)
                .requestInterceptor((req, body, exec) -> {
                    req.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + tokens.token());
                    String cid = MDC.get(CorrelationIdFilter.MDC_KEY);
                    if (cid != null) req.getHeaders().set(CorrelationIdFilter.HEADER, cid);
                    return exec.execute(req, body);
                })
                .build();
    }
}
