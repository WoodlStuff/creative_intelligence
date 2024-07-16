package com.noi;

import java.util.*;

public class Status implements Comparable<Status> {
    private final int status;
    private final String name;

    private static final Map<Integer, Status> statusMap = new HashMap<>();

    private Status(int status, String name) {
        this.status = status;
        this.name = name;
        statusMap.put(status, this);
    }

    public static final Status RETIRED = new Status(-2, "retired");
    public static final Status DELETED = new Status(-1, "deleted");
    public static final Status NEW = new Status(0, "new");
    public static final Status ACTIVE = new Status(1, "active");

    public static final Status COMPLETE = new Status(11, "complete");

    public static final Status DOWNLOADED = new Status(100, "downloaded");

    public static Status parse(int status) {
        return statusMap.get(status);
    }

    public static List<Status> getAll() {
        return new ArrayList<>(statusMap.values());
    }

    public int getStatus() {
        return status;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Status{" + "status=" + status + ", name='" + name + '\'' + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Status status1 = (Status) o;
        return status == status1.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(status);
    }

    @Override
    public int compareTo(Status o) {
        return this.name.compareTo(o.name);
    }
}
