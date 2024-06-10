package com.noi.requests;

import com.noi.image.AiImageRequest;

import javax.servlet.http.HttpServletResponse;

public class NoiResponse {
    private final NoiRequest request;
    protected int errorCode = HttpServletResponse.SC_OK;
    protected String errorMessage; // http error response content

    public boolean isErrorResponse() {
        return errorCode != HttpServletResponse.SC_OK;
    }

    public int getErrorCode() {
        return errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    protected NoiResponse(NoiRequest request) {
        this.request = request;
    }

    public NoiRequest getRequest() {
        return request;
    }
}
