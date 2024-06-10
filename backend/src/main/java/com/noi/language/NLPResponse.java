package com.noi.language;

import com.noi.requests.NoiRequest;
import com.noi.requests.NoiResponse;

public class NLPResponse extends NoiResponse {
    private final AnnotationResponse annotationResponse;

    protected NLPResponse(NoiRequest request, AnnotationResponse annotationResponse) {
        super(request);
        this.annotationResponse = annotationResponse;
    }

    public static NLPResponse create(NLPRequest request, AnnotationResponse annotationResponse) {
        return new NLPResponse(request, annotationResponse);
    }

    @Override
    public String toString() {
        return "NLPResponse{" +
                "annotationResponse=" + annotationResponse +
                '}';
    }
}
