package io.github.colla69.makemytaxes.core.form;

/**
 * The printed Lohnsteuerbescheinigung for 2023. Its numbered lines are the same as in 2022, so
 * the layout takes the lines of 2022 and reads only certificates for 2023. The source is named in
 * {@code WageTaxCertificate2023Test}.
 */
public final class WageTaxCertificate2023 {

  public static final WageTaxCertificate LAYOUT =
      new WageTaxCertificate(
          WageTaxCertificate2022.LAYOUT.lines(),
          WageTaxCertificate2022.LAYOUT.withoutLine(),
          text -> WageTaxCertificateReader2022.read(text, 2023));

  private WageTaxCertificate2023() {}
}
