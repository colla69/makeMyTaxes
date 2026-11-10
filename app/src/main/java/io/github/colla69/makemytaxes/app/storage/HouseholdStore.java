package io.github.colla69.makemytaxes.app.storage;

import io.github.colla69.makemytaxes.core.TaxYear;
import io.github.colla69.makemytaxes.core.form.Answers;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  public void addPerson(String name, Answers details) {
    long householdId =
        jdbc.sql("select id from household order by id limit 1")
            .query(Long.class)
            .optional()
            .orElseGet(this::createHousehold);
    jdbc.sql("insert into person(household_id, name) values (?, ?)")
        .params(householdId, name)
        .update();
    long personId = jdbc.sql("select last_insert_rowid()").query(Long.class).single();
    saveDetails(personId, details);
  }

  public Optional<String> nameOf(long personId) {
    return jdbc.sql("select name from person where id = ?")
        .param(personId)
        .query(String.class)
        .optional();
  }

  public void rename(long personId, String name) {
    jdbc.sql("update person set name = ? where id = ?").params(name, personId).update();
  }

  /** The personal details, such as the address. They hold for every year of the person. */
  public Answers detailsOf(long personId) {
    Map<String, String> values = new LinkedHashMap<>();
    jdbc.sql("select field_id, value from person_detail where person_id = ?")
        .param(personId)
        .query(row -> {
          values.put(row.getString("field_id"), row.getString("value"));
        });
    return new Answers(values);
  }

  /** Replaces all details, so a detail that the user cleared loses its old value. */
  @Transactional
  public void saveDetails(long personId, Answers details) {
    jdbc.sql("delete from person_detail where person_id = ?").param(personId).update();
    details
        .values()
        .forEach(
            (fieldId, value) ->
                jdbc.sql("insert into person_detail(person_id, field_id, value) values (?, ?, ?)")
                    .params(personId, fieldId, value)
                    .update());
  }

  /** Replaces one detail. An empty value clears it. */
  @Transactional
  public void saveDetail(long personId, String fieldId, String value) {
    jdbc.sql("delete from person_detail where person_id = ? and field_id = ?")
        .params(personId, fieldId)
        .update();
    if (!value.isEmpty()) {
      jdbc.sql("insert into person_detail(person_id, field_id, value) values (?, ?, ?)")
          .params(personId, fieldId, value)
          .update();
    }
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

  public boolean hasPerson(long personId) {
    return jdbc.sql("select count(*) from person where id = ?")
            .param(personId)
            .query(Integer.class)
            .single()
        > 0;
  }

  /**
   * Returns the id of the person's return for the year, and creates the return first if it does
   * not exist. Adding a year twice keeps the existing return and its expenses.
   */
  public long addReturn(long personId, TaxYear taxYear) {
    jdbc.sql("insert or ignore into tax_return(person_id, tax_year) values (?, ?)")
        .params(personId, taxYear.value())
        .update();
    return jdbc.sql("select id from tax_return where person_id = ? and tax_year = ?")
        .params(personId, taxYear.value())
        .query(Long.class)
        .single();
  }

  public Optional<Long> personOf(long returnId) {
    return jdbc.sql("select person_id from tax_return where id = ?")
        .param(returnId)
        .query(Long.class)
        .optional();
  }

  public Optional<TaxYear> taxYearOf(long returnId) {
    return jdbc.sql("select tax_year from tax_return where id = ?")
        .param(returnId)
        .query(Integer.class)
        .optional()
        .map(TaxYear::new);
  }
}
