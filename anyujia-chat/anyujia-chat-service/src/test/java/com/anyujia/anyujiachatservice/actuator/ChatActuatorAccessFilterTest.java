package com.anyujia.anyujiachatservice.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

class ChatActuatorAccessFilterTest {
    @Test
    void healthIsPublicButOperationalEndpointRequiresToken() throws Exception {
        ChatActuatorAccessFilter filter = filterWithToken("secret-token");

        MockHttpServletResponse healthResponse = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"),
                healthResponse, new MockFilterChain());
        assertThat(healthResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);

        MockHttpServletResponse deniedResponse = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/chatDataSourcePools"),
                deniedResponse, new MockFilterChain());
        assertThat(deniedResponse.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    void operationalEndpointAcceptsOnlyConfiguredToken() throws Exception {
        ChatActuatorAccessFilter filter = filterWithToken("secret-token");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.addHeader("X-Actuator-Token", "secret-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    private ChatActuatorAccessFilter filterWithToken(String token) {
        ChatActuatorAccessFilter filter = new ChatActuatorAccessFilter();
        ReflectionTestUtils.setField(filter, "accessToken", token);
        return filter;
    }
}
