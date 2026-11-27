package com.qualityminds.lazyval.testkit.evaluation

import com.qualityminds.lazyval.testkit.Approval
import com.qualityminds.lazyval.testkit.internal.approvals.ApprovalEvaluator
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * AI evaluation finding T10: the approval diff ({@code internal/toolchain/diff/Diff}) applies its
 * tolerances without regard to where they are safe. Whitespace is collapsed inside string literals and
 * text blocks, where it is part of the value, while the documented blank-line tolerance still reports a
 * mismatch once the two sides also differ in their trailing newline.
 * <p>
 * These tests intentionally fail on {@code main}; each feature documents one symptom of the finding.
 * Generated files are written to a {@code @TempDir} and evaluated like the Testkit does after a run.
 */
class ApprovalDiffToleranceSpec extends Specification {

    @TempDir
    Path generatedRoot

    private ApprovalEvaluator.Outcome evaluateJavaSource(String path, String generated, String approved) {
        def file = write(path, generated)
        ApprovalEvaluator.evaluate(
                new ApprovalEvaluator.GeneratedFiles([(path): file], [:], [:]),
                Approval.JavaSource.of(path, approved))
    }

    private ApprovalEvaluator.Outcome evaluateResource(String path, String generated, String approved) {
        def file = write(path, generated)
        ApprovalEvaluator.evaluate(
                new ApprovalEvaluator.GeneratedFiles([:], [:], [(path): file]),
                Approval.Resource.of(path, approved))
    }

    private Path write(String path, String content) {
        def file = generatedRoot.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, content)
        file
    }

    void "T10 whitespace inside a string literal is part of the approved content"() {
        when: 'the generated query has two spaces where the approval has one'
        def outcome = evaluateJavaSource('test/Query.java',
                'package test;\nclass Query { String sql = "SELECT  a FROM t"; }\n',
                'package test;\nclass Query { String sql = "SELECT a FROM t"; }\n')

        then: 'the literal differs, so the approval does not pass'
        outcome instanceof ApprovalEvaluator.Outcome.Mismatch
    }

    void "T10 blank lines and indentation inside a text block are part of the approved content"() {
        when: 'the generated text block has an extra blank line and a deeper indent'
        def outcome = evaluateJavaSource('test/Template.java',
                'package test;\nclass Template { String s = """\n    a\n\n      b\n    """; }\n',
                'package test;\nclass Template { String s = """\n    a\n    b\n    """; }\n')

        then: 'the string value differs, so the approval does not pass'
        outcome instanceof ApprovalEvaluator.Outcome.Mismatch
    }

    void "T10 an extra blank line is tolerated when the trailing newline differs too: #description"() {
        when:
        def outcome = evaluateResource('config/content.txt', generated, approved)

        then: 'extra blank lines on either side are documented as no mismatch'
        outcome instanceof ApprovalEvaluator.Outcome.Approved

        where:
        description                                      | generated | approved
        'extra inner blank, no trailing newline'         | 'a\n\nb'  | 'a\nb\n'
        'extra indented inner blank, no trailing newline' | 'a\n  \nb' | 'a\nb\n'
    }
}
