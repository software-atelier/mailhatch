# Contributing

1. Use Java 21 or newer and Maven 3.9 or newer.
2. Create a focused branch and keep public API changes documented.
3. Run `mvn clean verify` and `mvn javadoc:javadoc`.
4. Add integration coverage for protocol or TLS changes.
5. Do not add business-specific handler behavior to the core library.

Commits should be small and explain intent. By contributing, you agree that your changes
are licensed under Apache License 2.0.
