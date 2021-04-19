package com.lightbend.akka.tutorial.model;

import java.util.Objects;

public final class Temperature implements TemperatureReading {

    public final double value;

    public Temperature(final double value) {
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Temperature that = (Temperature) o;
        return Double.compare(that.value, value) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "Temperature{" +
                "value=" + value +
                '}';
    }
}
