package com.noi.models;

import com.noi.Status;
import com.noi.video.AiVideo;

import javax.naming.NamingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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
}
