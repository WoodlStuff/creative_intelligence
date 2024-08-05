package com.noi.models;

import com.noi.AiBrand;
import com.noi.Status;
import com.noi.video.AiVideo;
import com.noi.video.VideoFrameMoment;

import javax.naming.NamingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class DbVideo extends Model {
    private static String COLUMNS = "id, name, video_url, frame_rate, frame_count, status, ai_brand_id";

    public static List<AiVideo> findMostRecent(Connection con, int limit) throws SQLException {
        List<AiVideo> videos = new ArrayList<>();
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select " + COLUMNS + " from ai_videos where status !=? order by updated_at desc limit ?");
            stmt.setInt(1, Status.DELETED.getStatus());
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                videos.add(assembleVideo(con, rs));
            }
        } finally {
            close(stmt);
        }

        return videos;
    }

    public static AiVideo find(Long id) throws SQLException, NamingException {
        Connection con = null;
        try {
            con = Model.connectX();
            return find(con, id);
        } finally {
            Model.close(con);
        }
    }

    public static AiVideo find(Connection con, Long id) throws SQLException {
        if (id == null || id <= 0L) {
            return null;
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select " + COLUMNS + " from ai_videos where id=?");
            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return assembleVideo(con, rs);
            }
        } finally {
            close(stmt);
        }

        return null;
    }

    public static AiVideo find(Connection con, String name) throws SQLException {
        if (name == null || name.isEmpty()) {
            return null;
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select " + COLUMNS + " from ai_videos where name=?");
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return assembleVideo(con, rs);
            }
        } finally {
            close(stmt);
        }

        return null;
    }

    private static AiVideo assembleVideo(Connection con, ResultSet rs) throws SQLException {
        AiBrand brand = null;
        if (rs.getString("ai_brand_id") != null) {
            Long brandId = rs.getLong("ai_brand_id");
            brand = DbBrand.find(con, brandId);
        }
        return AiVideo.create(rs, brand);
    }

    public static AiVideo update(Connection con, Long videoId, int frames, double fps) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("update ai_videos set frame_count=?, frame_rate=?, updated_at=now() where id=?");
            stmt.setInt(1, frames);
            stmt.setDouble(2, fps);
            stmt.setLong(3, videoId);
            stmt.executeUpdate();
            return find(con, videoId);
        } finally {
            close(stmt);
        }
    }

    public static void update(Connection con, AiVideo aiVideo, Status status) throws SQLException {
        if (aiVideo == null || aiVideo.getId() == null) {
            throw new IllegalArgumentException();
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("update ai_videos set status=?, updated_at=now() where id=?");
            stmt.setInt(1, status.getStatus());
            stmt.setLong(2, aiVideo.getId());
            stmt.executeUpdate();
        } finally {
            close(stmt);
        }
    }

    public static List<VideoFrameMoment> findSummaryFrames(Connection con, Long videoId) throws SQLException {
        List<VideoFrameMoment> moments = new LinkedList<>();
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select s.ai_image_id, i.image_url, i.video_frame_number, v.frame_rate, round(i.video_frame_number/v.frame_rate) _seconds_in  from ai_video_summary_scenes s join ai_images i on i.id = s.ai_image_id join ai_videos v on v.id = s.ai_video_id and v.id = i.ai_video_id  where s.status=? and i.status !=? and s.ai_video_id=?  order by i.video_frame_number asc");
            stmt.setInt(1, Status.ACTIVE.getStatus());
            stmt.setInt(2, Status.DELETED.getStatus());
            stmt.setLong(3, videoId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                moments.add(VideoFrameMoment.create(rs));
            }

        } finally {
            Model.close(stmt);
        }
        return moments;
    }

    public static Long insert(Connection con, AiVideo aiVideo) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_videos(name, video_url, frame_rate, frame_count, ai_brand_id, status, created_at, updated_at) values(?,?,?,?,?,?, now(), now())");
            stmt.setString(1, aiVideo.getName());
            stmt.setString(2, aiVideo.getUrl());
            stmt.setDouble(3, aiVideo.getFrameRate());
            stmt.setInt(4, aiVideo.getFrameCount());

            if (aiVideo.getBrand() != null) {
                stmt.setLong(5, aiVideo.getBrand().getId());
            } else {
                stmt.setString(5, null);
            }
            stmt.setInt(6, aiVideo.getStatus().getStatus());

            return executeWithLastId(stmt);

        } finally {
            close(stmt);
        }
    }
}
