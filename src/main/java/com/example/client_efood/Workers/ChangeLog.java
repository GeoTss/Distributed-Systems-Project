package com.example.client_efood.Workers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class ChangeLog {

    private final TreeMap<String, DataChange> changes = new TreeMap<>();

    public ArrayList<DataChange> getChanges() {
        return new ArrayList<>(changes.values());
    }

    public boolean contains(DataChange change) {
        return changes.containsKey(change.getChange_id());
    }

    public void addChange(DataChange dt_change) {
        changes.put(dt_change.getChange_id(), dt_change);
    }

    public List<DataChange> changesAfterTimestamp(long timestamp) {

        List<DataChange> result = new ArrayList<>();
        for (DataChange change : changes.values()) {
            if (change.getTimestamp() > timestamp)
                result.add(change);
        }
        return result;
    }

    @Override
    public String toString(){
        return getChanges().toString();
    }
}
