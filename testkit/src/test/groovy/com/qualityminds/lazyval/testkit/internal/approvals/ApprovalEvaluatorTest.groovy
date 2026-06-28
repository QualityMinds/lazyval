package com.qualityminds.lazyval.testkit.internal.approvals

import com.qualityminds.lazyval.testkit.ApprovalDefinition
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Unit tests for the toolchain-agnostic core of the approval flow.
 *
 * No annotation processor needed; generated files are written directly into a {@code @TempDir} and the
 * evaluator is exercised against real files, real ApprovalDefinitions and the real diff engine. The
 * integration tests in the processor and ksp modules cover the toolchain-driven end of the pipeline.
 */
class ApprovalEvaluatorTest extends Specification {

    @TempDir
    Path tempDir

    private Map<String, Path> writeFiles(Map<String, String> filesByRelativePath) {
        def map = new LinkedHashMap<String, Path>()
        filesByRelativePath.each { rel, content ->
            def file = tempDir.resolve(rel.replace('/', File.separator))
            Files.createDirectories(file.parent)
            Files.writeString(file, content)
            map.put(rel, file)
        }
        return map
    }

    private static <T extends ApprovalEvaluator.Failure> List<T> failuresOf(
            ApprovalEvaluator.Outcome.Mismatch mismatch, Class<T> type) {
        mismatch.failures().findAll(type::isInstance).collect { type.cast(it) }
    }

    void "approved when every file matches its expected content and no extras"() {
        given:
        def content = "package x;\npublic class X {}\n"
        def files = writeFiles(["pkg/X.java": content])
        def approval = ApprovalDefinition.JavaSource.of("pkg/X.java", content)

        when:
        def outcome = ApprovalEvaluator.evaluate(files, approval)

        then:
        outcome instanceof ApprovalEvaluator.Outcome.Approved
        def approved = outcome as ApprovalEvaluator.Outcome.Approved
        approved.generatedFiles().toList() == ["pkg/X.java"]
    }

    void "approved generatedFiles is a sorted complete enumeration"() {
        given: 'three files, the approvals reference them in a different order than the sorted order'
        def files = writeFiles([
                "pkg/Z.java": "z",
                "pkg/A.java": "a",
                "pkg/M.java": "m",
        ])

        when:
        def outcome = ApprovalEvaluator.evaluate(files,
                ApprovalDefinition.JavaSource.of("pkg/Z.java", "z"),
                ApprovalDefinition.JavaSource.of("pkg/A.java", "a"),
                ApprovalDefinition.JavaSource.of("pkg/M.java", "m"))

        then: 'always sorted, regardless of insertion order'
        outcome instanceof ApprovalEvaluator.Outcome.Approved
        def approved = outcome as ApprovalEvaluator.Outcome.Approved
        approved.generatedFiles().toList() == ["pkg/A.java", "pkg/M.java", "pkg/Z.java"]
    }

    void "ContentDiffers when file is present but content drifts"() {
        given:
        def files = writeFiles(["pkg/X.java": "package x;\npublic class X { /* actual */ }\n"])
        def approval = ApprovalDefinition.JavaSource.of("pkg/X.java", "package x;\npublic class X { /* expected */ }\n")

        when:
        def outcome = ApprovalEvaluator.evaluate(files, approval)

        then:
        outcome instanceof ApprovalEvaluator.Outcome.Mismatch
        def mismatch = outcome as ApprovalEvaluator.Outcome.Mismatch
        def diffs = failuresOf(mismatch, ApprovalEvaluator.Failure.ContentDiffers)
        diffs.size() == 1

        and: 'rendered diff carries enough of the actual content to be navigable in test logs'
        diffs[0].generatedPath() == "pkg/X.java"
        diffs[0].renderedDiff().contains("actual")
        diffs[0].renderedDiff().contains("expected")
    }

    void "FileNotFound when approval targets a path that was not produced"() {
        given: 'a file is produced under one path, but the approval targets another'
        def files = writeFiles(["pkg/Actual.java": "irrelevant"])
        def approval = ApprovalDefinition.JavaSource.of("pkg/Missing.java", "anything")

        when:
        def outcome = ApprovalEvaluator.evaluate(files, approval)

        then: 'two failures: FileNotFound for the targeted path, and UnexpectedFile for the surplus'
        outcome instanceof ApprovalEvaluator.Outcome.Mismatch
        def mismatch = outcome as ApprovalEvaluator.Outcome.Mismatch

        and:
        def fileNotFound = failuresOf(mismatch, ApprovalEvaluator.Failure.FileNotFound)
        fileNotFound.size() == 1
        fileNotFound[0].expectedPath() == "pkg/Missing.java"
        fileNotFound[0].actualGeneratedPaths().toList() == ["pkg/Actual.java"]

        and:
        def unexpected = failuresOf(mismatch, ApprovalEvaluator.Failure.UnexpectedFile)
        unexpected*.generatedPath() == ["pkg/Actual.java"]
    }

    void "UnexpectedFile when a file is generated outside the approved set"() {
        given: 'two files generated, only one approved'
        def files = writeFiles([
                "pkg/A.java": "a",
                "pkg/B.java": "b",
        ])
        def approval = ApprovalDefinition.JavaSource.of("pkg/A.java", "a")

        when:
        def outcome = ApprovalEvaluator.evaluate(files, approval)

        then: 'the unapproved file surfaces as UnexpectedFile; no other failures'
        outcome instanceof ApprovalEvaluator.Outcome.Mismatch
        def mismatch = outcome as ApprovalEvaluator.Outcome.Mismatch
        mismatch.failures().size() == 1
        def unexpected = failuresOf(mismatch, ApprovalEvaluator.Failure.UnexpectedFile)
        unexpected*.generatedPath() == ["pkg/B.java"]
    }

    void "multiple failures of different kinds are reported together"() {
        given: 'A is content-drifted, C is missing, D is surplus, B is fine'
        def files = writeFiles([
                "pkg/A.java": "actual-A",
                "pkg/B.java": "B",
                "pkg/D.java": "D",
        ])

        when:
        def outcome = ApprovalEvaluator.evaluate(files,
                ApprovalDefinition.JavaSource.of("pkg/A.java", "expected-A"),  // ContentDiffers
                ApprovalDefinition.JavaSource.of("pkg/B.java", "B"),           // matches
                ApprovalDefinition.JavaSource.of("pkg/C.java", "C"))           // FileNotFound

        then:
        outcome instanceof ApprovalEvaluator.Outcome.Mismatch
        def mismatch = outcome as ApprovalEvaluator.Outcome.Mismatch

        and: 'each expected failure shape is present exactly once'
        def diffs = failuresOf(mismatch, ApprovalEvaluator.Failure.ContentDiffers)
        def missing = failuresOf(mismatch, ApprovalEvaluator.Failure.FileNotFound)
        def unexpected = failuresOf(mismatch, ApprovalEvaluator.Failure.UnexpectedFile)
        diffs*.generatedPath() == ["pkg/A.java"]
        missing*.expectedPath() == ["pkg/C.java"]
        unexpected*.generatedPath() == ["pkg/D.java"]
    }

    void "empty approvals list still enforces closed-set: every generated file is unexpected"() {
        given:
        def files = writeFiles(["pkg/X.java": "x"])

        when: 'evaluate with no approvals (the testkit short-circuits before calling here, but the evaluator must still be honest)'
        def outcome = ApprovalEvaluator.evaluate(files)

        then:
        outcome instanceof ApprovalEvaluator.Outcome.Mismatch
        def mismatch = outcome as ApprovalEvaluator.Outcome.Mismatch
        def unexpected = failuresOf(mismatch, ApprovalEvaluator.Failure.UnexpectedFile)
        unexpected*.generatedPath() == ["pkg/X.java"]
    }

    void "no files and no approvals is Approved with empty list"() {
        when:
        def outcome = ApprovalEvaluator.evaluate([:])

        then:
        outcome instanceof ApprovalEvaluator.Outcome.Approved
        def approved = outcome as ApprovalEvaluator.Outcome.Approved
        approved.generatedFiles().isEmpty()
    }

    void "diff tolerances apply: blank-line difference does not cause ContentDiffers"() {
        given: 'actual has an extra trailing blank line; expected has none'
        def files = writeFiles(["pkg/X.java": "package x;\npublic class X {}\n\n"])
        def approval = ApprovalDefinition.JavaSource.of("pkg/X.java", "package x;\npublic class X {}\n")

        when:
        def outcome = ApprovalEvaluator.evaluate(files, approval)

        then: 'the blank-line tolerance in Diff carries through here too'
        outcome instanceof ApprovalEvaluator.Outcome.Approved
    }

    void "Resource variant: matches by generatedPath like sources"() {
        given:
        def content = "key=value\nother=42\n"
        def files = writeFiles(["META-INF/lazyval.properties": content])
        def approval = ApprovalDefinition.Resource.of("META-INF/lazyval.properties", content)

        when:
        def outcome = ApprovalEvaluator.evaluate(files, approval)

        then:
        outcome instanceof ApprovalEvaluator.Outcome.Approved
    }

    void "ServiceLoader variant: derives META-INF/services/{fqn} path from the FQN"() {
        given: 'the file is at the META-INF/services path; the approval is built with just the FQN'
        def content = "com.example.impl.Foo\n"
        def files = writeFiles(["META-INF/services/com.example.MyService": content])
        def approval = ApprovalDefinition.ServiceLoader.of("com.example.MyService", content)

        expect: 'factory derives the right path'
        approval.generatedPath() == "META-INF/services/com.example.MyService"

        when:
        def outcome = ApprovalEvaluator.evaluate(files, approval)

        then:
        outcome instanceof ApprovalEvaluator.Outcome.Approved
    }

    void "JavaSource: reordered imports are absorbed as a match"() {
        given: 'fixture and generated file have the same set of imports, in different order'
        def actual = "package x;\n\nimport b.B;\nimport a.A;\n\nclass X {}"
        def expected = "package x;\n\nimport a.A;\nimport b.B;\n\nclass X {}"
        def files = writeFiles(["pkg/X.java": actual])
        def approval = ApprovalDefinition.JavaSource.of("pkg/X.java", expected)

        when:
        def outcome = ApprovalEvaluator.evaluate(files, approval)

        then:
        outcome instanceof ApprovalEvaluator.Outcome.Approved
    }

    void "KotlinSource: reordered imports are absorbed as a match"() {
        given:
        def actual = "package x\n\nimport b.B\nimport a.A\n\nclass X"
        def expected = "package x\n\nimport a.A\nimport b.B\n\nclass X"
        def files = writeFiles(["pkg/X.kt": actual])
        def approval = ApprovalDefinition.KotlinSource.of("pkg/X.kt", expected)

        when:
        def outcome = ApprovalEvaluator.evaluate(files, approval)

        then:
        outcome instanceof ApprovalEvaluator.Outcome.Approved
    }

    void "Resource: import-like lines are NOT sorted — non-source content stays opaque"() {
        given: 'a .properties file with two lines that happen to look like import statements'
        def actual = "import b=second\nimport a=first\n"
        def expected = "import a=first\nimport b=second\n"
        def files = writeFiles(["META-INF/synthetic.properties": actual])
        def approval = ApprovalDefinition.Resource.of("META-INF/synthetic.properties", expected)

        when:
        def outcome = ApprovalEvaluator.evaluate(files, approval)

        then: 'sort is gated on source variants only; resource content is compared as-is'
        outcome instanceof ApprovalEvaluator.Outcome.Mismatch
    }

    void "ServiceLoader and Resource at the same path are equal-by-lookup"() {
        given: 'a services file generated; user approves via the ServiceLoader convenience factory'
        def content = "com.example.impl.Foo\n"
        def files = writeFiles(["META-INF/services/com.example.MyService": content])

        when: 'either variant resolves to the same generatedPath; the testkit treats them identically'
        def serviceLoader = ApprovalDefinition.ServiceLoader.of("com.example.MyService", content)
        def resource = ApprovalDefinition.Resource.of("META-INF/services/com.example.MyService", content)

        then:
        serviceLoader.generatedPath() == resource.generatedPath()

        and: 'both produce Approved outcomes'
        ApprovalEvaluator.evaluate(files, serviceLoader) instanceof ApprovalEvaluator.Outcome.Approved
        ApprovalEvaluator.evaluate(files, resource) instanceof ApprovalEvaluator.Outcome.Approved
    }
}
