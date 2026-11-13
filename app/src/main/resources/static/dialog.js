// Opens each popup that the server sends with the page, such as a refused delete, as a modal
// dialog. A button in a form with method="dialog" closes it, and so does the Escape key.
document.addEventListener("DOMContentLoaded", () => {
  document.querySelectorAll("dialog[data-show-modal]").forEach((dialog) => dialog.showModal());
});
