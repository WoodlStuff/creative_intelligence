package com.noi.models;

import com.noi.AiBrand;
import com.noi.Status;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DbBrand extends Model {
    private static String COLUMNS = "id, name, brand_type_id, status";

    public static AiBrand find(Connection con, Long brandId) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select " + COLUMNS + " from ai_brands where id=?");
            stmt.setLong(1, brandId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return assembleBrand(con, rs);
            }
        } finally {
            close(stmt);
        }
        return null;
    }

    public static AiBrand find(Connection con, String name) throws SQLException {
        if (name == null || name.isEmpty()) {
            return null;
        }

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select " + COLUMNS + " from ai_brands where name=?");
            stmt.setString(1, name);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return assembleBrand(con, rs);
            }
        } finally {
            close(stmt);
        }
        return null;
    }

    private static AiBrand assembleBrand(Connection con, ResultSet rs) throws SQLException {
        AiBrand.Type brandType = findType(con, rs.getLong("brand_type_id"));
        return AiBrand.create(rs, brandType);
    }

    public static List<AiBrand> findAll(Connection con, Status status) throws SQLException {
        List<AiBrand> brands = new ArrayList();

        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select " + COLUMNS + " from ai_brands where status=?");
            stmt.setInt(1, status.getStatus());
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                brands.add(assembleBrand(con, rs));
            }
        } finally {
            close(stmt);
        }

        return brands;
    }

    private static AiBrand.Type findType(Connection con, long typeId) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("select id, name, status from ai_brand_types where id=?");
            stmt.setLong(1, typeId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return AiBrand.createType(rs);
            }
        } finally {
            close(stmt);
        }
        return null;
    }

    public static AiBrand insert(Connection con, String brandName) throws SQLException {
        PreparedStatement stmt = null;
        try {
            stmt = con.prepareStatement("insert into ai_brands(name, status, created_at, updated_at) values(?, ?, now(), now())");
            stmt.setString(1, brandName);
            stmt.setInt(2, Status.NEW.getStatus());
            Long id = executeWithLastId(stmt);
            if(id > 0L){
                return AiBrand.create(id, brandName, Status.NEW);
            }

        } finally {
            close(stmt);
        }

        return null;
    }
}
