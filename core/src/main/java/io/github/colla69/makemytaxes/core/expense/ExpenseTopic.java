package io.github.colla69.makemytaxes.core.expense;

/** Where an expense goes on the return. The label uses the name that the form uses. */
public enum ExpenseTopic {
  WORK("Work expenses (Anlage N)"),
  INSURANCE("Insurance (Anlage Vorsorgeaufwand)"),
  DONATIONS_AND_PENSIONS("Donations and pensions (Sonderausgaben)"),
  HOUSEHOLD_SERVICES("Household services (Haushaltsnahe Aufwendungen)"),
  MEDICAL("Medical costs (Außergewöhnliche Belastungen)"),
  CHILDCARE("Childcare (Anlage Kind)");

  private final String label;

  ExpenseTopic(String label) {
    this.label = label;
  }

  public String label() {
    return label;
  }
}
