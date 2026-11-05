package io.github.colla69.makemytaxes.app.storage;

import io.github.colla69.makemytaxes.core.TaxYear;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class HouseholdStore {

  public record StoredPerson(long id, String name, List<StoredReturn> returns) {}

  public record StoredReturn(long id, TaxYear taxYear) {}

  private final JdbcClient jdbc;

  HouseholdStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /** The app holds one household, so the first person creates it. */
  @Transactional
  public void addPerson(String name) {
    long householdId =
        jdbc.sql("select id from household order by id limit 1")
            .query(Long.class)
            .optional()
            .orElseGet(this::createHousehold);
    jdbc.sql("insert into person(household_id, name) values (?, ?)")
        .params(householdId, name)
        .update();
  }

  private long createHousehold() {
    jdbc.sql("insert into household(name) values ('Household')").update();
    return jdbc.sql("select last_insert_rowid()").query(Long.class).single();
  }

  public List<StoredPerson> persons() {
    // Read the persons first. The pool has one connection, so a query inside the row mapper
    // would wait forever for the connection that the outer query still holds.
    record PersonRow(long id, String name) {}
    List<PersonRow> rows =
        jdbc.sql("select id, name from person order by id")
            .query((row, rowNumber) -> new PersonRow(row.getLong("id"), row.getString("name")))
            .list();
    return rows.stream()
        .map(person -> new StoredPerson(person.id(), person.name(), returnsOf(person.id())))
        .toList();
  }

  private List<StoredReturn> returnsOf(long personId) {
    return jdbc.sql("select id, tax_year from tax_return where person_id = ? order by tax_year")
        .param(personId)
        .query(
            (row, rowNumber) ->
                new StoredReturn(row.getLong("id"), new TaxYear(row.getInt("tax_year"))))
        .list();
  }

  /** Adding a year twice keeps the existing return and its expenses. */
  public void addReturn(long personId, TaxYear taxYear) {
    jdbc.sql("insert or ignore into tax_return(person_id, tax_year) values (?, ?)")
        .params(personId, taxYear.value())
        .update();
  }

  public Optional<TaxYear> taxYearOf(long returnId) {
    return jdbc.sql("select tax_year from tax_return where id = ?")
        .param(returnId)
        .query(Integer.class)
        .optional()
        .map(TaxYear::new);
  }
}
