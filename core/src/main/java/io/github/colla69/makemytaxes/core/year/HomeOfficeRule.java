package io.github.colla69.makemytaxes.core.year;

/**
 * The rule that decides which days count for the home office amount. YearPackTest cites the
 * source of each rule.
 */
public enum HomeOfficeRule {

  /** Until 2022: each day of work only at home, § 4(5) sentence 1 no. 6b sentence 4 EStG. */
  ONLY_AT_HOME(
      "für jeden Tag nur zu Hause",
      "§ 4 Abs. 5 Satz 1 Nr. 6b Satz 4 EStG mit § 9 Abs. 5 EStG"),

  /**
   * From 2023: the day rate of § 4(5) sentence 1 no. 6c EStG. Sentence 1 counts each day of work
   * mostly at home without a trip to the first workplace. Sentence 2 also counts a day with a trip
   * if no other workplace is permanently available.
   */
  DAY_RATE(
      "für jeden Tag überwiegend zu Hause ohne Weg zur ersten Tätigkeitsstätte. Steht dauerhaft"
          + " kein anderer Arbeitsplatz zur Verfügung, für jeden Tag mit Arbeit zu Hause, auch mit"
          + " einem Weg zur Arbeit",
      "§ 4 Abs. 5 Satz 1 Nr. 6c Satz 1 und 2 EStG mit § 9 Abs. 5 Satz 1 EStG");

  private final String condition;
  private final String source;

  HomeOfficeRule(String condition, String source) {
    this.condition = condition;
    this.source = source;
  }

  /** The days that count, as the review page names them after the amount per day. */
  public String condition() {
    return condition;
  }

  /** The legal source, as the review page cites it. */
  public String source() {
    return source;
  }
}
