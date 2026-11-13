package io.github.colla69.makemytaxes.app.storage;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** The values that the assistant proposes for the fields of a return, until the user decides. */
@Repository
public class ProposalStore {

  /**
   * A value for one field, with its source.
   *
   * @param source the source as the user reads it, such as "Lohnsteuerbescheinigung, Zeile 3"
   * @param documentId the document of the return that the value comes from, or null
   */
  public record Proposal(
      String formId, String fieldId, String value, String source, Long documentId) {}

  public record StoredProposal(long id, Proposal proposal) {}

  private final JdbcClient jdbc;

  ProposalStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** Adds the proposals. A new proposal for a field replaces an older one that still waits. */
  @Transactional
  public void add(long returnId, List<Proposal> proposals) {
    for (Proposal proposal : proposals) {
      jdbc.sql(
              "delete from field_proposal where tax_return_id = ? and form_id = ? and field_id = ?")
          .params(returnId, proposal.formId(), proposal.fieldId())
          .update();
      jdbc.sql(
              "insert into field_proposal"
                  + "(tax_return_id, form_id, field_id, value, source, document_id)"
                  + " values (?, ?, ?, ?, ?, ?)")
          .params(
              returnId,
              proposal.formId(),
              proposal.fieldId(),
              proposal.value(),
              proposal.source(),
              proposal.documentId())
          .update();
    }
  }

  /** The proposals of the return that wait for the user, the oldest first. */
  public List<StoredProposal> proposalsOf(long returnId) {
    return jdbc.sql(
            "select id, form_id, field_id, value, source, document_id from field_proposal"
                + " where tax_return_id = ? order by id")
        .param(returnId)
        .query(
            (row, rowNumber) ->
                new StoredProposal(
                    row.getLong("id"),
                    new Proposal(
                        row.getString("form_id"),
                        row.getString("field_id"),
                        row.getString("value"),
                        row.getString("source"),
                        row.getObject("document_id") == null ? null : row.getLong("document_id"))))
        .list();
  }

  /** The return id is part of the condition, so one return cannot reach the proposal of another. */
  public Optional<StoredProposal> find(long returnId, long proposalId) {
    return proposalsOf(returnId).stream().filter(stored -> stored.id() == proposalId).findFirst();
  }

  public void delete(long returnId, long proposalId) {
    jdbc.sql("delete from field_proposal where id = ? and tax_return_id = ?")
        .params(proposalId, returnId)
        .update();
  }
}
