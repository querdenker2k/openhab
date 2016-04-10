package org.openhab.io.caldav.internal.exception;

public class OAuthException extends Exception {
    private int statusCode;

    public OAuthException(int statusCode) {
        super();
        this.statusCode = statusCode;
    }
}
