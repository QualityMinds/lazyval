package com.qualityminds.lazyval.integration.shared;

import org.jspecify.annotations.Nullable;

import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

public final class Birthdate implements Comparable<Birthdate> {

    public static final Birthdate UNKNOWN = new Birthdate(new ParsedDate(0, 0, 0));
    private static final Parser parser = new Parser();

    @Override
    public int compareTo(@Nullable Birthdate other) {
        if(other == null){
            return 1;
        }
        return this.value.compareTo(other.value);
    }

    public sealed interface State {
        record Complete(LocalDate date) implements State {}
        record DayUnknown (int month, int year) implements State {}
        record DayMonthUnknown (int year) implements State {}
        record Unknown() implements State {
            private static final Unknown INSTANCE = new Unknown();
        }
    }

    /**
     * ISO-8601 representation
     */
    private final String value;
    private final transient State state;

    public String value(){ return value; }

    public State getState() { return state; }

    private Birthdate(final ParsedDate parsedDate){
        this.value = parsedDate.asIsoString();
        if(parsedDate.isComplete()){
            this.state = new State.Complete(LocalDate.of(parsedDate.year, parsedDate.month, parsedDate.day));
        }else if(parsedDate.isDayUnknown()){
            this.state = new State.DayUnknown(parsedDate.year, parsedDate.month);
        }else if(parsedDate.isDayMonthUnknown()){
            this.state = new State.DayMonthUnknown(parsedDate.year);
        }else{
            this.state = State.Unknown.INSTANCE;
        }
    }

    public static Birthdate of(String value){
        if(value == null || value.isBlank()){
            return null;
        }
        var parsedDate = parser.parse(value);
        // use shared instance
        if(parsedDate.isUnknown()){
            return UNKNOWN;
        }
        return new Birthdate(parsedDate);
    }


    public static Birthdate of(LocalDate date){
        if(date == null){
            return null;
        }
        return new Birthdate(ParsedDate.from(date));
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        Birthdate birthdate = (Birthdate) o;
        return Objects.equals(value, birthdate.value);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(value);
    }

    private record ParsedDate(int year, int month, int day) {
        ParsedDate {
            checkValid();
        }

        static ParsedDate from(LocalDate date){
            return new ParsedDate(date.getYear(), date.getMonthValue(), date.getDayOfMonth());
        }

        boolean isUnknown() {
            return year == 0 && month == 0 && day == 0;
        }

        boolean isComplete(){
            return year != 0 && month != 0 && day != 0;
        }

        boolean isDayUnknown(){
            return year == 0 && month == 0 && day != 0;
        }

        boolean isDayMonthUnknown(){
            return year != 0 && month == 0 && day == 0;
        }

        /**
         * will cause a DateTimeParseException for invalid Dates
         */
        @SuppressWarnings("ResultOfMethodCallIgnored")
        void checkValid(){
            LocalDate.of(
                    year,
                    month != 0 ? month : 1,
                    day != 0 ? day : 1
            );
        }

        /**
         * Converts the parsingresult to yyyy-mm-dd
         */
        String asIsoString(){
            return "%04d-%02d-%02d".formatted(year, month, day);
        }
    }

    private static class Parser {

        private static final Pattern DATE_PATTERN_ISO_8601 = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

        private ParsedDate parse(String value){
            final ParsedDate parsed;
            if(DATE_PATTERN_ISO_8601.matcher(value).matches()){
                parsed = parseIso(value);
            }else{
                throw new IllegalArgumentException("Date '%s' is not matching yyyy-mm-dd".formatted(value));
            }
            return parsed;
        }

        private static ParsedDate parseIso(final String value){
            return new ParsedDate(
                    Integer.parseInt(value.substring(0,4)),
                    Integer.parseInt(value.substring(5,7)),
                    Integer.parseInt(value.substring(8,10))
            );
        }
    }
}
