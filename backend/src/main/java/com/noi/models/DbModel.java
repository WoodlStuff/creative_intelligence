package com.noi.models;


import com.noi.AiModel;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DbModel extends Model {
    public static AiModel ensure(Connection con, String name) throws SQLException {
        AiModel model = find(con, name);
        if (model == null) {
            model = insert(con, name);
        }

        return model;
    }


    public static List<AiModel> findAll(Connection con) throws SQLException {
        List<AiModel> models = new ArrayList<>();
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select id, name from ai_models where status >= 0");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                models.add(AiModel.create(rs));
            }
        } finally {
            close(stmt);
        }

        return models;
    }

    public static AiModel find(Connection con, String name) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select id, name from ai_models where name=?");
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return AiModel.create(rs);
            }
        } finally {
            close(stmt);
        }

        return null;
    }

    public static AiModel find(Connection con, Long id) throws SQLException {
        if (id == null) {
            return null;
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select id, name from ai_models where id=?");
            stmt.setLong(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return AiModel.create(rs);
            }
        } finally {
            close(stmt);
        }

        return null;
    }

    public static AiModel insert(Connection con, String name) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_models (name, created_at, updated_at) values(?,now(),now())");
            stmt.setString(1, name);
            if (stmt.executeUpdate() > 0L) {
                return find(con, name);
            }
        } finally {
            close(stmt);
        }
        return null;
    }
}
