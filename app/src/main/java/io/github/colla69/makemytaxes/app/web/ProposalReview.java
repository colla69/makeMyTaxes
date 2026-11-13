package io.github.colla69.makemytaxes.app.web;

import io.github.colla69.makemytaxes.app.storage.FormStore;
import io.github.colla69.makemytaxes.app.storage.FormStore.SourcedAnswer;
import io.github.colla69.makemytaxes.app.storage.HouseholdStore;
import io.github.colla69.makemytaxes.app.storage.ProposalStore;
import io.github.colla69.makemytaxes.app.storage.ProposalStore.Proposal;
import io.github.colla69.makemytaxes.app.storage.ProposalStore.StoredProposal;
import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.form.Field;
import io.github.colla69.makemytaxes.core.form.FormModule;
import io.github.colla69.makemytaxes.core.form.Forms;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The user's side of the values that the assistant proposes: the panel shows each stored proposal,
 * and a value enters the return only when the user confirms it. This class reads the stored
 * proposals only, so it does not reach the assistant.
 */
@Component
class ProposalReview {

  /** A proposal as the panel shows it. */
  public record ProposalView(
      long id, String form, String line, String label, String value, String source) {}

  private final HouseholdStore household;
  private final FormStore forms;
  private final ProposalStore proposals;

  ProposalReview(HouseholdStore household, FormStore forms, ProposalStore proposals) {
    this.household = household;
    this.forms = forms;
    this.proposals = proposals;
  }

  /** The proposals of the return that wait for the user. */
  List<ProposalView> pending(long returnId) {
    Optional<TaxYear> taxYear = household.taxYearOf(returnId);
    if (taxYear.isEmpty()) {
      return List.of();
    }
    List<ProposalView> views = new ArrayList<>();
    for (StoredProposal stored : proposals.proposalsOf(returnId)) {
      Proposal proposal = stored.proposal();
      Optional<FormModule> form = Forms.find(taxYear.get(), proposal.formId());
      Optional<Field> field = form.flatMap(module -> module.field(proposal.fieldId()));
      if (field.isPresent()) {
        views.add(
            new ProposalView(
                stored.id(),
                form.get().title(),
                field.get().line(),
                field.get().label(),
                field.get().kind().display(proposal.value()),
                proposal.source()));
      }
    }
    return views;
  }

  /** Puts the value into the return with its source. An unknown proposal changes nothing. */
  @Transactional
  public void confirm(long returnId, long proposalId) {
    proposals
        .find(returnId, proposalId)
        .ifPresent(
            stored -> {
              Proposal proposal = stored.proposal();
              forms.saveSourcedAnswer(
                  returnId,
                  new SourcedAnswer(
                      proposal.formId(),
                      proposal.fieldId(),
                      proposal.value(),
                      proposal.documentId()));
              proposals.delete(returnId, proposalId);
            });
  }

  /** Drops the proposal. The return does not change. */
  public void reject(long returnId, long proposalId) {
    proposals.delete(returnId, proposalId);
  }

  @Transactional
  public void confirmAll(long returnId) {
    for (StoredProposal stored : proposals.proposalsOf(returnId)) {
      confirm(returnId, stored.id());
    }
  }

  @Transactional
  public void rejectAll(long returnId) {
    for (StoredProposal stored : proposals.proposalsOf(returnId)) {
      reject(returnId, stored.id());
    }
  }
}
