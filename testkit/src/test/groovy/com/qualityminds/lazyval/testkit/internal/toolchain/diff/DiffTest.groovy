package com.qualityminds.lazyval.testkit.internal.toolchain.diff

import spock.lang.Shared
import spock.lang.Specification


class DiffTest extends Specification {


    void "Change in same line"(){
        when:
        def diff = Diff.compare("Hello my World", "Hello World")

        then:
        diff == ComparisonResult.Mismatch.of(
                ComparisonResult.Difference.missing(1, "Hello World"),
                ComparisonResult.Difference.present(1, "Hello my World"),
        )
    }

    def "whitespace differences within a line are not a mismatch: #description"() {
        expect:
        Diff.compare(actual, expected) == ComparisonResult.match()

        where:
        description                                | actual            | expected
        "collapsed internal whitespace"            | "foo  bar"        | "foo bar"
        "tab vs spaces"                            | "foo\tbar"        | "foo    bar"
        "trailing whitespace on a line"            | "foo  \nbar"      | "foo\nbar"
        "leading whitespace on a line"             | "  foo\nbar"      | "foo\nbar"
    }

    def "line-ending differences are not a mismatch: #description"() {
        expect:
        Diff.compare(actual, expected) == ComparisonResult.match()

        where:
        description                          | actual              | expected
        "CRLF actual vs LF expected"         | "foo\r\nbar"        | "foo\nbar"
        "LF actual vs CRLF expected"         | "foo\nbar"          | "foo\r\nbar"
        "CR-only actual vs LF expected"      | "foo\rbar"          | "foo\nbar"
        "mixed endings within one side"      | "foo\r\nbar\nbaz"   | "foo\nbar\nbaz"
        "trailing CRLF vs no trailing"       | "foo\r\nbar\r\n"    | "foo\nbar"
    }

    def "blank lines on only one side are not a mismatch: #description"() {
        expect:
        Diff.compare(actual, expected) == ComparisonResult.match()

        where:
        description                                | actual              | expected
        "extra trailing blank in actual"           | "foo\nbar\n"        | "foo\nbar"
        "extra trailing blank in expected"         | "foo\nbar"          | "foo\nbar\n"
        "extra blank mid-text in actual"           | "foo\n\nbar"        | "foo\nbar"
        "extra blank mid-text in expected"         | "foo\nbar"          | "foo\n\nbar"
        "multiple extra blanks"                    | "foo\n\n\nbar"      | "foo\nbar"
        "whitespace-only line treated as blank"    | "foo\n   \nbar"     | "foo\nbar"
    }

    void "line numbers stay anchored to the original source across suppressed blanks"() {
        when:
        // expected has a blank between foo and bar; actual has no blank but a different bar.
        // The reported MISSING line for "bar" must be L3 (its real expected-side line number),
        // not L2 (its position if blanks had been filtered out).
        def diff = Diff.compare("foo\nBAZ", "foo\n\nbar")

        then:
        diff == ComparisonResult.Mismatch.of(
                ComparisonResult.Difference.present(2, "BAZ"),
                ComparisonResult.Difference.missing(3, "bar"),
        )
    }

    @Shared
    def expectedContent = """
            public class QuantityAttributeConverter implements AttributeConverter<Quantity, Integer> {
              public Integer convertToDatabaseColumn(Quantity type) {
                if(type == null) {
                  return null;
                }
                return type.value();
              }

              public Quantity convertToEntityAttribute(Integer dbValue) {
                if(dbValue == null) {
                  return null;
                }
                return new Quantity(dbValue);
              }
            }"""

    def invalidContent = """
            public class QuantityAttributeConverter implements AttributeConverter<Quantity, Integer> {
              public Integer convertToDatabaseColumn(Quantity type) {
                return type.value();
              }

              public Quantity convertToEntityAttribute(Integer dbValue) {
                if(dbValue ==  null) {
                  return null;
                }
                // fubar
                return new Quantity(dbValue);
              }
            }"""

    void "Diffing with same contents shows no differences"(){
        def generated = expectedContent

        expect:
        Diff.compare(generated, expectedContent) == ComparisonResult.match()
    }


    void "Diffing with mismatched contents yield correct deltas"(){
        when:
        def diff = Diff.compare(invalidContent, expectedContent)

        then:
        diff == ComparisonResult.Mismatch.of(
                ComparisonResult.Difference.missing(4, "                if(type == null) {"),
                ComparisonResult.Difference.missing(5, "                  return null;"),
                ComparisonResult.Difference.missing(6, "                }"),
                ComparisonResult.Difference.present(11, "                // fubar"),
        )
    }

    def "rendered diff contains the changes and surrounding context"() {
        given:
        def rendered = ((ComparisonResult.Mismatch) Diff.compare(invalidContent, expectedContent))
                .render()

        expect:
        rendered.contains("if(type == null) {")     // a deleted line
        rendered.contains("// fubar")               // an inserted line
        rendered.contains("return type.value();")   // a context line
        rendered.contains("@@")                      // hunk separator present
    }

}
