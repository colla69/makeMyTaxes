package io.github.colla69.makemytaxes.core.expense;

/** Where an expense goes on the return. The label uses the name that the form uses. */
public enum ExpenseTopic {
  WORK_EQUIPMENT("Arbeitsmittel (Anlage N)"),
  WORK_TRAINING("Fortbildungskosten (Anlage N)"),
  WORK_ASSOCIATIONS("Beiträge zu Berufsverbänden (Anlage N)"),
  WORK_OTHER("Weitere Werbungskosten (Anlage N)"),
  INSURANCE("Versicherungen (Anlage Vorsorgeaufwand)"),
  DONATIONS_AND_PENSIONS("Spenden und Renten (Sonderausgaben)"),
  HOUSEHOLD_SERVICES("Haushaltsnahe Aufwendungen"),
  MEDICAL("Krankheitskosten (Außergewöhnliche Belastungen)"),
  CHILDCARE("Kinderbetreuung (Anlage Kind)");

  private final String label;

  ExpenseTopic(String label) {
    this.label = label;
  }

  public boolean isWorkExpense() {
    return name().startsWith("WORK_");
  }

  public String label() {
    return label;
  }
}
