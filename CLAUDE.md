# CLAUDE.md

## Do not run Java code

Claude must not run any Java code of any kind in this repo. This includes, but is not limited to:

- Building the project (`mvn`, `./mvnw`, `gradle`, IntelliJ's bundled Maven, or any wrapper)
- Running the OpenAPI generator, directly or through the Maven plugin
- Compiling with `javac`, or running anything with `java` or `jshell`
- Running tests, mock servers, scratch programs, or snippets to check behavior

Reading and editing files is fine, including `pom.xml` and the README. Say plainly that a change is unverified rather than running anything to check it. The user will build and test it themselves.

## Do not read `.env` files

Claude must not read any `.env` file in this repo or anywhere else, in whole or in part, even with the values redacted. This covers `.env`, `.env.build`, `.env.*`, and any similar secrets or environment file. It includes:

- The Read tool, `cat`, `type`, `Get-Content`, `head`, `tail`, `sed`, `awk`, `grep`, `rg`, or any other command that opens the file or prints any of its contents or key names
- Glob or Grep calls aimed at the contents of these files
- Reading the values indirectly, such as printing environment variables that were loaded from them

Listing that a file exists is fine. If the contents matter, ask the user and let them say what is in it. Don't state or guess facts about what a `.env` file contains (for example which environment a key is for) without the user telling you. When the user needs a `.env` file created or changed, give them the exact line or command to run themselves rather than opening the file.
