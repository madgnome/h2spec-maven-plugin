package com.madgnome.maven.h2spec;

public class Failure {
    private final String name;
    private final String specId;
    private final String actual;
    private final String expected;
    private final boolean ignored;

    public Failure(final String name, final String specId, final String actual, final String expected, final boolean ignored) {
        this.name = name;
        this.specId = specId;
        this.actual = actual;
        this.expected = expected;
        this.ignored = ignored;
    }

    @Override
    public String toString() {
        return "[" + H2SpecTestSuite.getSpecIdentifier(specId, name) + "]" + " failed\n Expected: " + expected + "\n Actual: " + actual;
    }

    public boolean isIgnored() {
        return ignored;
    }
}
