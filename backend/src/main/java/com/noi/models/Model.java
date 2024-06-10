package com.noi.models;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.sql.*;

public abstract class Model {
    public static final String DB_REF = "/jdbc/noi";

    private static final String url = "jdbc:mysql://localhost:3306/noi?characterEncoding=UTF8&autoReconnect=true";
    private static final String javaxDb = "java:comp/env";

    private static final String user = "noi";
    private static final String pwd = "iBeSmart";

    public static Connection connect() throws SQLException {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            return DriverManager.getConnection(url, user, pwd);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static Connection connectX() throws NamingException, SQLException {
        return connectX(DB_REF);
    }

    public static Connection connectX(String poolName) throws NamingException, SQLException {
        Context ctx = new InitialContext();
        javax.sql.DataSource ds = (javax.sql.DataSource) ctx.lookup(javaxDb + poolName);

        return ds.getConnection();
    }

    public static void close(Connection con) {
        if (con != null) {
            try {
                con.close();
            } catch (SQLException e) {
                // ignore
                e.printStackTrace();
            }
        }
    }

    public static void close(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                // ignore
                e.printStackTrace();
            }
        }
    }

    public static void close(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                // ignore
                e.printStackTrace();
            }
        }
    }

    public static Long executeWithLastId(PreparedStatement stmt) throws SQLException {
        PreparedStatement pstmt = null;
        try {
            if (stmt.executeUpdate() > 0) {
                // read the inserted row to get the new id
                pstmt = stmt.getConnection().prepareStatement("SELECT LAST_INSERT_ID()");
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } finally {
            close(pstmt);
        }
        return -1L;
    }
}
