package io.github.colla69.makemytaxes.core.form;

/**
 * Which days count for the home office amount, in the words of the year. In 2022, § 4(5) sentence
 * 1 no. 6b sentence 4 EStG counts each day of work only at home. From 2023, § 52(6) sentence 13
 * EStG applies no. 6c: sentence 1 counts each day of work mainly at home without a visit to the
 * first place of work, and sentence 2 counts each day of work at home when no other workplace is
 * available for good.
 */
public final class HomeOfficeDay {

  private static final int FIRST_YEAR_OF_6C = 2023;

  private HomeOfficeDay() {}

  /** The help text of the field. */
  public static String help(int year) {
    return year >= FIRST_YEAR_OF_6C
        ? "Tage, an denen Sie überwiegend zu Hause gearbeitet und die erste Tätigkeitsstätte nicht"
            + " aufgesucht haben. Steht Ihnen dauerhaft kein anderer Arbeitsplatz zur Verfügung,"
            + " zählt jeder Tag mit Arbeit zu Hause, auch wenn Sie am selben Tag auswärts oder an"
            + " der ersten Tätigkeitsstätte gearbeitet haben. Das Finanzamt berechnet den Betrag."
        : "Tage, an denen Sie nur zu Hause gearbeitet haben. Das Finanzamt berechnet den Betrag.";
  }

  /**
   * The question of the interview. In 2025 the help of the field names line 58 for the days of
   * sentence 1 and line 59 for the days of sentence 2, so the question names no rule.
   */
  public static String question(int year) {
    if (year == 2025) {
      return "An wie vielen Tagen haben Sie zu Hause gearbeitet? Der Hinweis nennt, welche Tage"
          + " zählen.";
    }
    return year >= FIRST_YEAR_OF_6C
        ? "An wie vielen Tagen haben Sie überwiegend zu Hause gearbeitet, ohne die erste"
            + " Tätigkeitsstätte aufzusuchen?"
        : "An wie vielen Tagen haben Sie nur zu Hause gearbeitet?";
  }

  /** The days in the short text of the review, after "für jeden". */
  public static String reviewText(int year) {
    return year >= FIRST_YEAR_OF_6C
        ? "Tag überwiegend zu Hause ohne Besuch der ersten Tätigkeitsstätte, ohne dauerhaft"
            + " anderen Arbeitsplatz für jeden Tag mit Arbeit zu Hause"
        : "Tag nur zu Hause";
  }
}
