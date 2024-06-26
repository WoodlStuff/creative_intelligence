package com.noi.image;

import com.noi.AiModel;
import com.noi.Status;
import com.noi.image.label.LabelService;
import com.noi.language.AiImageLabelRequest;
import com.noi.language.AiPrompt;
import com.noi.models.DbImage;
import com.noi.models.DbLanguage;
import com.noi.models.DbRequest;
import com.noi.models.Model;
import com.noi.requests.ImageLabelResponse;
import com.noi.tools.FileTools;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

public class AiImage {
    private Long id, videoId; // the db id for this image
    private final String url, revisedPrompt;

    private Status status;
    private Integer videoFrameNumber;
    private boolean newVideoScene, videoSceneSnap;

    private final AiPrompt prompt;

    private AiImage(Long id, AiImageRequest request, String url, String revisedPrompt) {
        this(id, request != null ? request.getPrompt() : null, url, revisedPrompt, null, null, false, false, Status.ACTIVE);
    }

    private AiImage(Long id, AiPrompt prompt, String url, String revisedPrompt, Long videoId, Integer videoFrameNumber, boolean isNewVideoScene, boolean isVideoSceneSnap, Status status) {
        this.id = id;
        this.prompt = prompt;
        this.url = url;
        this.revisedPrompt = revisedPrompt;
        this.videoId = videoId;
        this.videoFrameNumber = videoFrameNumber;
        this.newVideoScene = isNewVideoScene;
        this.videoSceneSnap = isVideoSceneSnap;
        this.status = status;
    }

    public static AiImage create(Long id, AiPrompt prompt, String url, String revisedPrompt) {
        return new AiImage(id, prompt, url, revisedPrompt, null, null, false, false, Status.ACTIVE);
    }

    public static AiImage create(Long id, Long videId, int frame, String url, Status status) {
        return new AiImage(id, null, url, null, videId, frame, false, false, status);
    }

    public static AiImage create(AiImageRequest request, String url, String revisedPrompt) {
        return new AiImage(null, request, url, revisedPrompt);
    }

    public static AiImage create(ResultSet rs, AiPrompt prompt) throws SQLException {
        Long id = rs.getLong("id");
        String url = rs.getString("image_url");
        String revisedPrompt = rs.getString("revised_prompt");
        Status status = Status.parse(rs.getInt("status"));

        Long videoId = rs.getLong("ai_video_id");
        if (videoId != null && videoId <= 0L) {
            videoId = null;
        }

        Integer videoFrameNumber = rs.getInt("video_frame_number");
        if (videoFrameNumber != null && videoFrameNumber <= 0) {
            videoFrameNumber = null;
        }

        boolean isNewVideoScene = rs.getBoolean("is_new_video_scene");
        boolean isVideoSceneSnap = rs.getBoolean("is_video_scene_snap");

        return new AiImage(id, prompt, url, revisedPrompt, videoId, videoFrameNumber, isNewVideoScene, isVideoSceneSnap, status);
    }

    public String getUrl() {
        return url;
    }

    public String getRevisedPrompt() {
        return revisedPrompt;
    }

    @Override
    public String toString() {
        return "AiImage{" +
                "url='" + url + '\'' +
                ", prompt='" + prompt + '\'' +
                ", revisedPrompt='" + revisedPrompt + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AiImage aiImage = (AiImage) o;
        return url.equals(aiImage.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        if (this.id != null) {
            throw new IllegalStateException();
        }
        this.id = id;
    }

    public Long getVideoId() {
        return videoId;
    }

    public Status getStatus() {
        return status;
    }

    public Integer getVideoFrameNumber() {
        return videoFrameNumber;
    }

    public boolean isNewVideoScene() {
        return newVideoScene;
    }

    public boolean isVideoSceneSnap() {
        return videoSceneSnap;
    }

    public AiPrompt getPrompt() {
        return prompt;
    }

    private static ImageLabelResponse requestImageLabels(Connection con, AiImage image, AiPrompt prompt, String modelName) throws SQLException, IOException {
        AiImageLabelRequest request = AiImageLabelRequest.create(image.getId(), prompt, modelName);

        request = DbRequest.insertForLabel(con, request);

        LabelService labelService = LabelService.getService(request);
        if (labelService != null) {
            ImageLabelResponse response = labelService.labelize(con, request);
            DbRequest.finishedForLabel(con, request);
            return response;
        }
        return null;
    }

    public static void main(String[] args) throws IOException, SQLException {
        // read csv and populate ai_images from image url column in csv
//        if (args.length < 2) {
//            throw new IllegalArgumentException();
//        }

        Long promptId = 1L;
        String fileUrl = "/Users/martin/Downloads/nativepayload.results.csv"; // args[0];
        String urlColumn = "payload.results.snapshot.cards[0].original_image_url"; //args[1];
        int urlColumnIndex = -1;
        String line;
        String[] firstLine = null;

        Connection con = null;

        try {
            con = Model.connect();

            BufferedReader reader = FileTools.getFileReader(fileUrl);
            while ((line = reader.readLine()) != null) {
                try {
                    String[] columns = line.split(",");
                    if (firstLine == null) {
                        firstLine = columns;
                        int index = -1;
                        for (String column : firstLine) {
                            index++;
                            if (column.replace("\"", "").trim().equalsIgnoreCase(urlColumn)) {
                                urlColumnIndex = ++index;
                                break;
                            }
                        }
                        continue;
                    }

                    if (urlColumnIndex >= 0 && columns.length > urlColumnIndex) {
                        String url = columns[urlColumnIndex];
                        if (url != null && !url.isEmpty()) {
                            String u = url.replace("\"", "").trim();
                            if (!u.isEmpty()) {
                                DbImage.insert(con, u);
                            }
                        }
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            reader.close();

            // now that we have new images, request labels for each of them
//            String labelServiceName = LabelService.getServiceName(null);
//            if (args.length >= 3) {
//                labelServiceName = args[2];
//            }
            //String modelName = LabelService.getModelName(labelServiceName, AiModel.DEFAULT_VISION_MODEL.getName());
            String modelName = AiModel.DEFAULT_VISION_MODEL.getName();
            if (args.length >= 3) {
                modelName = args[2];
            }

            AiPrompt prompt = DbLanguage.findPrompt(con, promptId);
            if (prompt == null) {
                throw new IllegalArgumentException("no prompt found for " + promptId);
            }

            List<AiImage> newImages = DbImage.findImages(con, Status.NEW);
            for (AiImage image : newImages) {
                ImageLabelResponse imageLabelResponse = requestImageLabels(con, image, prompt, modelName);
                DbImage.updateStatus(con, image, Status.ACTIVE);
            }

        } finally {
            Model.close(con);
        }
    }

    public boolean isLocal() {
        return url != null && url.startsWith("/");
    }

    public String getFilePath() {
        if (!isLocal()) {
            return null;
        }
        return url;
    }
}
