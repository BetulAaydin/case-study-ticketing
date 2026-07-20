package com.turkcell.mayacore.apigateway.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPropertiesTest {

    @Test
    void settersAndGetters_shouldRoundTrip() {
        RateLimitProperties props = new RateLimitProperties();
        props.setEnabled(false);
        props.setWindowSeconds(30);
        props.setLoginMaxRequests(3);
        props.setAuthenticatedMaxRequests(200);
        props.setAnonymousMaxRequests(25);

        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getWindowSeconds()).isEqualTo(30);
        assertThat(props.getLoginMaxRequests()).isEqualTo(3);
        assertThat(props.getAuthenticatedMaxRequests()).isEqualTo(200);
        assertThat(props.getAnonymousMaxRequests()).isEqualTo(25);
    }

    @Test
    void defaults_shouldMatchSkillExpectations() {
        RateLimitProperties props = new RateLimitProperties();
        assertThat(props.isEnabled()).isTrue();
        assertThat(props.getWindowSeconds()).isEqualTo(60);
        assertThat(props.getLoginMaxRequests()).isEqualTo(5);
        assertThat(props.getAuthenticatedMaxRequests()).isEqualTo(100);
        assertThat(props.getAnonymousMaxRequests()).isEqualTo(50);
    }
}
