package com.havenbank.backend.shared.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Guards against a new endpoint going live with no {@link RateLimited} tier declared, silently
 * unlimited, because nothing about serving it looks wrong until it is abused.
 */
class RateLimitCoverageVerifierTest {

    static class TieredEndpoint {
        @RateLimited(RateLimitTier.STANDARD)
        public void handle() {
        }
    }

    static class UntieredEndpoint {
        public void handle() {
        }
    }

    @Test
    void passesWhenEveryAppOwnedHandlerIsTiered() throws NoSuchMethodException {
        RequestMappingHandlerMapping mapping = mappingWithHandler(
                new HandlerMethod(new TieredEndpoint(), TieredEndpoint.class.getMethod("handle")));

        assertThatCode(() -> new RateLimitCoverageVerifier(mapping).verifyEveryEndpointIsTiered())
                .doesNotThrowAnyException();
    }

    @Test
    void failsStartupWhenAnAppOwnedHandlerHasNoRateLimitedAnnotation() throws NoSuchMethodException {
        RequestMappingHandlerMapping mapping = mappingWithHandler(
                new HandlerMethod(new UntieredEndpoint(), UntieredEndpoint.class.getMethod("handle")));

        assertThatThrownBy(() -> new RateLimitCoverageVerifier(mapping).verifyEveryEndpointIsTiered())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UntieredEndpoint")
                .hasMessageContaining("handle");
    }

    @Test
    void ignoresHandlersOutsideOurOwnPackage() throws NoSuchMethodException {
        // Stands in for a framework/library-owned handler (Boot's error controller, springdoc) -
        // not ours to annotate, and explicitly out of scope for this check.
        Method frameworkMethod = ArrayList.class.getMethod("size");
        RequestMappingHandlerMapping mapping = mappingWithHandler(
                new HandlerMethod(new ArrayList<>(), frameworkMethod));

        assertThatCode(() -> new RateLimitCoverageVerifier(mapping).verifyEveryEndpointIsTiered())
                .doesNotThrowAnyException();
    }

    @Test
    void listsEveryUncoveredEndpointNotJustTheFirst() throws NoSuchMethodException {
        Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();
        handlers.put(mock(RequestMappingInfo.class),
                new HandlerMethod(new UntieredEndpoint(), UntieredEndpoint.class.getMethod("handle")));
        handlers.put(mock(RequestMappingInfo.class),
                new HandlerMethod(new TieredEndpoint(), TieredEndpoint.class.getMethod("handle")));

        RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);
        when(mapping.getHandlerMethods()).thenReturn(handlers);

        assertThatThrownBy(() -> new RateLimitCoverageVerifier(mapping).verifyEveryEndpointIsTiered())
                .hasMessageContaining("UntieredEndpoint")
                .hasMessageNotContaining("TieredEndpoint");
    }

    private RequestMappingHandlerMapping mappingWithHandler(HandlerMethod handlerMethod) {
        RequestMappingHandlerMapping mapping = mock(RequestMappingHandlerMapping.class);
        Map<RequestMappingInfo, HandlerMethod> handlers = new LinkedHashMap<>();
        handlers.put(mock(RequestMappingInfo.class), handlerMethod);
        when(mapping.getHandlerMethods()).thenReturn(handlers);
        return mapping;
    }
}