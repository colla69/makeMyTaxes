// Opens each popup that the server sends, such as a refused delete, as a modal dialog: with the
// page, or later with a part of the page that htmx adds. A button in a form with method="dialog"
// closes it, and so do a button with data-close-dialog and the Escape key.
//
// A click on the backdrop closes every modal dialog of the page, also one that another script
// opens. The dialog closes as with the Escape key, so the "close" listener of that script runs and
// unsaved input is dropped as with "Abbrechen".
function showDialogs(root) {
  root.querySelectorAll("dialog[data-show-modal]").forEach((dialog) => {
    if (!dialog.open) {
      dialog.showModal();
    }
  });
}

// A click on the backdrop has the dialog itself as its target, and so does a click on the padding
// of the dialog. Only a point outside the box of the dialog is on the backdrop.
function onBackdrop(dialog, event) {
  const box = dialog.getBoundingClientRect();
  return event.target === dialog
      && (event.clientX < box.left || event.clientX > box.right
          || event.clientY < box.top || event.clientY > box.bottom);
}

// A text selection that starts inside the dialog and ends on the backdrop also fires a click on the
// dialog. The press must start on the backdrop too.
let pressedOnBackdrop = false;

document.addEventListener("DOMContentLoaded", () => showDialogs(document));
document.addEventListener("htmx:load", (event) => showDialogs(event.detail.elt));
document.addEventListener("pointerdown", (event) => {
  const dialog = event.target.closest?.("dialog[open]");
  pressedOnBackdrop = Boolean(dialog) && onBackdrop(dialog, event);
});
document.addEventListener("click", (event) => {
  const button = event.target.closest?.("[data-close-dialog]");
  if (button) {
    button.closest("dialog").close();
    return;
  }
  const dialog = event.target.closest?.("dialog[open]");
  if (dialog && pressedOnBackdrop && onBackdrop(dialog, event)) {
    dialog.close();
  }
  pressedOnBackdrop = false;
});
