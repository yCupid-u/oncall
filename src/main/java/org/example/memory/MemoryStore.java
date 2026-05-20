package org.example.memory;

import java.util.List;

public interface MemoryStore {

    List<MemoryRecord> findAll();

    MemoryRecord upsert(MemoryRecord record);

    List<MemoryRecord> search(MemorySearchRequest request);

    void clear();
}
