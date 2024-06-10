package com.noi.tools;

import javax.servlet.http.HttpServletRequest;

public class SystemEnv {
    /**
     * request trumps system trumps default
     *
     * @param propertyName
     * @param req
     * @param defaultValue
     * @return
     */
    public static String get(String propertyName, HttpServletRequest req, String defaultValue) {
        // if the request has a property with this name, it wins, otherwise, see if there is a system property, else default
        String value = req.getParameter(propertyName);
        if (value != null) {
            return value;
        }
        return get(propertyName, defaultValue);
    }

    public static String get(String propertyName, String defaultValue) {
        String value = System.getenv(propertyName);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }
}
