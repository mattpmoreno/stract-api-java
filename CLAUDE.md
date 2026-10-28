# CLAUDE.md

## Do not run Java code

Claude must not run any Java code of any kind in this repo. This includes, but is not limited to:

- Building the project (`mvn`, `./mvnw`, `gradle`, IntelliJ's bundled Maven, or any wrapper)
- Running the OpenAPI generator, directly or through the Maven plugin
- Compiling with `javac`, or running anything with `java` or `jshell`
- Running tests, mock servers, scratch programs, or snippets to check behavior

Reading and editing files is fine, including `pom.xml` and the README. Say plainly that a change is unverified rather than running anything to check it. The user will build and test it themselves.
