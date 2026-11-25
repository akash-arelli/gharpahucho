package com.gharpahucho.integration.transport;

import com.gharpahucho.entity.DataSource;

import java.util.List;

/**
 * Adapter boundary for transport data. The recommendation engine only ever sees {@link Candidate}s, so a real,
 * authorised bus or train API can be added later as one more implementation of this interface, with no change to the
 * business logic. Providers must never present simulated inventory as live availability.
 */
public interface TransportProvider {

    /** Stable id used in logs and metrics. */
    String id();

    DataSource dataSource();

    List<Candidate> search(SearchContext context);
}
