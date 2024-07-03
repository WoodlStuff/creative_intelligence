package com.noi.embeddings;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.noi.image.AiImage;
import com.noi.image.label.LabelMetaData;
import com.noi.image.label.LabelService;
import com.noi.models.DbImage;
import com.noi.models.DbImageLabel;

import javax.naming.NamingException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class EmbeddingService {
    public static EmbeddingService getService() {
        // todo: how do we determine that?
        return new OpenAIEmbeddingService(null);
    }

    protected abstract JsonArray createEmbedding(AiImage image, JsonObject inputDoc) throws EmbeddingException, IOException;

    /**
     * create one embedding per category.
     * [{'category': 'a name here', 'vector':[0.01, 0.23, ...]}, {}]
     *
     * @param image
     * @param categories
     * @return
     * @throws EmbeddingException
     * @throws IOException
     */
    public abstract JsonArray createEmbeddings(AiImage image, JsonArray categories) throws EmbeddingException, IOException;

    public static JsonObject formatImageLabels(Connection con, Long imageId, String categoryName) throws SQLException, NamingException {
        AiImage image = DbImage.find(con, imageId);
        // todo: do we care about the annotations?
        // List<AiImageLabel> annotations = DbImageLabel.findAnnotations(con, image);
        Map<String, List<LabelMetaData>> metaValues = DbImageLabel.findLabelMetaCategories(con, image);
        return LabelService.addImageLabels(image, new ArrayList<>(), metaValues, categoryName);
    }
}
