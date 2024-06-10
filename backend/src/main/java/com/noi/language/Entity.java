package com.noi.language;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.noi.tools.JsonTools;

import java.util.ArrayList;
import java.util.List;

public class Entity {
    private final String name;
    private final TypeEnum type;
    private final MetaData metaData;
    private final List<EntityMention> mentions;
    private final Sentiment sentiment;

    private Entity(String name, TypeEnum type, MetaData metaData, List<EntityMention> mentions, Sentiment sentiment) {
        this.name = name;
        this.type = type;
        this.metaData = metaData;
        this.mentions = mentions;
        this.sentiment = sentiment;
    }

    public static List<Entity> parseArray(JsonObject responseJson, String arrayElementName) {
        List<Entity> entities = new ArrayList<>();
        JsonArray array = responseJson.getAsJsonArray(arrayElementName);
        if (array != null && !array.isJsonNull()) {
            for (int i = 0; i < array.size(); i++) {
                JsonObject entity = array.get(i).getAsJsonObject();
                Entity e = Entity.parse(entity);
                entities.add(e);
            }
        }

        return entities;
    }

    private static Entity parse(JsonObject entityJson) {
        String name = JsonTools.getAsString(entityJson, "name");
        TypeEnum type = TypeEnum.parse(entityJson, "type");
        MetaData meta = MetaData.parse(entityJson, "metadata");
        List<EntityMention> mentions = EntityMention.parseArray(entityJson, "mentions");
        Sentiment sentiment = Sentiment.parse(entityJson, "sentiment");

        return new Entity(name, type, meta, mentions, sentiment);

    /*
        -- Entity
    {
      "name": string,
      "type": enum (Type),
      "metadata": {
        string: string,
        ...
      },
      "mentions": [
        {
          object (EntityMention)
        }
      ],
      "sentiment": {
        object (Sentiment)
      }
    }
     */
    }

    @Override
    public String toString() {
        return "Entity{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", metaData=" + metaData +
                ", mentions=" + mentions +
                ", sentiment=" + sentiment +
                '}';
    }

    public String getName() {
        return name;
    }

    public TypeEnum getType() {
        return type;
    }

    public MetaData getMetaData() {
        return metaData;
    }

    public List<EntityMention> getMentions() {
        return mentions;
    }

    public Sentiment getSentiment() {
        return sentiment;
    }
}
