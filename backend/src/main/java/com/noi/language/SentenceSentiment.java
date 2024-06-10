package com.noi.language;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

public class SentenceSentiment {
    private final TextSpan text;
    private final Sentiment sentiment;

    private SentenceSentiment(TextSpan text, Sentiment sentiment) {
        this.sentiment = sentiment;
        this.text = text;
    }

    public static List<SentenceSentiment> parseArray(JsonObject responseJson, String jsonLabel) {
        List<SentenceSentiment> sentiments = new ArrayList<>();

        JsonArray array = responseJson.getAsJsonArray(jsonLabel);
        if (array != null && !array.isJsonNull()) {
            for (int i = 0; i < array.size(); i++) {
                sentiments.add(SentenceSentiment.parse(array.get(i).getAsJsonObject()));
            }
        }
        /*
        "sentences": [
            {
              "text": {
                "content": "The rain in Spain stays mainly in the plain.",
                "beginOffset": -1
              },
              "sentiment": {
                "magnitude": 0.026,
                "score": -0.009
              }
            }
          ],
         */
        return sentiments;
    }

    private static SentenceSentiment parse(JsonObject jsonObject) {
        TextSpan text = TextSpan.parse(jsonObject, "text");
        Sentiment sentiment = Sentiment.parse(jsonObject, "sentiment");

        return new SentenceSentiment(text, sentiment);
        /*
        {
              "text": {
                "content": "The rain in Spain stays mainly in the plain.",
                "beginOffset": -1
              },
              "sentiment": {
                "magnitude": 0.026,
                "score": -0.009
              }
            }
         */
    }

    @Override
    public String toString() {
        return "SentenceSentiment{" +
                "text=" + text +
                ", sentiment=" + sentiment +
                '}';
    }

    public TextSpan getText() {
        return text;
    }

    public Sentiment getSentiment() {
        return sentiment;
    }
}
