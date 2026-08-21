package com.havenbank.backend.iam.service;

/**
 * Kinds of single-use, out-of-band tokens, each with its own Redis key space and TTL.
 */
public enum OneTimeTokenType {
    EMAIL_VERIFICATION("ott:verify:"),
    PASSWORD_RESET("ott:reset:");

    private final String keyPrefix;

    OneTimeTokenType(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public String keyPrefix() {
        return keyPrefix;
    }
}
