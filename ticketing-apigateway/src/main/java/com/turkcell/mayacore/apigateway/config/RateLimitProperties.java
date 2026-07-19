package com.turkcell.mayacore.apigateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "security.rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int windowSeconds = 60;
    private int loginMaxRequests = 5;
    private int authenticatedMaxRequests = 100;
    private int anonymousMaxRequests = 50;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(int windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public int getLoginMaxRequests() {
        return loginMaxRequests;
    }

    public void setLoginMaxRequests(int loginMaxRequests) {
        this.loginMaxRequests = loginMaxRequests;
    }

    public int getAuthenticatedMaxRequests() {
        return authenticatedMaxRequests;
    }

    public void setAuthenticatedMaxRequests(int authenticatedMaxRequests) {
        this.authenticatedMaxRequests = authenticatedMaxRequests;
    }

    public int getAnonymousMaxRequests() {
        return anonymousMaxRequests;
    }

    public void setAnonymousMaxRequests(int anonymousMaxRequests) {
        this.anonymousMaxRequests = anonymousMaxRequests;
    }
}
