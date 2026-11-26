# Contributing to This Project

Thank you for considering contributing to this project! 🎉  
This guide will help you get started and ensure your contributions are productive and welcome.

## 📌 What You Can Contribute

- Bug reports
- Bug fixes
- New features or improvements
- Documentation updates
- Tests or improvements to test coverage
- Discussions and feedback on issues or pull requests

## 🧑‍💻 How to Contribute

1. **Fork the Repository**  
   Create a fork of this repository using the GitHub UI.

1. **Create a Branch**  
   Use a descriptive branch name, e.g., `fix/abc` or `feature/provide-cassandra-support`.

1. **Make Your Changes**  
   Keep commits clear and descriptive. Use [Conventional Commits](https://www.conventionalcommits.org).

1. **Run Tests** (if applicable)  
   Make sure your changes don't break existing tests.

   Run `./mvnw install` (`-T 1C`) or, in case you have [act](https://github.com/nektos/act) installed, `act --action-offline-mode --artifact-server-path $PWD/.artifacts pull_request` which will run the Github-Action pipeline locally (though act will not run the integration tests due to issues with Docker)

1. **Push & Create Pull Request**  
   Push your branch and open a Pull Request against the `main` branch, depending on the project's workflow.

   In your PR description, please include:
    - WHAT was changed
    - WHY it was changed
    - HOW it can be tested (if relevant)

## 📏 Adding or Changing a Rule

The rules Lazyval holds a `@LazyValue` type to are listed in one method per processor, in the order they are
judged: `validate` in
[`LazyvalElementValidator`](processor/src/main/java/com/qualityminds/lazyval/processor/internal/LazyvalElementValidator.java)
for Java and in
[`LazyvalKspElementValidator`](ksp/src/main/kotlin/com/qualityminds/lazyval/ksp/internal/LazyvalKspElementValidator.kt)
for Kotlin. Each block there starts with the section of the [rules page](_documentation/modules/ROOT/pages/rules.adoc)
that explains it. A new rule needs:

1. a step in that method, under the section it belongs to;
2. its wording in `LazyvalElementValidatorMessages` (KSP: `LazyvalKspElementValidatorMessages.kt`);
3. a failing scenario in `ApIT` (KSP: `KspIT`);
4. an explanation on the rules page.

What a rule judges is looked up beforehand and handed to it as data — `PayloadLookup` and `CreatorLookup` say how —
so a rule that needs a new fact gets it added there rather than looked up inside `validate`.

## 🤝 Support & Contact

If you need help or have questions, feel free to open an [issue](https://github.com/QualityMinds/lazyval/issues).

---

Thank you for helping improve this project! 🙌
