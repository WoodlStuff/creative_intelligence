package com.noi.models;

import com.noi.AiModel;
import com.noi.Status;
import com.noi.image.AiImage;
import com.noi.image.label.*;
import com.noi.language.AiImageLabelRequest;
import com.noi.language.MetaKeyImages;
import com.noi.language.MetaKeyValues;
import com.noi.requests.NoiRequest;

import javax.naming.NamingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DbImageLabel extends Model {
//    public static Map<AiImage, AiImageLabel> find(String requestUUID) throws SQLException, NamingException {
//        Connection con = null;
//        try {
//            con = connectX();
//            return find(con, requestUUID);
//        } finally {
//            close(con);
//        }
//    }

//    public static Map<AiImage, AiImageLabel> find(Connection con, String requestUUID) throws SQLException {
//        NoiRequest request = DbRequest.find(con, requestUUID);
//        if (request == null) {
//            throw new IllegalArgumentException();
//        }
//
//        Map<AiImage, AiImageLabel> labelMap = new HashMap<>();
//
//        find(con, request, labelMap);
//        findAnnotation(con, request, labelMap);
//
//        return labelMap;
//    }

    private static void find(Connection con, NoiRequest request, Map<AiImage, AiImageLabel> labelMap) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select id, ai_image_id, model_name, label from ai_image_labels where ai_request_id=?");
            stmt.setLong(1, request.getId());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Long imageId = rs.getLong("ai_image_id");
                AiImage image = DbImage.find(con, imageId);
                Map<String, List<LabelMetaData>> labelCategories = findLabelMetaCategories(con, rs.getLong("id"));
                AiImageLabel label = AiImageLabel.create(image, request.getUUID(), rs.getString("model_name"), rs.getString("label"), labelCategories);
                labelMap.put(image, label);
            }
        } finally {
            close(stmt);
        }
    }

    private static Map<String, List<LabelMetaData>> findLabelMetaCategories(Connection con, Long imageLabelId) throws SQLException {
        Map<String, List<LabelMetaData>> labelCategories = new HashMap<>();
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select mc.name category_name, lm.meta_key, lm.meta_value from ai_image_label_meta_categories lm join meta_categories mc on mc.id = lm.meta_category_id where lm.ai_image_label_id=?");
            stmt.setLong(1, imageLabelId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String categoryName = rs.getString("category_name");
                List<LabelMetaData> metas = labelCategories.get(categoryName);
                if (metas == null) {
                    metas = new ArrayList<>();
                    labelCategories.put(categoryName, metas);
                }
                metas.add(LabelMetaData.create(rs.getString("meta_key"), rs.getString("meta_value")));
            }
        } finally {
            close(stmt);
        }

        return labelCategories;
    }

    public static Map<String, List<LabelMetaData>> findLabelMetaCategories(Connection con, AiImage image) throws SQLException {
        Map<String, List<LabelMetaData>> labelCategories = new HashMap<>();
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select r.uuid label_request_uuid, p.prompt_type, r.model_name, c.name category_name, mc.meta_key, group_concat(mc.meta_value) meta_values from ai_label_requests r join ai_prompts p on p.id=r.ai_prompt_id join ai_image_labels l on l.ai_label_request_id = r.id join ai_image_label_meta_categories mc on mc.ai_image_label_id = l.id join meta_categories c on c.id = mc.meta_category_id  where r.ai_image_id=? and r.status=? group by uuid, p.prompt_type, r.model_name, c.name, mc.meta_key order by c.name, mc.meta_key");
            stmt.setLong(1, image.getId());
            stmt.setInt(2, Status.COMPLETE.getStatus());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String categoryName = rs.getString("category_name");
                List<LabelMetaData> metas = labelCategories.get(categoryName);
                if (metas == null) {
                    metas = new ArrayList<>();
                    labelCategories.put(categoryName, metas);
                }
                metas.add(LabelMetaData.create(rs.getString("label_request_uuid"), rs.getString("model_name"), rs.getString("meta_key"), rs.getString("meta_values")));
            }
        } finally {
            close(stmt);
        }

        return labelCategories;
    }

    public static List<AiImageLabel> findAnnotations(Connection con, AiImage image) throws SQLException {
        List<AiImageLabel> labels = new ArrayList<>();
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select l.id, r.uuid, l.model_name, l.label, l.mid, l.score, l.topicality from ai_image_label_annotations l join ai_label_requests r on r.id = l.ai_label_request_id where l.ai_image_id=? and r.status=?");
            stmt.setLong(1, image.getId());
            stmt.setInt(2, Status.COMPLETE.getStatus());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                labels.add(AiImageLabel.create(image, rs.getString("uuid"), rs.getString("model_name"), rs.getString("label"), rs.getString("mid"), rs.getDouble("score"), rs.getDouble("topicality")));
            }
        } finally {
            close(stmt);
        }

        return labels;
    }

    public static void insert(AiImageLabelRequest request, List<AiImageLabel> imageLabels) throws SQLException, NamingException {
        Connection con = null;
        try {
            con = connectX();
            insert(con, request, imageLabels);
        } finally {
            close(con);
        }
    }

    public static List<AiImageLabel> insert(Connection con, AiImageLabelRequest request, List<AiImageLabel> imageLabels) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_image_labels(ai_label_request_id, ai_image_id, model_name, label, status, created_at, updated_at) values(?,?,?,?,?,now(),now())");
            stmt.setLong(1, request.getId());
            stmt.setInt(5, Status.NEW.getStatus());

            for (AiImageLabel label : imageLabels) {
                stmt.setLong(2, label.getImage().getId());
                stmt.setString(3, label.getModelName());
                stmt.setString(4, label.getContent());
                Long id = executeWithLastId(stmt);
                if (id > 0L) {
                    label.setId(id);

                    // persist any label meta data ...
                    insertMetaCategories(con, label);
                    insertMetaColorGradients(con, label);
                    //insertMetaContents(con, label);
                    insertMetaObjects(con, label);
                    insertMetaShadows(con, label);
                }
            }
        } finally {
            close(stmt);
        }

        return imageLabels;
    }

    private static void insertMetaShadows(Connection con, AiImageLabel label) throws SQLException {
        if (label.getLabelCategories().isEmpty()) {
            return;
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_image_label_meta_shadows(ai_image_id, ai_image_label_id, subject, description, status, created_at, updated_at) values(?,?,?,?,?,now(),now())");
            // check if any of the lable metadata has color gradients
            for (Map.Entry<String, List<LabelMetaData>> entry : label.getLabelCategories().entrySet()) {
                for (LabelMetaData metaData : entry.getValue()) {
                    if (metaData.hasMetaShadow()) {
                        LabelMetaShadow shadow = metaData.getLabelMetaShadow();
                        // persist it and link it to the image and the label!
                        stmt.setLong(1, label.getImage().getId());
                        stmt.setLong(2, label.getId());
                        stmt.setString(3, shadow.getSubject());
                        stmt.setString(4, shadow.getDescription());
                        stmt.setInt(5, Status.ACTIVE.getStatus());
                        stmt.executeUpdate();
                    }
                }
            }
        } finally {
            close(stmt);
        }
    }

    private static void insertMetaObjects(Connection con, AiImageLabel label) throws SQLException {
        if (label.getLabelCategories().isEmpty()) {
            return;
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_image_label_meta_objects(ai_image_id, ai_image_label_id, object, object_type, object_value, location, relative_size, color, background_color, brand, gender, status,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?,?,?,now(),now())");
            // check if any of the lable metadata has color gradients
            for (Map.Entry<String, List<LabelMetaData>> entry : label.getLabelCategories().entrySet()) {
                for (LabelMetaData metaData : entry.getValue()) {
                    if (metaData.hasMetaObject()) {
                        LabelMetaObject object = metaData.getLabelMetaObject();
                        // persist it and link it to the image and the label!
                        stmt.setLong(1, label.getImage().getId());
                        stmt.setLong(2, label.getId());
                        stmt.setString(3, object.getObject());
                        stmt.setString(4, object.getType());
                        stmt.setString(5, object.getValue());
                        stmt.setString(6, object.getLocation());
                        stmt.setString(7, object.getRelativeSize());
                        stmt.setString(8, object.getColor());
                        stmt.setString(9, object.getBackgroundColor());
                        stmt.setString(10, object.getBrand());
                        stmt.setString(11, object.getGender());
                        stmt.setInt(12, Status.ACTIVE.getStatus());
                        stmt.executeUpdate();
                    }
                }
            }
        } finally {
            close(stmt);
        }
    }

//    private static void insertMetaContents(Connection con, AiImageLabel label) throws SQLException {
//        if (label.getLabelCategories().isEmpty()) {
//            return;
//        }
//
//        PreparedStatement stmt = null;
//        try {
//            stmt = con.prepareStatement("insert into ai_image_label_meta_contents(ai_image_id,ai_image_label_id,content,elementType,location,relativeSize,color,backgroundColor,font,status,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?,now(),now())");
//            // check if any of the lable metadata has color gradients
//            for (Map.Entry<String, List<LabelMetaData>> entry : label.getLabelCategories().entrySet()) {
//                for (LabelMetaData metaData : entry.getValue()) {
//                    if (metaData.hasMetaContent()) {
//                        LabelMetaContent content = metaData.getLabelMetaContent();
//                        // persist it and link it to the image and the label!
//                        stmt.setLong(1, label.getImage().getId());
//                        stmt.setLong(2, label.getId());
//                        stmt.setString(3, content.getContent());
//                        stmt.setString(4, content.getElementType());
//                        stmt.setString(5, content.getLocation());
//                        stmt.setString(6, content.getRelativeSize());
//                        stmt.setString(7, content.getColor());
//                        stmt.setString(8, content.getBackgroundColor());
//                        stmt.setString(9, content.getFont());
//                        stmt.setInt(10, Status.ACTIVE.getStatus());
//                        stmt.executeUpdate();
//                    }
//                }
//            }
//        } finally {
//            close(stmt);
//        }
//    }

    private static void insertMetaColorGradients(Connection con, AiImageLabel label) throws SQLException {
        if (label.getLabelCategories().isEmpty()) {
            return;
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_image_label_meta_color_gradients(ai_image_id, ai_image_label_id, location, description, status, created_at, updated_at) values(?,?,?,?,?,now(),now())");
            // check if any of the lable metadata has color gradients
            for (Map.Entry<String, List<LabelMetaData>> entry : label.getLabelCategories().entrySet()) {
                for (LabelMetaData metaData : entry.getValue()) {
                    if (metaData.hasMetaColorGradient()) {
                        LabelMetaColorGradient colorGradient = metaData.getLabelMetaColorGradient();
                        // persist it and link it to the image and the label!
                        stmt.setLong(1, label.getImage().getId());
                        stmt.setLong(2, label.getId());
                        stmt.setString(3, colorGradient.getLocation());
                        stmt.setString(4, colorGradient.getDescription());
                        stmt.setInt(5, Status.ACTIVE.getStatus());
                        stmt.executeUpdate();
                    }
                }
            }
        } finally {
            close(stmt);
        }
    }

    private static void insertMetaCategories(Connection con, AiImageLabel label) throws SQLException {
        if (label.getLabelCategories().isEmpty()) {
            return;
        }

        Map<String, Long> categories = findAllCategories(con);

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_image_label_meta_categories(ai_image_id, ai_image_label_id, meta_category_id, meta_key, meta_value, status, created_at, updated_at) values(?,?,?,?,?,?,now(),now())");
            stmt.setLong(1, label.getImage().getId());
            stmt.setLong(2, label.getId());
            stmt.setInt(6, Status.ACTIVE.getStatus());

            for (Map.Entry<String, List<LabelMetaData>> entry : label.getLabelCategories().entrySet()) {
                stmt.setLong(3, lookupCategoryId(con, categories, entry.getKey()));
                for (LabelMetaData lm : entry.getValue()) {
                    if ("none".equalsIgnoreCase(lm.getValue())) {
                        // skip 'none' values
                        continue;
                    }
                    stmt.setString(4, lm.getKey());
                    stmt.setString(5, lm.getValue());
                    stmt.executeUpdate();
                }
            }

        } finally {
            close(stmt);
        }
    }

    // lookup the id of the category (or create a new one if we don't have it yet...)
    private static Long lookupCategoryId(Connection con, Map<String, Long> categoryIds, String categoryName) throws SQLException {
        // look in cache first
        Long categoryId = categoryIds.get(categoryName);
        if (categoryId != null) {
            return categoryId;
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into meta_categories(name, status, created_at, updated_at) values(?,?,now(),now())");
            stmt.setString(1, categoryName);
            stmt.setInt(2, Status.ACTIVE.getStatus());
            Long id = executeWithLastId(stmt);
            if (id > 0L) {
                categoryIds.put(categoryName, id);
                return id;
            }
            throw new IllegalStateException("unable to link label metadata category id for [" + categoryName + "]");
        } finally {
            close(stmt);
        }
    }

    /**
     * for short labels, we can use a db table with varchar (vs. text)!
     */
    public static void insertAnnotation(AiImageLabelRequest request, List<AiImageLabel> labels) throws SQLException, NamingException {
        Connection con = null;
        try {
            con = connectX();
            insertAnnotation(con, request, labels);
        } finally {
            close(con);
        }
    }

    public static List<AiImageLabel> insertAnnotation(Connection con, AiImageLabelRequest request, List<AiImageLabel> imageLabels) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_image_label_annotations(ai_label_request_id, ai_image_id, model_name, label, mid, score, topicality, status, created_at, updated_at) values(?,?,?,?,?,?,?,?,now(),now())");
            stmt.setLong(1, request.getId());
            stmt.setInt(8, Status.NEW.getStatus());

            for (AiImageLabel label : imageLabels) {
                stmt.setLong(2, label.getImage().getId());
                stmt.setString(3, label.getModelName());
                stmt.setString(4, label.getContent());
                stmt.setString(5, label.getMid());
                stmt.setDouble(6, label.getScore());
                stmt.setDouble(7, label.getTopicality());

                Long id = executeWithLastId(stmt);
                if (id > 0L) {
                    label.setId(id);
                }
            }
        } finally {
            close(stmt);
        }

        return imageLabels;
    }

    public static void main(String[] args) throws SQLException {
        // test insert and find
        Connection con = connect();
        String requestUUID = "2d50af10-2ca2-4ee7-8d0c-b12b6d6b7677";
//        NoiRequest request = DbRequest.find(con, requestUUID);
        Long imageId = 3L;
//        AiImage image = DbImage.find(con, imageId, request);
        String modelName = AiModel.DEFAULT_VISION_MODEL.getName();
        String content = "The image shows a person outdoors holding a cute puppy. The person is wearing a beanie, a puffy jacket with what appears to be a fleece collar, and has a backpack. They appear to be on a trail with a scenic mountainous backdrop, indicative of a wilderness area or hiking trail. The puppy has fluffy fur and seems to be quite young. The setting is picturesque, with mountains in the distance and a partly cloudy sky. This looks like a moment captured during a hike or outdoor adventure.";
//        AiImageLabel label = AiImageLabel.create(image, modelName, content, null);
        List<AiImageLabel> labels = new ArrayList<>();
//        labels.add(label);

//        insert(con, request, labels);

//        Map<AiImage, AiImageLabel> map = find(con, requestUUID);
//        for (Map.Entry<AiImage, AiImageLabel> entry : map.entrySet()) {
//            System.out.println(entry.getKey() + "=" + entry.getValue());
//        }
    }

    public static Map<String, Long> findAllCategories() throws SQLException, NamingException {
        Connection con = null;
        try {
            con = connectX();
            return findAllCategories(con);
        } finally {
            close(con);
        }
    }

    /**
     * return a map of category name to its id.
     * @param con
     * @return
     * @throws SQLException
     */
    public static Map<String, Long> findAllCategories(Connection con) throws SQLException {
        Map<String, Long> categories = new HashMap<>();

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select id, name from meta_categories");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                categories.put(rs.getString("name"), rs.getLong("id"));
            }
        } finally {
            close(stmt);
        }

        return categories;
    }

    public static Map<Long, Map<String, List<MetaKeyValues>>> findImageCategoryKeyGroups(Connection con, Long videoId) throws SQLException {
        Map<Long, Map<String, List<MetaKeyValues>>> kv = new HashMap<>();

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select ai_image_id, name category_name, meta_key, group_concat(meta_value) meta_values from (select distinct lmc.ai_image_id, i.video_frame_number, c.name, lmc.meta_key, lmc.meta_value from ai_image_label_meta_categories lmc join meta_categories c on c.id = lmc.meta_category_id join ai_images i on i.id = lmc.ai_image_id where i.ai_video_id=?)x group by 1,2,3 order by video_frame_number, name, meta_key");
            stmt.setLong(1, videoId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                long imageId = rs.getLong("ai_image_id");
                Map<String, List<MetaKeyValues>> categoryMap = kv.get(imageId);
                if (categoryMap == null) {
                    categoryMap = new HashMap<>();
                    kv.put(imageId, categoryMap);
                }
                String categoryName = rs.getString("category_name");
                List<MetaKeyValues> kvList = categoryMap.get(categoryName);
                if (kvList == null) {
                    kvList = new ArrayList<>();
                    categoryMap.put(categoryName, kvList);
                }

                kvList.add(MetaKeyValues.create(rs));
            }
        } finally {
            close(stmt);
        }

        return kv;
    }

    public static Map<String, List<MetaKeyImages>> findCategoryImages(Connection con, Long videoId) throws SQLException {
        Map<String, List<MetaKeyImages>> images = new HashMap<>();
        // select category_name, meta_key, meta_value, count(distinct ai_image_id) image_count, group_concat(distinct ai_image_id) image_ids from (select ai_image_id, name category_name, meta_key, meta_value, count(*) _count from (select distinct lmc.ai_image_id, i.video_frame_number, c.name, lmc.meta_key, lmc.meta_value from ai_image_label_meta_categories lmc join meta_categories c on c.id = lmc.meta_category_id join ai_images i on i.id = lmc.ai_image_id where i.ai_video_id=@video_id and name=@category)a group by 1,2,3,4)x group by 1,2,3 having count(distinct ai_image_id) > 1 order by count(distinct ai_image_id), category_name, meta_key, meta_value;
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select category_name, meta_key, meta_value, count(distinct ai_image_id) image_count, group_concat(distinct ai_image_id) image_ids from (select ai_image_id, name category_name, meta_key, meta_value, count(*) _count from (select distinct lmc.ai_image_id, i.video_frame_number, c.name, lmc.meta_key, lmc.meta_value from ai_image_label_meta_categories lmc join meta_categories c on c.id = lmc.meta_category_id join ai_images i on i.id = lmc.ai_image_id where i.ai_video_id=?)a group by 1,2,3,4)x group by 1,2,3 having count(distinct ai_image_id) > 0 order by count(distinct ai_image_id) desc, category_name, meta_key, meta_value");
            stmt.setLong(1, videoId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String categoryName = rs.getString("category_name");
                List<MetaKeyImages> catImages = images.get(categoryName);
                if (catImages == null) {
                    catImages = new ArrayList<>();
                    images.put(categoryName, catImages);
                }

                catImages.add(MetaKeyImages.create(rs));
            }
        } finally {
            close(stmt);
        }

        return images;
    }
}
