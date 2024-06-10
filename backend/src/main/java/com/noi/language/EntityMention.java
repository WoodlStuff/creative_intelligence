package com.noi.language;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.noi.tools.JsonTools;

import java.util.ArrayList;
import java.util.List;

public class EntityMention {
    private final TextSpan text;
    private final TypeEnum type;
    private final Sentiment sentiment;
    private final double probability;

    private EntityMention(TextSpan text, TypeEnum type, Sentiment sentiment, double probability) {
        this.text = text;
        this.type = type;
        this.sentiment = sentiment;
        this.probability = probability;
    }

    public static List<EntityMention> parseArray(JsonObject entityJson, String jsonLabel) {
        List<EntityMention> mentions = new ArrayList<>();
        /*
        "mentions": [
            {
              object (EntityMention)
            }
         ],
         */
        JsonArray array = entityJson.getAsJsonArray(jsonLabel);
        if (array != null && !array.isJsonNull()) {
            for (int i = 0; i < array.size(); i++) {
                mentions.add(EntityMention.parse(array.get(i).getAsJsonObject()));
            }
        }

        return mentions;
    }

    private static EntityMention parse(JsonObject jsonObject) {
        TextSpan text = TextSpan.parse(jsonObject, "text");
        TypeEnum type = TypeEnum.parse(jsonObject, "type");
        Sentiment sentiment = Sentiment.parse(jsonObject, "sentiment");
        double probability = JsonTools.getAsDouble(jsonObject, "probability");

        return new EntityMention(text, type, sentiment, probability);
    /*
        -- EntityMention
        {
          "text": {
            object (TextSpan)
          },
          "type": enum (Type),
          "sentiment": {
            object (Sentiment)
          },
          "probability": number
        }
     */
    }

    public TextSpan getText() {
        return text;
    }

    public TypeEnum getType() {
        return type;
    }

    public Sentiment getSentiment() {
        return sentiment;
    }

    public double getProbability() {
        return probability;
    }
}
