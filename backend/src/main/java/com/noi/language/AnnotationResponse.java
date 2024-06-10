package com.noi.language;

public class AnnotationResponse {
    private final String languageCode;
    private final boolean languageSupported;
    private final SentimentResponse sentimentResponse;
    private final EntitiesResponse entityResponse;
    private final ClassificationResponse classificationResponse, categoryResponse;

    private AnnotationResponse(String languageCode, boolean languageSupported, SentimentResponse sentimentResponse, EntitiesResponse entityResponse, ClassificationResponse classificationResponse, ClassificationResponse categoryResponse) {
        this.languageCode = languageCode;
        this.languageSupported = languageSupported;
        this.sentimentResponse = sentimentResponse;
        this.entityResponse = entityResponse;
        this.classificationResponse = classificationResponse;
        this.categoryResponse = categoryResponse;
    }

    public static AnnotationResponse create(String languageCode, boolean languageSupported, SentimentResponse sentimentResponse, EntitiesResponse entityResponse, ClassificationResponse classificationResponse, ClassificationResponse categoryResponse) {
        return new AnnotationResponse(languageCode, languageSupported, sentimentResponse, entityResponse, classificationResponse, categoryResponse);
    }

    @Override
    public String toString() {
        return "AnnotationResponse{" +
                "languageCode='" + languageCode + '\'' +
                ", languageSupported=" + languageSupported +
                ", sentimentResponse=" + sentimentResponse +
                ", entityResponse=" + entityResponse +
                ", classificationResponse=" + classificationResponse +
                ", categoryResponse=" + categoryResponse +
                '}';
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public boolean isLanguageSupported() {
        return languageSupported;
    }

    public SentimentResponse getSentimentResponse() {
        return sentimentResponse;
    }

    public EntitiesResponse getEntityResponse() {
        return entityResponse;
    }

    public ClassificationResponse getClassificationResponse() {
        return classificationResponse;
    }

    public ClassificationResponse getCategoryResponse() {
        return categoryResponse;
    }
}
