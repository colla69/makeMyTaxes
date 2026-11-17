package io.github.colla69.makemytaxes.core.document;

import java.util.List;

/**
 * What the reader found in the file of one month.
 *
 * @param month the amounts of the month of the file
 * @param corrections the corrections of other months of the same year in the file
 * @param yearToDate the sum of the year up to the month, the year column of the statement
 */
public record PayslipContent(
    PayslipValues month, List<PayslipCorrection> corrections, PayslipValues yearToDate) {

  public static final PayslipContent NONE =
      new PayslipContent(PayslipValues.NONE, List.of(), PayslipValues.NONE);
}
