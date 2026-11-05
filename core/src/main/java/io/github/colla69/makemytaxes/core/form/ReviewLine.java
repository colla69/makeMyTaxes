package io.github.colla69.makemytaxes.core.form;

/**
 * One value to enter in Mein ELSTER.
 *
 * @param evidence where the value comes from, or an empty text
 */
public record ReviewLine(String section, String line, String label, String value, String evidence) {}
