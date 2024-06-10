package com.noi.web;

import javax.servlet.http.HttpServletRequest;
import java.util.Objects;

public class Path {
    private final boolean testing;
    String contextPath, servletPath, pathInfo, query;

    private Path() {
        this("/", null, null, null, false);
    }

    private Path(String contextPath, String servletPath, String pathInfo, String query, boolean testing) {
        this.contextPath = contextPath; // /musesick
        this.servletPath = servletPath; // context plus path (/musesick/meta)
        this.pathInfo = pathInfo; // the token (1); everything after servletPath
        this.query = query;
        this.testing = testing;
    }

    public static Path parse(HttpServletRequest req) {
        return parse(req.getContextPath(), req.getServletPath(), req.getPathInfo(), req.getQueryString(), req.getParameter("test"));
    }

    public static Path parse(String contextPath, String servletPath, String pathInfo, String queryString) {
        return parse(contextPath, servletPath, pathInfo, queryString, null);
    }

    // path = /context/page
    public static Path parse(String contextPath, String servletPath, String pathInfo, String queryString, String isTest) {
        if (servletPath == null) {
            return new Path();
        }

        String context, path, info = null;

        // context
        int startIndex = 0;
        if (contextPath.startsWith("/")) {
            startIndex = 1;
        }
        context = contextPath.substring(startIndex);

        // path
        startIndex = 0;
        if (servletPath.startsWith("/")) {
            startIndex = 1;
        }
        path = servletPath.substring(startIndex);

        // info
        if (pathInfo != null) {

            startIndex = 0;
            if (pathInfo.startsWith("/")) {
                startIndex = 1;
            }
            info = pathInfo.substring(startIndex);
        }

        boolean testing = isTest != null || (queryString != null && queryString.contains("test="));
        return new Path(context, path, info, queryString, testing);
    }

    public String getServletPath() {
        return servletPath;
    }

    public String getPathInfo() {
        return pathInfo;
    }

    public String getQuery() {
        return query;
    }

    public boolean isTesting() {
        return testing;
    }

    @Override
    public String toString() {
        return "Path{" +
                "contextPath='" + contextPath + '\'' +
                ", servletPath='" + servletPath + '\'' +
                ", pathInfo='" + pathInfo + '\'' +
                ", query='" + query + '\'' +
                '}';
    }

    public String toURL(Scope scope) {
        StringBuilder uri = new StringBuilder();

        if (scope == Scope.CONTEXT) {
            return uri.append("/").append(contextPath).toString();
        }

        if (scope == Scope.SERVLET || scope == Scope.INFO || scope == Scope.QUERY) {
            uri.append("/").append(servletPath);
        }

        if (scope == Scope.INFO && pathInfo != null) {
            uri.append("/").append(pathInfo);
        }

        if (scope == Scope.QUERY && query != null && !"".equals(query)) {
            uri.append("?").append(query);
        }

        return uri.toString();
    }

    public String getContextPath() {
        return contextPath;
    }

    public static class Scope {
        private Scope(String level) {
            this.level = level;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Scope context = (Scope) o;
            return level.equals(context.level);
        }

        @Override
        public int hashCode() {
            return Objects.hash(level);
        }

        @Override
        public String toString() {
            return "Scope{" +
                    "level=" + level +
                    '}';
        }

        private final String level;

        public static final Scope CONTEXT = new Scope("context");
        public static final Scope SERVLET = new Scope("servlet");
        public static final Scope INFO = new Scope("info");
        public static final Scope QUERY = new Scope("query");
    }
}