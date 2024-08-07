package com.noi.video;

import com.noi.AiBrand;
import com.noi.Status;
import com.noi.image.AiImage;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

public class AiVideo {
    private final Long id;
    private final String name, url;
    private final double frameRate;
    private final int frameCount;
    private final Status status;
    private final AiBrand brand;

    private AiVideo(Long id, String name, String url, double frameRate, int frameCount, Status status, AiBrand brand) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.frameRate = frameRate;
        this.frameCount = frameCount;
        this.status = status;
        this.brand = brand;
    }

    public static AiVideo create(String name, String url, Status status, AiBrand brand) throws SQLException {
        return new AiVideo(null, name, url, 0.0, 0, status, brand);
    }

    public static AiVideo create(ResultSet rs, AiBrand brand) throws SQLException {
        // id, video_url, frame_rate, frame_count, status
        Long id = rs.getLong("id");
        String name = rs.getString("name");
        String url = rs.getString("video_url");
        double frameRate = rs.getDouble("frame_rate");
        int frameCount = rs.getInt("frame_count");
        Status status = Status.parse(rs.getInt("status"));

        return new AiVideo(id, name, url, frameRate, frameCount, status, brand);
    }

    public Long getId() {
        return id;
    }

    public String getUrl() {
        return url;
    }

    public double getFrameRate() {
        return frameRate;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public Status getStatus() {
        return status;
    }

    public AiBrand getBrand() {
        return brand;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "AiVideo{" +
                "id=" + id +
                ", url='" + url + '\'' +
                ", brand='" + brand + '\'' +
                ", frameRate=" + frameRate +
                ", frameCount=" + frameCount +
                ", status=" + status +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AiVideo aiVideo = (AiVideo) o;
        return id.equals(aiVideo.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    public String getFilePath() {
        return url;
    }

    public long getSeconds() {
        return Math.round(frameCount / frameRate);
    }

    public boolean isLocal() {
        return url != null && url.startsWith("/");
    }

    public static class SceneChange {
        private final AiImage lastImage, firstImage;
        private final int lastFrame, firstFrame;
        private final double score;
        private final String explanation;
        private final boolean isNewScene;

        private SceneChange(AiImage lastImage, AiImage firstImage, int lastFrame, int firstFrame, double score, String explanation, boolean isNewScene) {
            this.lastImage = lastImage;
            this.lastFrame = lastFrame;
            this.firstImage = firstImage;
            this.firstFrame = firstFrame;

            this.score = score;
            this.explanation = explanation;
            this.isNewScene = isNewScene;
        }

        public static SceneChange create(AiImage lastImage, AiImage firstImage, int lastFrame, int firstFrame, double score, String explanation, boolean isNewScene) {
            return new SceneChange(lastImage, firstImage, lastFrame, firstFrame, score, explanation, isNewScene);
        }

        @Override
        public String toString() {
            return "SceneChange{" +
//                    "lastImage=" + lastImage +
//                    ", firstImage=" + firstImage +
                    "lastFrame=" + lastImage.getVideoFrameNumber() +
                    ", firstFrame=" + firstImage.getVideoFrameNumber() +
                    ", score=" + score +
                    ", explanation='" + explanation + '\'' +
                    ", isNewScene=" + isNewScene +
                    '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SceneChange that = (SceneChange) o;
            return lastFrame == that.lastFrame && firstFrame == that.firstFrame;
        }

        @Override
        public int hashCode() {
            return Objects.hash(lastFrame, firstFrame);
        }

        public AiImage getLastImage() {
            return lastImage;
        }

        public AiImage getFirstImage() {
            return firstImage;
        }

        public int getLastFrame() {
            return lastFrame;
        }

        public int getFirstFrame() {
            return firstFrame;
        }

        public double getScore() {
            return score;
        }

        public String getExplanation() {
            return explanation;
        }

        public boolean isNewScene() {
            return isNewScene;
        }
    }
}
