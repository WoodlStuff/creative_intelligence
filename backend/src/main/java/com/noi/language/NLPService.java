package com.noi.language;

import com.noi.requests.NoiRequest;

import java.io.IOException;

public abstract class NLPService {

    protected static final String TYPE_MODERATE = "moderate";
    protected static final String TYPE_CLASSIFY = "classify";

    public static NLPService getService() {
        return new GoogleNLPService();
    }

    // Finds named entities (currently proper names and common nouns) in the text along with entity types, probability, mentions for each entity, and other properties.
    abstract EntitiesResponse analyzeEntities(NLPRequest request) throws IOException;

    // Analyzes the sentiment of the provided text.
    abstract SentimentResponse analyzeSentiment(NLPRequest request) throws IOException;

    // A convenience method that provides all features in one call.
    public abstract AnnotationResponse annotateText(NLPRequest request, boolean persistResponses) throws IOException;

    // Classifies a document into categories.
    abstract ClassificationResponse classifyText(NLPRequest request) throws IOException;

    // Moderates a document for harmful and sensitive categories.
    abstract ClassificationResponse moderateText(NLPRequest request) throws IOException;

    public abstract String getModelName();
}
