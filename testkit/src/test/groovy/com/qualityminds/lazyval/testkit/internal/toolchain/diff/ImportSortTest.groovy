package com.qualityminds.lazyval.testkit.internal.toolchain.diff


import spock.lang.Specification


class ImportSortTest extends Specification {

    void "no imports — content unchanged"() {
        expect:
        ImportSort.sort(input) == input

        where:
        input << [
                "",
                "package x;",
                "package x;\npublic class X {}",
                "// just a comment\n// another",
        ]
    }

    void "single import is returned unchanged"() {
        given:
        def input = "package x;\n\nimport a.B;\n\npublic class X {}"

        expect:
        ImportSort.sort(input) == input
    }

    void "already-sorted imports stay as they are"() {
        given:
        def input = "package x;\n\nimport a.A;\nimport b.B;\nimport c.C;\n\npublic class X {}"

        expect:
        ImportSort.sort(input) == input
    }

    void "reverse-sorted imports become sorted"() {
        given:
        def input = "package x;\n\nimport c.C;\nimport b.B;\nimport a.A;\n\npublic class X {}"
        def expected = "package x;\n\nimport a.A;\nimport b.B;\nimport c.C;\n\npublic class X {}"

        expect:
        ImportSort.sort(input) == expected
    }

    void "package statement above imports is preserved"() {
        given:
        def input = "package some.long.pkg.name;\n\nimport b.B;\nimport a.A;\n"
        def expected = "package some.long.pkg.name;\n\nimport a.A;\nimport b.B;\n"

        expect:
        ImportSort.sort(input) == expected
    }

    void "class declaration below imports stays where it is"() {
        given:
        def input = "import z.Z;\nimport a.A;\n\n@SomeAnnotation\npublic class X {\n    int field;\n}"
        def expected = "import a.A;\nimport z.Z;\n\n@SomeAnnotation\npublic class X {\n    int field;\n}"

        expect:
        ImportSort.sort(input) == expected
    }

    void "blank lines between imports are removed from sorted block"() {
        given:
        def input = "import b.B;\n\nimport a.A;\n\npublic class X {}"
        def expected = "import a.A;\nimport b.B;\n\npublic class X {}"

        expect:
        ImportSort.sort(input) == expected
    }

    void "static and regular imports sort by raw string order"() {
        given: 'lexicographic sort interleaves them — i < s, so non-static comes first'
        def input = "import static a.B.foo;\nimport a.A;\n"
        def expected = "import a.A;\nimport static a.B.foo;\n"

        expect:
        ImportSort.sort(input) == expected
    }

    void "Kotlin 'as' aliases sort normally"() {
        given:
        def input = "import foo.Bar as MyBar\nimport baz.Qux as BazQux\n"
        def expected = "import baz.Qux as BazQux\nimport foo.Bar as MyBar\n"

        expect:
        ImportSort.sort(input) == expected
    }

    void "tab-indented line starting with 'import' is not treated as an import"() {
        given: 'leading tab disqualifies the line; the block sees only the two real imports'
        def input = "import b.B;\nimport a.A;\n\tnot.an.import;\n"
        def expected = "import a.A;\nimport b.B;\n\tnot.an.import;\n"

        expect:
        ImportSort.sort(input) == expected
    }

    void "comment within imports ends the block"() {
        given: 'imports above the comment are sorted; the comment and everything after are untouched'
        def input = "import b.B;\nimport a.A;\n// section header\nimport d.D;\nimport c.C;\n"
        def expected = "import a.A;\nimport b.B;\n// section header\nimport d.D;\nimport c.C;\n"

        expect:
        ImportSort.sort(input) == expected
    }

    void "empty file is a no-op"() {
        expect:
        ImportSort.sort("") == ""
    }

    void "duplicate imports stay duplicated after sort"() {
        given:
        def input = "import b.B;\nimport a.A;\nimport a.A;\n"
        def expected = "import a.A;\nimport a.A;\nimport b.B;\n"

        expect:
        ImportSort.sort(input) == expected
    }

    void "trailing content after imports keeps its original order"() {
        given:
        def input = "import b.B;\nimport a.A;\n\npublic class X {\n    void m() {}\n    void n() {}\n}"
        def expected = "import a.A;\nimport b.B;\n\npublic class X {\n    void m() {}\n    void n() {}\n}"

        expect:
        ImportSort.sort(input) == expected
    }

    void "sort is idempotent: sort(sort(x)) == sort(x)"() {
        when:
        def once = ImportSort.sort(input)
        def twice = ImportSort.sort(once)

        then:
        once == twice

        where:
        input << [
                "",
                "package x;\n",
                "import b.B;\nimport a.A;\n",
                "import b.B;\n\nimport a.A;\nimport c.C;\n\npublic class X {}",
                "import b.B;\nimport a.A;\n// stop\nimport d.D;\nimport c.C;\n",
        ]
    }

    void "line containing 'import' as data is not touched"() {
        given: 'a string literal mentioning import has leading whitespace; not a real import'
        def input = '    String s = "import a.B;";\nimport b.B;\nimport a.A;\n'
        def expected = '    String s = "import a.B;";\nimport a.A;\nimport b.B;\n'

        expect:
        ImportSort.sort(input) == expected
    }

    void "composes with diff whitespace tolerance: extra spaces in one side still equal after sort"() {
        given: 'actual sorts imports, expected has reordered + extra internal whitespace'
        def actual = "import a.A;\nimport b.B;\nclass X {}"
        def expected = "import  b.B;\nimport  a.A;\nclass X {}"

        when:
        def diff = Diff.compare(ImportSort.sort(actual), ImportSort.sort(expected))

        then: 'whitespace differences absorbed by the diff after sort lines up the order'
        diff == ComparisonResult.match()
    }

    void "composes with diff blank-line tolerance: blanks within import block disappear, blanks outside ignored"() {
        given:
        def actual = "import a.A;\n\nimport b.B;\nclass X {}"
        def expected = "import b.B;\nimport a.A;\nclass X {}"

        when:
        def diff = Diff.compare(ImportSort.sort(actual), ImportSort.sort(expected))

        then:
        diff == ComparisonResult.match()
    }

    void "differing imports still report a diff — pure reorder is what matches, not added/removed"() {
        given: 'expected has one more import than actual'
        def actual = "import a.A;\nclass X {}"
        def expected = "import a.A;\nimport b.B;\nclass X {}"

        when:
        def diff = Diff.compare(ImportSort.sort(actual), ImportSort.sort(expected))

        then: 'the missing import surfaces; reorder logic does not paper over real differences'
        diff instanceof ComparisonResult.Mismatch
        ((ComparisonResult.Mismatch) diff).changes()*.text().contains("import b.B;")
    }
}
