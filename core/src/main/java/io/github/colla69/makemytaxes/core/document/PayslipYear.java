package io.github.colla69.makemytaxes.core.document;

import io.github.colla69.makemytaxes.core.expense.Money;
import java.time.Month;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The payslips of one year together. The value of a month is the amount of its own payslip plus
 * each correction of the month in any payslip of the year. A correction that two files print
 * counts once. The year only adds up what the files say. It never changes a value.
 */
public final class PayslipYear {

  /** The file of one month and what the reader found in it. */
  public record Payslip(String fileName, YearMonth month, PayslipContent content) {}

  /**
   * A correction and the file that holds it.
   *
   * @param repeatedIn the later files that print the same correction again. It counts once. The
   *     files do not say if a repeat is the same correction or a new one with the same amounts, so
   *     the year check shows a difference in the second case.
   */
  public record SourcedCorrection(String fileName, PayslipValues values, List<String> repeatedIn) {}

  /**
   * Where the value of a month comes from.
   *
   * @param own the amounts of the payslip of the month, or empty without a payslip
   * @param corrections the corrections of the month in the files of the year
   */
  public record MonthValue(Optional<PayslipValues> own, List<SourcedCorrection> corrections) {

    public Optional<Money> taxableGross() {
      return total(PayslipValues::taxableGross);
    }

    public Optional<Money> wageTax() {
      return total(PayslipValues::wageTax);
    }

    /** Empty if one of the parts was not read. */
    public Optional<Money> total(Function<PayslipValues, Optional<Money>> amount) {
      List<Optional<Money>> parts = new ArrayList<>();
      own.ifPresent(values -> parts.add(amount.apply(values)));
      corrections.forEach(correction -> parts.add(amount.apply(correction.values())));
      if (parts.stream().anyMatch(Optional::isEmpty)) {
        return Optional.empty();
      }
      return Optional.of(parts.stream().map(Optional::get).reduce(Money.ofCents(0), Money::plus));
    }
  }

  private final List<Payslip> payslips;

  private PayslipYear(List<Payslip> payslips) {
    this.payslips = payslips;
  }

  public static PayslipYear of(List<Payslip> payslips) {
    return new PayslipYear(List.copyOf(payslips));
  }

  /** The value of the month, or empty if the month has neither a payslip nor a correction. */
  public Optional<MonthValue> month(Month month) {
    Optional<PayslipValues> own =
        payslips.stream()
            .filter(payslip -> payslip.month().getMonth() == month)
            .map(payslip -> payslip.content().month())
            .findFirst();
    List<SourcedCorrection> corrections = correctionsOf(month);
    if (own.isEmpty() && corrections.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new MonthValue(own, corrections));
  }

  /** The values of all months with a payslip or a correction, for a sum. */
  public List<Optional<Money>> monthTotals(Function<PayslipValues, Optional<Money>> amount) {
    List<Optional<Money>> totals = new ArrayList<>();
    for (Month month : Month.values()) {
      month(month).ifPresent(value -> totals.add(value.total(amount)));
    }
    return totals;
  }

  /** The payslip of the latest month, whose year column holds the sum of the year so far. */
  public Optional<Payslip> last() {
    return payslips.stream().max(Comparator.comparing(Payslip::month));
  }

  private List<SourcedCorrection> correctionsOf(Month month) {
    Map<PayslipValues, String> firstFile = new LinkedHashMap<>();
    Map<PayslipValues, List<String>> repeats = new HashMap<>();
    for (Payslip payslip : payslips.stream().sorted(Comparator.comparing(Payslip::month)).toList()) {
      for (PayslipCorrection correction : payslip.content().corrections()) {
        if (correction.month().getMonth() != month) {
          continue;
        }
        if (firstFile.putIfAbsent(correction.values(), payslip.fileName()) != null) {
          repeats.computeIfAbsent(correction.values(), values -> new ArrayList<>())
              .add(payslip.fileName());
        }
      }
    }
    return firstFile.entrySet().stream()
        .map(
            entry ->
                new SourcedCorrection(
                    entry.getValue(),
                    entry.getKey(),
                    repeats.getOrDefault(entry.getKey(), List.of())))
        .toList();
  }
}
