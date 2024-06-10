package com.noi.language;

import com.google.gson.JsonObject;
import com.noi.tools.JsonTools;

public class Sentiment {
    private Sentiment(double magnitude, double score) {
        this.magnitude = magnitude;
        this.score = score;
    }

    private final double magnitude, score;

    public static Sentiment parse(JsonObject jsonObject, String jsonLabel) {
        JsonObject sentimentNode = jsonObject.getAsJsonObject(jsonLabel);
        if (sentimentNode != null && !sentimentNode.isJsonNull()) {
            double magnitude = JsonTools.getAsDouble(sentimentNode, "magnitude");
            double score = JsonTools.getAsDouble(sentimentNode, "score");
            return new Sentiment(magnitude, score);
        }
        return null;
    }

    /*
        Sentiment {
            "magnitude":number,
            "score":number
        }
     */

    @Override
    public String toString() {
        return "Sentiment{" +
                "magnitude=" + magnitude +
                ", score=" + score +
                '}';
    }

    public double getMagnitude() {
        return magnitude;
    }

    public double getScore() {
        return score;
    }
}
