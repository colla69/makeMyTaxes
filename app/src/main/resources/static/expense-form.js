// Shows "Davon erstattet" only for the topic that has a refund, Krankheitskosten. The field is in a
// fieldset that is disabled while hidden, so the form does not send it. The receipt check has its
// own topic, so its topic decides while it is open.
document.addEventListener("change", (event) => {
  if (event.target.matches("#expense-form select[name=topic]")) {
    showRefundFor(event.target);
  }
});

document.addEventListener("htmx:load", (event) => {
  event.detail.elt
    .querySelectorAll("dialog[data-receipt-check] select[name=topic]")
    .forEach(showRefundFor);
});

function showRefundFor(topic) {
  const refund = topic.form.querySelector("#refund");
  const shown = topic.value === refund.dataset.topic;
  refund.hidden = !shown;
  refund.disabled = !shown;
}
