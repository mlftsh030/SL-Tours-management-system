package com.sltours.inspections.model;
public enum FuelLevel {
    EMPTY(0), QUARTER(25), HALF(50), THREE_QUARTERS(75), FULL(100);
    public final int percent;
    FuelLevel(int percent) { this.percent = percent; }
}
