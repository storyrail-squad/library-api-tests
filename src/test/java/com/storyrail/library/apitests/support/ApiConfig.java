package com.storyrail.library.apitests.support;

/** Where the API under test runs: -Dapi.baseUrl, else API_BASE_URL, else the local default. */
public final class ApiConfig {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";

    private ApiConfig() {
    }

    public static String baseUrl() {
        String fromProperty = System.getProperty("api.baseUrl");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return stripTrailingSlash(fromProperty.trim());
        }
        String fromEnv = System.getenv("API_BASE_URL");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return stripTrailingSlash(fromEnv.trim());
        }
        return DEFAULT_BASE_URL;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
