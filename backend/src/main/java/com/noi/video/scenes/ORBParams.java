package com.noi.video.scenes;

import java.sql.ResultSet;
import java.sql.SQLException;

public class ORBParams {
    private final int maxDistance;
    private final double threshold;

    private final int count;

    private ORBParams(int maxDistance, double threshold, int count) {
        this.maxDistance = maxDistance;
        this.threshold = threshold;
        this.count = count;
    }


    public static ORBParams create(ResultSet rs) throws SQLException {
        return new ORBParams(rs.getInt("max_distance"), rs.getDouble("score_threshold"), rs.getInt("_count"));
    }

    @Override
    public String toString() {
        return "ORBParams{" +
                "maxDistance=" + maxDistance +
                ", threshold=" + threshold +
                ", count=" + count +
                '}';
    }

    public int getMaxDistance() {
        return maxDistance;
    }

    public double getThreshold() {
        return threshold;
    }

    public int getCount() {
        return count;
    }
}
