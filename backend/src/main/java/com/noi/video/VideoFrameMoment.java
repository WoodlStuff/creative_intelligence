package com.noi.video;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

public class VideoFrameMoment {
    private final Long imageId;
    private final String imageURL;

    private final int frameNumber, secondsFromStart;

    private final double frameRate;

    private VideoFrameMoment(Long imageId, String imageURL, int frameNumber, int secondsFromStart, double frameRate) {
        this.imageId = imageId;
        this.imageURL = imageURL;
        this.frameNumber = frameNumber;
        this.secondsFromStart = secondsFromStart;
        this.frameRate = frameRate;
    }

    public static VideoFrameMoment create(ResultSet rs) throws SQLException {
        return new VideoFrameMoment(rs.getLong("ai_image_id"), rs.getString("image_url"), rs.getInt("video_frame_number"), rs.getInt("_seconds_in"), rs.getDouble("frame_rate"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VideoFrameMoment that = (VideoFrameMoment) o;
        return imageId.equals(that.imageId);
    }

    @Override
    public String toString() {
        return "VideoFrameMoment{" +
                "imageId=" + imageId +
                ", imagerURL='" + imageURL + '\'' +
                ", frameNumber=" + frameNumber +
                ", secondsFromStart=" + secondsFromStart +
                ", frameRate=" + frameRate +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(imageId);
    }

    public String getImageURL() {
        return imageURL;
    }

    public int getFrameNumber() {
        return frameNumber;
    }

    public int getSecondsFromStart() {
        return secondsFromStart;
    }

    public double getVideoFrameRate() {
        return frameRate;
    }

    public Long getImageId() {
        return imageId;
    }
}
