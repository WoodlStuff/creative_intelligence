package com.noi.models;

import com.noi.AiModel;
import com.noi.Status;
import com.noi.image.AiImage;
import com.noi.language.AiPrompt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DbMedia extends Model {

    public static Long ensureSound(Connection con, Long videoId, String soundURL) throws SQLException {
        Long soundId = findSound(con, videoId, soundURL);
        if (soundId == null) {
            soundId = insertSound(con, videoId, soundURL);
        }
        return soundId;
    }

    public static Long findSound(Connection con, Long videoId, String soundURL) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select id from ai_sounds where sound_url=? and ai_video_id=?");
            stmt.setString(1, soundURL);
            stmt.setLong(2, videoId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getLong("id");
            }
        } finally {
            close(stmt);
        }
        return null;
    }

    public static Long insertSound(Connection con, Long videoId, String soundURL) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_sounds(sound_url, status, ai_video_id, created_at, updated_at) values(?, ?, ?, now(), now())");
            stmt.setString(1, soundURL);
            stmt.setInt(2, Status.ACTIVE.getStatus());
            stmt.setLong(3, videoId);
            return executeWithLastId(stmt);
        } finally {
            close(stmt);
        }
    }

    public static String findMostRecentSoundTranscript(Connection con, Long videoId) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select t.transcription_text from ai_transcriptions t join ai_transcribe_requests r on r.id = t.ai_transcribe_request_id join ai_sounds s on s.id = r.ai_sound_id  where r.status = t.status and r.status=? and s.ai_video_id=? order by r.updated_at desc limit 1");
            stmt.setInt(1, Status.ACTIVE.getStatus());
            stmt.setLong(2, videoId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("transcription_text");
            }
        } finally {
            close(stmt);
        }

        return null;
    }

    public static String findMostRecentSoundSummary(Connection con, Long videoId) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select ss.summary_text from ai_sound_summaries ss join ai_sound_summary_requests r on r.id = ss.ai_sound_summary_request_id join ai_transcriptions t on t.id = r.ai_transcription_id join ai_sounds s on s.id = ss.ai_sound_id  where r.status = t.status and r.status=? and s.ai_video_id=? order by r.updated_at desc limit 1");
            stmt.setInt(1, Status.ACTIVE.getStatus());
            stmt.setLong(2, videoId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("summary_text");
            }
        } finally {
            close(stmt);
        }

        return null;
    }

    public static Long insertTranscriptRequest(Connection con, Long soundId, String uuid, AiModel model) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert ignore into ai_transcribe_requests(uuid, ai_sound_id, ai_model_id, status, created_at, updated_at) values(?, ?, ?, ?, now(), now()) ON DUPLICATE KEY UPDATE updated_at=now(), id=LAST_INSERT_ID(id)");
            stmt.setString(1, uuid);
            stmt.setLong(2, soundId);
            stmt.setLong(3, model.getId());
            stmt.setInt(4, Status.NEW.getStatus());
            return executeWithLastId(stmt);
        } finally {
            close(stmt);
        }
    }

    public static void updateTranscriptRequest(Connection con, Long requestId, Status status) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("update ai_transcribe_requests set status=?, updated_at=now() where id=?");
            stmt.setInt(1, status.getStatus());
            stmt.setLong(2, requestId);
            stmt.executeUpdate();
        } finally {
            close(stmt);
        }
    }

    public static Long insertTranscript(Connection con, Long soundId, Long requestId, String transcript) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert ignore into ai_transcriptions(ai_sound_id, ai_transcribe_request_id, transcription_text, status, created_at, updated_at) values(?, ?, ?, ?, now(), now()) ON DUPLICATE KEY UPDATE updated_at=now(), id=LAST_INSERT_ID(id)");
            stmt.setLong(1, soundId);
            stmt.setLong(2, requestId);
            stmt.setString(3, transcript);
            stmt.setInt(4, Status.ACTIVE.getStatus());
            return executeWithLastId(stmt);
        } finally {
            close(stmt);
        }
    }

    public static Long insertTranscriptSummaryRequest(Connection con, String uuid, Long transcriptId, AiPrompt prompt) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert ignore into ai_sound_summary_requests(uuid, ai_transcription_id, ai_model_id, ai_prompt_id, status, created_at, updated_at) values(?, ?, ?, ?, ?, now(), now()) ON DUPLICATE KEY UPDATE updated_at=now(), id=LAST_INSERT_ID(id)");
            stmt.setString(1, uuid);
            stmt.setLong(2, transcriptId);
            stmt.setLong(3, prompt.getModel().getId());
            stmt.setLong(4, prompt.getId());
            stmt.setInt(5, Status.NEW.getStatus());
            return executeWithLastId(stmt);
        } finally {
            close(stmt);
        }
    }

    public static void updateTranscriptSummaryRequest(Connection con, Long requestId, Status status) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("update ai_sound_summary_requests set status=?, updated_at=now() where id=?");
            stmt.setInt(1, status.getStatus());
            stmt.setLong(2, requestId);
            stmt.executeUpdate();
        } finally {
            close(stmt);
        }
    }

    public static void insertTranscriptSummary(Connection con, Long soundId, Long requestId, String audioSummary) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert ignore into ai_sound_summaries(ai_sound_id, ai_sound_summary_request_id, summary_text, status, created_at, updated_at) values(?, ?, ?, ?, now(), now()) ON DUPLICATE KEY UPDATE updated_at=now(), summary_text=?");
            stmt.setLong(1, soundId);
            stmt.setLong(2, requestId);
            stmt.setString(3, audioSummary);
            stmt.setInt(4, Status.ACTIVE.getStatus());
            stmt.setString(5, audioSummary);
            stmt.executeUpdate();
        } finally {
            close(stmt);
        }
    }

    public static Long insertVideoSummaryRequest(Connection con, Long videoId, String uuid, AiPrompt prompt) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert ignore into ai_video_summary_requests(uuid, ai_video_id, ai_model_id, ai_prompt_id, status, created_at, updated_at) values(?, ?, ?, ?, ?, now(), now()) ON DUPLICATE KEY UPDATE updated_at=now(), id=LAST_INSERT_ID(id)");
            stmt.setString(1, uuid);
            stmt.setLong(2, videoId);
            stmt.setLong(3, prompt.getModel().getId());
            stmt.setLong(4, prompt.getId());
            stmt.setInt(5, Status.NEW.getStatus());
            return executeWithLastId(stmt);
        } finally {
            close(stmt);
        }
    }

    public static void updateVideoSummaryRequest(Connection con, Long requestId, Status status) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("update ai_video_summary_requests set status=?, updated_at=now() where id=?");
            stmt.setInt(1, status.getStatus());
            stmt.setLong(2, requestId);
            stmt.executeUpdate();
        } finally {
            close(stmt);
        }
    }

    public static void insertVideoSummaryScene(Connection con, Long videoId, Long requestId, AiImage image) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert ignore into ai_video_summary_scenes(ai_video_summary_request_id, ai_video_id, ai_image_id, status, created_at, updated_at) values(?, ?, ?, ?, now(), now()) ON DUPLICATE KEY UPDATE updated_at=now()");
            stmt.setLong(1, requestId);
            stmt.setLong(2, videoId);
            stmt.setLong(3, image.getId());
            stmt.setInt(4, Status.ACTIVE.getStatus());
            stmt.executeUpdate();
        } finally {
            close(stmt);
        }
    }

    public static void insertVideoSummary(Connection con, Long videoId, Long requestId, String videoSummary) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert ignore into ai_video_summaries(ai_video_id, ai_video_summary_request_id, summary_text, status, created_at, updated_at) values(?, ?, ?, ?, now(), now()) ON DUPLICATE KEY UPDATE updated_at=now(), summary_text=?");
            stmt.setLong(1, videoId);
            stmt.setLong(2, requestId);
            stmt.setString(3, videoSummary);
            stmt.setInt(4, Status.COMPLETE.getStatus());
            stmt.setString(5, videoSummary);
            stmt.executeUpdate();
        } finally {
            close(stmt);
        }
    }

    public static String findMostRecentVideoSummary(Connection con, Long videoId) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select vs.summary_text from ai_video_summaries vs join ai_video_summary_requests r on r.id = vs.ai_video_summary_request_id and r.ai_video_id = vs.ai_video_id where vs.status=r.status and vs.status=? and vs.ai_video_id=? order by r.updated_at desc limit 1");
            stmt.setInt(1, Status.ACTIVE.getStatus());
            stmt.setLong(2, videoId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("summary_text");
            }
        } finally {
            close(stmt);
        }

        return null;
    }
}
