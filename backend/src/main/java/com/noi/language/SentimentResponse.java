package com.noi.language;

import java.util.ArrayList;
import java.util.List;

public class SentimentResponse {
    private final Sentiment documentSentiment;
    private final List<SentenceSentiment> sentenceSentiments = new ArrayList<>();
    private final String languageCode;
    private final boolean languageSupported;

    private SentimentResponse(String languageCode, boolean languageSupported, Sentiment documentSentiment) {
        this.languageCode = languageCode;
        this.languageSupported = languageSupported;
        this.documentSentiment = documentSentiment;
    }

    public static SentimentResponse create(String languageCode, boolean languageSupported, Sentiment documentSentiment) {
        return new SentimentResponse(languageCode, languageSupported, documentSentiment);
    }

    public void addSentences(List<SentenceSentiment> sentenceSentiments) {
        this.sentenceSentiments.addAll(sentenceSentiments);
    }

    @Override
    public String toString() {
        return "SentimentResponse{" +
                "languageCode='" + languageCode + '\'' +
                ", languageSupported=" + languageSupported +
                ", documentSentiment=" + documentSentiment +
                ", sentenceSentiments=" + sentenceSentiments +
                '}';
    }

    public Sentiment getDocumentSentiment() {
        return documentSentiment;
    }

    public List<SentenceSentiment> getSentenceSentiments() {
        return sentenceSentiments;
    }
}
