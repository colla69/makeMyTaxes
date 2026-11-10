package io.github.colla69.makemytaxes.core.form;

/** One value to enter in Mein ELSTER, with its source. */
public record ReviewLine(String section, String line, String label, String value, Source source) {}
