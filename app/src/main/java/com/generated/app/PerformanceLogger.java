package com.generated.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class PerformanceLogger {

    public static class Record {
        public final String name;
        public final long durationMs;
        public final long timestamp;

        public Record(String name, long durationMs) {
            this.name = name;
            this.durationMs = durationMs;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private static final List<Record> records = new ArrayList<>();

    public static synchronized void record(String name, long durationMs) {
        if (durationMs > 100) {
            records.add(new Record(name, durationMs));
            if (records.size() > 500) {
                records.remove(0);
            }
        }
    }

    public static synchronized List<Record> getTop20Slowest() {
        List<Record> copy = new ArrayList<>(records);
        Collections.sort(copy, new Comparator<Record>() {
            @Override
            public int compare(Record o1, Record o2) {
                return Long.compare(o2.durationMs, o1.durationMs);
            }
        });
        if (copy.size() > 20) {
            return copy.subList(0, 20);
        }
        return copy;
    }

    public static synchronized void clear() {
        records.clear();
    }
}
