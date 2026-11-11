package io.github.colla69.makemytaxes.core.form;

/**
 * The printed Lohnsteuerbescheinigung for 2024. Each line that the app uses has the same number and
 * label as in 2022, so the layout takes the lines of 2022 and reads the year 2024. The source is
 * named in {@code Definition2024Test}.
 */
public final class WageTaxCertificate2024 {

  private static final int YEAR = 2024;

  public static final WageTaxCertificate LAYOUT =
      new WageTaxCertificate(
          WageTaxCertificate2022.LAYOUT.lines(),
          WageTaxCertificate2022.LAYOUT.withoutLine(),
          text -> WageTaxCertificateReader2022.read(text, YEAR));

  private WageTaxCertificate2024() {}
}
