package de.qualityminds.lazyval.testkit;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

import java.util.Arrays;
import java.util.Objects;

public sealed interface Testresult {

    sealed interface Java extends Testresult {

        record Success(ImmutableList<String> generatedFiles) implements Java {
            public Success {
                Objects.requireNonNull(generatedFiles);
            }

            public Success(String... generatedFiles) {
                this(Lists.immutable.ofAll(Arrays.asList(generatedFiles)));
            }
        }

        record SuccessWithWarnings(ImmutableList<String> generatedFiles,
                                   ImmutableList<String> warnings) implements Java {
            public SuccessWithWarnings {
                Objects.requireNonNull(generatedFiles);
                Objects.requireNonNull(warnings);
                if(warnings.isEmpty()){
                    throw new IllegalArgumentException("Warnings must not be empty");
                }
            }
        }

        record NothingGenerated() implements Java {
        }

        record Failure(ImmutableList<String> errors) implements Java {
            public Failure {
                Objects.requireNonNull(errors);
                if(errors.isEmpty()){
                    throw new IllegalArgumentException("Errors must not be empty");
                }
            }

            public Failure(String error) {
                this(Lists.immutable.of(error));
            }
        }
    }

    sealed interface Kotlin extends Testresult {
        record Success(ImmutableList<String> generatedFiles) implements Kotlin {

            public Success {
                Objects.requireNonNull(generatedFiles);
            }

            public Success(String... generatedFiles) {
                this(Lists.immutable.ofAll(Arrays.asList(generatedFiles)));
            }
        }

        record SuccessWithWarnings(ImmutableList<String> generatedFiles,
                                   ImmutableList<String> warnings) implements Kotlin {
            public SuccessWithWarnings {
                Objects.requireNonNull(generatedFiles);
                Objects.requireNonNull(warnings);
                if(warnings.isEmpty()){
                    throw new IllegalArgumentException("Warnings must not be empty");
                }
            }
        }

        record NothingGenerated() implements Kotlin {
        }

        record Failure(ImmutableList<String> errors) implements Kotlin {
            public Failure {
                Objects.requireNonNull(errors);
                if(errors.isEmpty()){
                    throw new IllegalArgumentException("Errors must not be empty");
                }
            }

            public Failure(String error) {
                this(Lists.immutable.of(error));
            }
        }
    }

}
