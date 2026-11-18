package io.github.colla69.makemytaxes.app.web;

/**
 * The form for the kind of one file.
 *
 * @param fileHref the address of the file, for the preview
 * @param action where the form sends the kind and the month
 * @param cancelAction where "Abbrechen" sends a file that waits for its kind, so the app forgets
 *     it, or null for a stored document
 * @param kind the kind that the form shows first, or empty
 * @param month the number of the month that the form shows first, or empty
 */
record KindForm(
    String fileName,
    String fileHref,
    boolean image,
    String action,
    String cancelAction,
    String kind,
    String month) {}
