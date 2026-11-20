// Sets up the receipt check, the modal dialog that the server sends after the user chose a receipt.
// The dialog shows the chosen file beside the read fields. While it is open, the same fields on the
// page are off, so that the form sends each field once and "Speichern" saves the values of the
// dialog. "Abbrechen", "×" and the Escape key close the dialog: the app stores nothing, the file is
// cleared, and the fields on the page are back as the user left them.
document.addEventListener("htmx:load", (event) => {
  event.detail.elt.querySelectorAll("dialog[data-receipt-check]").forEach(setUpReceiptCheck);
  if (event.detail.elt.id === "receipt-check-error") {
    placeCheckError(event.detail.elt);
  }
});

// A refused save answers with the error box of the dialog. The error stands below the field it names.
function placeCheckError(box) {
  const field = box.querySelector("[data-field]")?.dataset.field;
  box.closest("dialog").querySelector(`[name="${field}"]`)?.closest(".field")?.after(box);
}

function setUpReceiptCheck(dialog) {
  const form = dialog.closest("form");
  const fileInput = form.querySelector("input[type=file][name=file]");
  const pageFields = form.querySelectorAll("#expense-fields input, #expense-fields select");
  const file = fileInput.files[0];
  const url = file ? URL.createObjectURL(file) : null;
  if (file) {
    dialog.querySelector(".receipt-preview").replaceChildren(previewOf(file, url));
  }
  pageFields.forEach((control) => (control.disabled = true));
  // An error about a file that the user chose before does not belong to this file.
  form.querySelector("#read-error")?.replaceChildren();

  dialog.addEventListener("close", () => {
    if (url) {
      URL.revokeObjectURL(url);
    }
    fileInput.value = "";
    pageFields.forEach((control) => (control.disabled = false));
    dialog.remove();
    // The topic of the page decides again which fields show.
    form
      .querySelector("#expense-fields select[name=topic]")
      .dispatchEvent(new Event("change", { bubbles: true }));
  });
}

function previewOf(file, url) {
  if (file.type.startsWith("image/")) {
    const image = document.createElement("img");
    image.src = url;
    image.alt = file.name;
    return image;
  }
  const frame = document.createElement("iframe");
  frame.src = url;
  frame.title = file.name;
  return frame;
}
