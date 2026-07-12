package com.example.rate_limit.interceptor;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.example.rate_limit.dto.response.RateLimitResponse;

@RestControllerAdvice
public class RateLimitHeaderAdvice implements ResponseBodyAdvice<RateLimitResponse> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.getParameterType().equals(RateLimitResponse.class);
    }

    @Override
    public RateLimitResponse beforeBodyWrite(RateLimitResponse body, MethodParameter returnType,
            MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request, ServerHttpResponse response) {
        
        if (body != null) {
            long resetAtEpochSec = (long) Math.ceil(body.getResetAt() / 1000.0);
            
            String limit = "100"; // fallback
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                Object limitAttr = attrs.getAttribute("rate-limit-limit", ServletRequestAttributes.SCOPE_REQUEST);
                if (limitAttr != null) {
                    limit = limitAttr.toString();
                }
            }

            response.getHeaders().add("X-RateLimit-Limit", limit);
            response.getHeaders().add("X-RateLimit-Remaining", String.valueOf(body.getRemaining()));
            response.getHeaders().add("X-RateLimit-Reset", String.valueOf(resetAtEpochSec));
        }
        return body;
    }
}
