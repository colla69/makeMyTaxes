// Shows the preview of a document in a modal dialog, so that the list keeps its place. The script
// loads the page that the "Vorschau" form asks for and moves its preview into the dialog. Without
// JavaScript, the form loads that page.
document.addEventListener("DOMContentLoaded", () => {
  const dialog = document.getElementById("preview-dialog");
  if (!dialog) {
    return;
  }
  const title = dialog.querySelector("h2");
  const body = dialog.querySelector(".preview-body");
  let opener = null;

  document.querySelectorAll("form.preview").forEach((form) => {
    form.addEventListener("submit", async (event) => {
      event.preventDefault();
      opener = event.submitter;
      const url = form.action + "?" + new URLSearchParams(new FormData(form));
      const response = await fetch(url);
      if (!response.ok) {
        form.submit();
        return;
      }
      const page = new DOMParser().parseFromString(await response.text(), "text/html");
      const preview = page.getElementById("document-preview");
      title.textContent = preview.dataset.fileName;
      body.replaceChildren(preview.querySelector("img, iframe"));
      dialog.showModal();
    });
  });

  // The "×" button, the Escape key and a click on the backdrop (dialog.js) close the dialog.
  dialog.addEventListener("close", () => {
    body.replaceChildren();
    opener?.focus();
  });
});
