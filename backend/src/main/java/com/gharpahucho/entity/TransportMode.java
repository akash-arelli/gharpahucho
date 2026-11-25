package com.gharpahucho.entity;

public enum TransportMode {
    ANY, TRAIN, BUS, SHARED_CAR, CAB;

    public String label() {
        return switch (this) {
            case ANY -> "Any";
            case TRAIN -> "Train";
            case BUS -> "Bus";
            case SHARED_CAR -> "Shared car";
            case CAB -> "Cab";
        };
    }
}
