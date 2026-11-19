// Opens each popup that the server sends, such as a refused delete, as a modal dialog: with the
// page, or later with a part of the page that htmx adds. A button in a form with method="dialog"
// closes it, and so do a button with data-close-dialog and the Escape key.
function showDialogs(root) {
  root.querySelectorAll("dialog[data-show-modal]").forEach((dialog) => {
    if (!dialog.open) {
      dialog.showModal();
    }
  });
}

document.addEventListener("DOMContentLoaded", () => showDialogs(document));
document.addEventListener("htmx:load", (event) => showDialogs(event.detail.elt));
document.addEventListener("click", (event) => {
  const button = event.target.closest("[data-close-dialog]");
  if (button) {
    button.closest("dialog").close();
  }
});
