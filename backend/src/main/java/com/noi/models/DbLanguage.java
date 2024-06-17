package com.noi.models;

import com.noi.Status;
import com.noi.language.*;

import javax.naming.NamingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DbLanguage extends Model {
    private static final String PROMPT_COLUMNS = "id, prompt, prompt_type, system_prompt, status";

    public static void persistClassificationResponse(NLPRequest request, ClassificationResponse classificationResponse, String type) throws SQLException, NamingException {
        Connection con = null;
        try {
            con = connectX();
            persistClassificationResponse(con, request, classificationResponse, type);
        } finally {
            close(con);
        }
    }

    public static void persistClassificationResponse(Connection con, NLPRequest request, ClassificationResponse classificationResponse, String type) throws SQLException {
        // we use the first prompt only (for now): so get the id for that prompt for db references
        Long promptId = request.getPrompt().getId();
        if (promptId == null || promptId <= 0L) {
            throw new IllegalStateException("Prompt[0] has no id!");
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_prompt_classifiers(ai_prompt_id, name, confidence, category_type, status, created_at, updated_at) values(?,?,?,?,?,now(), now())");
            stmt.setLong(1, promptId);
            stmt.setString(4, type);
            stmt.setInt(5, Status.ACTIVE.getStatus());
            for (ModerationCategory category : classificationResponse.getCategories()) {
                stmt.setString(2, category.getName());
                stmt.setDouble(3, category.getConfidence());
                stmt.executeUpdate();
            }
        } finally {
            close(stmt);
        }
    }

    public static void persistEntityResponse(Connection con, NLPRequest request, EntitiesResponse entityResponse) throws SQLException {
        if (request.getPrompt() == null) {
            throw new IllegalArgumentException("No prompt in this request!");
        }

        // we use the first prompt only (for now): so get the id for that prompt for db references
        Long promptId = request.getPrompt().getId();
        if (promptId == null || promptId <= 0L) {
            throw new IllegalStateException("Prompt[0] has no id!");
        }

        PreparedStatement stmt = null, mentionStmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_prompt_entities(ai_prompt_id, name, entity_type, status, created_at, updated_at) values(?,?,?,?,now(),now())");
            stmt.setLong(1, promptId);
            stmt.setInt(4, Status.ACTIVE.getStatus());

            mentionStmt = con.prepareStatement("insert into ai_prompt_mentions(ai_prompt_entity_id, mention_text, mention_offset, mention_type, probability, status, created_at, updated_at) values(?,?,?,?,?,?,now(),now())");
            mentionStmt.setInt(6, Status.ACTIVE.getStatus());

            for (Entity entity : entityResponse.getEntities()) {
                stmt.setString(2, entity.getName());
                stmt.setString(3, entity.getType().getType());
                Long entityId = executeWithLastId(stmt);
                if (entityId > 0L) {
                    for (EntityMention mention : entity.getMentions()) {
                        mentionStmt.setLong(1, entityId);
                        mentionStmt.setString(2, mention.getText().getContent());
                        mentionStmt.setLong(3, mention.getText().getBeginOffset());
                        mentionStmt.setString(4, mention.getType() == null ? "empty" : mention.getType().getType());
                        mentionStmt.setDouble(5, mention.getProbability());
                        mentionStmt.executeUpdate();
                    }
                }
            }

        } finally {
            close(mentionStmt);
            close(stmt);
        }
    }

    public static void persistEntityResponse(NLPRequest request, EntitiesResponse entityResponse) throws SQLException, NamingException {
        Connection con = null;
        try {
            con = connectX();
            persistEntityResponse(con, request, entityResponse);
        } finally {
            close(con);
        }
    }

    public static void persistSentimentResponse(Connection con, NLPRequest request, SentimentResponse sentimentResponse) throws SQLException, NamingException {
        if (request.getPrompt() == null) {
            throw new IllegalArgumentException("No prompts in this request!");
        }

        // we use the first prompt only (for now): so get the id for that prompt for db references
        Long promptId = request.getPrompt().getId();
        if (promptId == null || promptId <= 0L) {
            throw new IllegalStateException("Prompt[0] has no id!");
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_prompt_sentiments (ai_prompt_id, sentiment_text, sentiment_offset, magnitude, score, status, created_at, updated_at) values(?,?,?,?,?,?,now(),now())");
            stmt.setLong(1, promptId);
            stmt.setString(2, null); // text is null if this is for the entire prompt!
            stmt.setInt(3, 0);
            stmt.setDouble(4, sentimentResponse.getDocumentSentiment().getMagnitude());
            stmt.setDouble(5, sentimentResponse.getDocumentSentiment().getScore());
            stmt.setInt(6, Status.NEW.getStatus());
            stmt.executeUpdate();

            // now for the sentence sentiments, if any are linked
            for (SentenceSentiment sentiment : sentimentResponse.getSentenceSentiments()) {
                TextSpan text = sentiment.getText();
                if (text != null && sentiment.getSentiment() != null) {
                    stmt.setString(2, text.getContent());
                    stmt.setLong(3, text.getBeginOffset());
                    stmt.setDouble(4, sentiment.getSentiment().getMagnitude());
                    stmt.setDouble(5, sentiment.getSentiment().getScore());
                    stmt.executeUpdate();
                }
            }
        } finally {
            close(stmt);
        }
    }

    public static void persistSentimentResponse(NLPRequest request, SentimentResponse sentimentResponse) throws SQLException, NamingException {
        Connection con = null;
        try {
            con = connectX();
            persistSentimentResponse(con, request, sentimentResponse);
        } finally {
            close(con);
        }
    }

    public static AiPrompt findPrompt(Long promptId) throws SQLException, NamingException {
        Connection con = null;
        try {
            con = connectX();
            return findPrompt(con, promptId);
        } finally {
            close(con);
        }
    }

    public static AiPrompt findPrompt(Connection con, Long promptId) throws SQLException {
        if (promptId == null || promptId <= 0L) {
            return null;
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select " + PROMPT_COLUMNS + " from ai_prompts where id=?");
            stmt.setLong(1, promptId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return AiPrompt.create(rs);
            }
        } finally {
            close(stmt);
        }
        return null;
    }

    public static AiPrompt ensurePrompt(Connection con, String prompt, String systemPrompt, int promptType) throws SQLException {
        AiPrompt p = findPrompt(con, prompt);
        if (p == null) {
            p = insertPrompt(con, prompt, systemPrompt, promptType);
        }
        return p;
    }

    public static AiPrompt findPrompt(Connection con, String prompt) throws SQLException {
        if (prompt == null || prompt.isEmpty()) {
            return null;
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select " + PROMPT_COLUMNS + " from ai_prompts where prompt=?");
            stmt.setString(1, prompt);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return AiPrompt.create(rs.getLong("id"), prompt, rs.getInt("prompt_type"));
            }
        } finally {
            close(stmt);
        }
        return null;
    }

    public static List<AiPrompt> findPrompts() throws SQLException, NamingException {
        Connection con = null;
        try {
            con = Model.connectX();
            return findPrompts(con);
        } finally {
            Model.close(con);
        }
    }

    /**
     * find all active prompts
     *
     * @param con
     * @return
     * @throws SQLException
     */
    public static List<AiPrompt> findPrompts(Connection con) throws SQLException {
        List<AiPrompt> prompts = new ArrayList<>();

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select " + PROMPT_COLUMNS + " from ai_prompts where status in(?,?)");
            stmt.setInt(1, Status.ACTIVE.getStatus());
            stmt.setInt(2, Status.NEW.getStatus());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                prompts.add(AiPrompt.create(rs));
            }
        } finally {
            close(stmt);
        }
        return prompts;
    }

    public static AiPrompt findPrompt(Connection con, String prompt, int promptType) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select id, prompt, prompt_type from ai_prompts where prompt_type=? and prompt=?");
            stmt.setInt(1, promptType);
            stmt.setString(2, prompt);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return AiPrompt.create(rs.getLong("id"), prompt, promptType);
            }
        } finally {
            close(stmt);
        }
        return null;
    }

    public static AiPrompt insertPrompt(AiPrompt prompt) throws SQLException, NamingException {
        Connection con = null;
        try {
            con = connectX();
            return insertPrompt(con, prompt);

        } finally {
            close(con);
        }
    }

    public static AiPrompt insertPrompt(Connection con, AiPrompt prompt) throws SQLException {
        return insertPrompt(con, prompt.getPrompt(), prompt.getSystemPrompt(), prompt.getPromptType());
    }

    public static AiPrompt insertPrompt(Connection con, String prompt, String systemPrompt, int promptType) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_prompts(prompt, system_prompt, prompt_type, status, created_at, updated_at) values(?,?,?,?, now(), now())");
            stmt.setString(1, prompt);
            stmt.setString(2, systemPrompt);
            stmt.setInt(3, promptType);
            stmt.setInt(4, Status.NEW.getStatus());
            Long id = executeWithLastId(stmt);
            if (id > 0L) {
                return AiPrompt.create(id, prompt, promptType);
            }

        } finally {
            close(stmt);
        }

        return null;
    }
}
