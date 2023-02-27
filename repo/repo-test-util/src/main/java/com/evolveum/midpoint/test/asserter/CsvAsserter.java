/*
 * Copyright (C) 2018-2022 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */
package com.evolveum.midpoint.test.asserter;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.ListAssert;
import org.jetbrains.annotations.NotNull;

import com.evolveum.midpoint.test.IntegrationTestTools;

/**
 * Asserts on CSV files, like reports. The first draft.
 */
public class CsvAsserter<RA> extends AbstractAsserter<RA> {

    private static final String STANDARD_FOOTER = "No active subscription. Please support midPoint by purchasing a subscription.";

    @NotNull private final List<String> lines;

    private CSVFormat customCsvFormat;
    private boolean expectingStandardFooter = true;
    private boolean parsed;

    // The following are filled in after calling "parse" method.

    private List<String> headerNames;
    private List<CSVRecord> records;

    @NotNull private Set<Integer> numericColumns = Set.of(); // because of sorting

    public CsvAsserter(@NotNull List<String> lines, RA returnAsserter, String details) {
        super(returnAsserter, details);
        this.lines = lines;
    }

    public CsvAsserter<RA> withNotExpectingStandardFooter() {
        expectingStandardFooter = false;
        return this;
    }

    public CsvAsserter<RA> withCustomFormat(CSVFormat format) {
        customCsvFormat = format;
        return this;
    }

    public CsvAsserter<RA> withNumericColumns(Integer... columns) {
        numericColumns = Set.of(columns);
        return this;
    }

    public CsvAsserter<RA> parse() throws IOException {
        if (parsed) {
            return this;
        }
        if (expectingStandardFooter) {
            removeStandardFooter();
        }
        CSVFormat format =
                Objects.requireNonNullElseGet(
                        customCsvFormat,
                        () -> CSVFormat.newFormat(';')
                                .builder()
                                .setQuote('"')
                                .setEscape('\\')
                                .setHeader()
                                .build());

        try (CSVParser parser = CSVParser.parse(
                String.join("\n", lines),
                format)) {
            headerNames = parser.getHeaderNames();
            records = parser.getRecords();
        }
        parsed = true;
        return this;
    }

    private void removeStandardFooter() {
        String last = lines.get(lines.size() - 1);
        assertThat(last).as("last line").isEqualTo(STANDARD_FOOTER);
        lines.remove(lines.size() - 1);
    }

    public CsvAsserter<RA> display() {
        return display(desc());
    }

    public CsvAsserter<RA> display(String message) {
        if (parsed) {
            IntegrationTestTools.display(message + ": header names", headerNames);
            IntegrationTestTools.display(message + ": records", records);
        } else {
            IntegrationTestTools.display(message + ": raw content", lines);
        }
        return this;
    }

    public CsvAsserter<RA> sort(Comparator<CSVRecord> comparator) throws IOException {
        parse();
        records = new ArrayList<>(records);
        records.sort(comparator);
        return this;
    }

    public CsvAsserter<RA> sortBy(int... columns) throws IOException {
        return sort((o1, o2) -> {
            for (int column : columns) {
                //noinspection unchecked
                int c = getValue(o1, column).compareTo(getValue(o2, column));
                if (c != 0) {
                    return c;
                }
            }
            return 0;
        });
    }

    @SuppressWarnings("rawtypes")
    private Comparable getValue(CSVRecord record, int column) {
        String stringValue = record.get(column);
        return isNumeric(column) ? Long.valueOf(stringValue) : stringValue;
    }

    private boolean isNumeric(int column) {
        return numericColumns.contains(column);
    }

    @Override
    protected String desc() {
        return descWithDetails(lines.size() + "-line CSV");
    }

    public CsvAsserter<RA> assertRecords(int expected) throws IOException {
        parse();
        assertThat(records).as("records").hasSize(expected);
        return this;
    }

    public CsvAsserter<RA> assertRecords(Consumer<ListAssert<?>> consumer) throws IOException {
        parse();
        consumer.accept(
                assertThat(records).as("records"));
        return this;
    }

    public CsvAsserter<RA> assertColumns(int expected) throws IOException {
        parse();
        assertThat(headerNames).as("header names").hasSize(expected);
        return this;
    }

    public RecordAsserter record(int index) throws IOException {
        parse();
        var recordAsserter = new RecordAsserter(index, this, getDetails());
        copySetupTo(recordAsserter);
        return recordAsserter;
    }

    public class RecordAsserter extends AbstractAsserter<CsvAsserter<RA>> {

        int index;

        RecordAsserter(int index, CsvAsserter<RA> returnAsserter, String details) {
            super(returnAsserter, details);
            this.index = index;
        }

        public RecordAsserter assertValue(int column, String expected) {
            return assertValue(column, a -> a.isEqualTo(expected));
        }

        public RecordAsserter assertValue(int column, Consumer<AbstractStringAssert<?>> assertConsumer) {
            assertConsumer.accept(
                    assertThat(getValue(column)).as("value in col " + column + " in " + desc()));
            return this;
        }

        public RecordAsserter assertValueNotEmpty(int column) {
            assertThat(getValue(column)).as("value in col " + column + " in " + desc()).isNotEmpty();
            return this;
        }

        public RecordAsserter assertValuesEqual(int column1, int column2) {
            String value1 = getValue(column1);
            String value2 = getValue(column2);
            assertThat(value1)
                    .withFailMessage(
                            String.format("values in col %d (%s) and %d (%s) differ although they should not; in %s",
                                    column1, value1, column2, value2, desc()))
                    .isEqualTo(value2);
            return this;
        }

        public RecordAsserter assertValues(int column, String... expected) {
            return assertValues(column, Set.of(expected));
        }

        @SuppressWarnings("WeakerAccess")
        public RecordAsserter assertValues(int column, Set<String> expected) {
            assertThat(getValues(column))
                    .as("values in col " + column + " in " + desc())
                    .containsExactlyInAnyOrderElementsOf(expected);
            return this;
        }

        private String getValue(int column) {
            return records.get(index).get(column);
        }

        private Set<String> getValues(int column) {
            return Arrays.stream(getValue(column).split(","))
                    .collect(Collectors.toSet());
        }

        @Override
        protected String desc() {
            return descWithDetails("row #" + index + " in " + CsvAsserter.this.desc());
        }
    }
}
