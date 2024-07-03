package com.noi.embeddings;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.noi.image.AiImage;
import com.noi.image.label.LabelMetaData;
import com.noi.models.DbImage;
import com.noi.models.DbImageLabel;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public abstract class EmbeddingService {
    public static EmbeddingService getService() {
        // todo: how do we determine that?
        return new OpenAIEmbeddingService(null);
    }

    public static ImageEmbeddings getEmbeddings(Connection con, Long imageId, String categoryName) throws SQLException, EmbeddingException, IOException {
        // look up the image and the label metadata for it
        AiImage image = DbImage.find(con, imageId);
        Map<String, List<LabelMetaData>> metaValues = DbImageLabel.findLabelMetaCategories(con, image);

        // then get the embeddings for the categories
        EmbeddingService embeddingService = getService();

        // by formatting a json doc with all the category data contained
        JsonObject inputDoc = new JsonObject();
        JsonArray categories = new JsonArray();
        inputDoc.add("categories", categories);
        for (Map.Entry<String, List<LabelMetaData>> cat : metaValues.entrySet()) {
            // if a filter is provided: only the matching category will be added to the response
            if (categoryName == null || categoryName.equalsIgnoreCase(cat.getKey())) {
                JsonObject category = new JsonObject();
                categories.add(category);
                category.addProperty("category", cat.getKey());
                JsonArray labels = new JsonArray();
                category.add("labels", labels);
                for (LabelMetaData meta : cat.getValue()) {
                    JsonObject label = new JsonObject();
                    labels.add(label);
                    label.addProperty("model_name", meta.getModelName());
                    label.addProperty("key", meta.getKey());
                    label.addProperty("value", meta.getValue());
                }
            }
        }

        // and creating the embeddings for the resulting doc (one embedding per category)
        // 'vectors' is an array of {"category":"paralanguage","vector":[....]}, {"category": ...}]
        JsonArray vectors = embeddingService.createEmbeddings(con, image, categories);
        System.out.println("EmbeddingService:created embedding for " + image + ": size=" + vectors.size());

        int dimensions = -1;
        for (int i = 0; i < vectors.size(); i++) {
            JsonObject object = vectors.get(i).getAsJsonObject();
            int vectorSize = object.get("vector").getAsJsonArray().size();
            if (dimensions > 0 && dimensions != vectorSize) {
                System.out.printf("EmbeddingService:WARNING: embedding vector dimensions not consistent! %d<->%d%n", vectorSize, dimensions);
            } else {
                dimensions = vectorSize;
            }
        }

        return ImageEmbeddings.create(image, categoryName, categories, vectors, dimensions);
    }

    /**
     * create one embedding per category.
     * [{'category': 'a name here', 'vector':[0.01, 0.23, ...]}, {}]
     *
     * @param con
     * @param image
     * @param categories json array in format: [{'category': 'name here', 'labels':[{'model_name':'gpt-4o', 'key':'xx', 'value':'yyy'}, {}, ...]}, {'category': ...}, ...]
     * @return
     * @throws EmbeddingException
     * @throws IOException
     */
    public abstract JsonArray createEmbeddings(Connection con, AiImage image, JsonArray categories) throws EmbeddingException, IOException;

    public static class ImageEmbeddings {
        private final AiImage image;
        private final String categoryName;
        private final JsonArray categories;
        private final JsonArray vectors;
        private final int dimensions;

        private ImageEmbeddings(AiImage image, String categoryName, JsonArray categories, JsonArray vectors, int dimensions) {
            this.image = image;
            this.categoryName = categoryName;
            this.categories = categories;
            this.vectors = vectors;
            this.dimensions = dimensions;
        }

        /**
         * @param image        the image holding the labels
         * @param categoryName optional filter for only that category (in the labels for this image)
         * @param categories   is a json array with format: [{'category': 'name here', 'labels':[{'model_name':'gpt-4o', 'key':'xx', 'value':'yyy'}, {}, ...]}, {'category': ...}, ...]
         * @param vectors      is an array of {"category":"paralanguage","vector":[....]}, {"category": ...}]
         * @param dimensions   the dimension count of each vector element
         * @return
         */
        public static ImageEmbeddings create(AiImage image, String categoryName, JsonArray categories, JsonArray vectors, int dimensions) {
            return new ImageEmbeddings(image, categoryName, categories, vectors, dimensions);
        }

        @Override
        public String toString() {
            return "ImageEmbeddings{" +
                    "image=" + image +
                    ", categoryName='" + categoryName + '\'' +
                    ", dimensions=" + dimensions +
                    ", categories=" + categories +
                    ", vectors=" + vectors +
                    '}';
        }

        public AiImage getImage() {
            return image;
        }

        public String getCategoryName() {
            return categoryName;
        }

        public JsonArray getCategories() {
            return categories;
        }

        public JsonArray getVectors() {
            return vectors;
        }

        public boolean hasVectors() {
            return vectors != null && !vectors.isJsonNull() && vectors.size() > 0;
        }

        public int getDimensions() {
            return dimensions;
        }
    }
}
