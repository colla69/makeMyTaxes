package io.github.colla69.makemytaxes.app.web;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;

/** Clicks inside and outside an open modal dialog, as a user does to close it. */
final class Backdrop {

  private Backdrop() {}

  /**
   * Checks that a click inside the dialog and a text selection from inside onto the backdrop keep
   * the dialog open, and that a click on the backdrop closes it.
   */
  static void closesOnlyOnABackdropClick(Page page, Locator dialog) {
    assertThat(dialog).isVisible();
    BoundingBox box = dialog.boundingBox();
    double insideX = box.x + 10;
    double insideY = box.y + 10;

    page.mouse().click(insideX, insideY);
    assertThat(dialog).isVisible();

    page.mouse().move(insideX, insideY);
    page.mouse().down();
    page.mouse().move(2, 2);
    page.mouse().up();
    assertThat(dialog).isVisible();

    page.mouse().click(2, 2);
    assertThat(dialog).isHidden();
  }
}
