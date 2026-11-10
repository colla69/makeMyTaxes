package io.github.colla69.makemytaxes.core.form;

import java.util.List;

/** Hauptvordruck ESt 1 A. */
public final class MainForm implements FormModule {

  public static final String ID = "main";

  /**
   * The fields that belong to the person and not to one year. The person keeps them, and the main
   * form of each year shows them until the user saves other values there.
   */
  public static final List<String> PERSONAL_FIELDS =
      List.of(
          "taxId",
          "birthDate",
          "lastName",
          "firstName",
          "street",
          "houseNumber",
          "postalCode",
          "city");

  /** The personal fields that a new person must have. */
  public static final List<String> NAME_FIELDS = List.of("lastName", "firstName");

  private final FormLayout layout;

  public MainForm(FormLayout layout) {
    this.layout = layout;
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public String title() {
    return "Hauptvordruck (ESt 1 A)";
  }

  /** The personal fields, in the order of the form. They hold for each year, so they have no line. */
  public static List<Field> personalFields() {
    return Sections.generalFields(PERSONAL_FIELDS);
  }

  @Override
  public List<Field> fields() {
    return layout.fields(0);
  }
}
