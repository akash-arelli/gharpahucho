package com.gharpahucho.integration.maps;

import java.util.List;

/** A city with coordinates, multilingual aliases and well-known localities (coarse areas, never addresses). */
public record City(String name, String state, double lat, double lon, List<String> aliases, List<Locality> localities) {

    public record Locality(String name, double lat, double lon) {
    }

    public City(String name, String state, double lat, double lon) {
        this(name, state, lat, lon, List.of(), List.of());
    }
}
