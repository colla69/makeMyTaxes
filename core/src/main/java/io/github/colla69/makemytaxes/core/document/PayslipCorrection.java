package io.github.colla69.makemytaxes.core.document;

import java.time.YearMonth;

/**
 * A correction of an earlier month that a later payslip holds under its own period. The amounts
 * are the change, with their sign. An empty amount was not read.
 */
public record PayslipCorrection(YearMonth month, PayslipValues values) {}
