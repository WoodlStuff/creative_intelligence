package com.noi.models;

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
    public static List<AiVideo> findMostRecent(Connection con, int limit) throws SQLException {
        List<AiVideo> videos = new ArrayList<>();
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select id, video_url, frame_rate, frame_count, status from ai_videos where status !=? order by updated_at desc limit ?");
            stmt.setInt(1, Status.DELETED.getStatus());
            stmt.setInt(2, limit);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                videos.add(AiVideo.create(rs));
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
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select id, video_url, frame_rate, frame_count, status from ai_videos where id=?");
            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return AiVideo.create(rs);
            }
        } finally {
            close(stmt);
        }

        return null;
    }

    public static void update(Connection con, Long videoId, int frames, double fps) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("update ai_videos set frame_count=?, frame_rate=?, updated_at=now() where id=?");
            stmt.setInt(1, frames);
            stmt.setDouble(2, fps);
            stmt.setLong(3, videoId);
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
}
