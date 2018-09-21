# Arend plugin for IntelliJ IDEA

[![JetBrains incubator project](http://jb.gg/badges/incubator.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Build Status][travis-build-status-svg]][travis-build-status] 

Plugin that implements [Arend](https://github.com/JetBrains/Arend) support 
in [IntelliJ IDEA](http://www.jetbrains.com/idea/) and other IntelliJ-based products.

## Clone

```
git clone https://github.com/JetBrains/Arend
git clone https://github.com/JetBrains/intellij-arend.git
cd intellij-arend
```

## Building

We use gradle to build the plugin. It comes with a wrapper script (`gradlew` or `gradlew.bat` in
the root of the repository) which downloads appropriate version of gradle
automatically as long as you have JDK installed.

Common tasks are

  - `./gradlew buildPlugin` — fully build plugin and create an archive at
    `build/distributions` which can be installed into IntelliJ IDEA via `Install
    plugin from disk` action found in **File | Settings | Plugins**.

  - `./gradlew runIde` — run a development IDE with the plugin installed.

  - `./gradlew test` — run all tests.

## Developing

You can get the latest Intellij IDEA Community Edition
[here](https://www.jetbrains.com/idea/download/).

To import this project in IntelliJ, use **File | New | Project from Existing Sources**
and select `build.gradle` from the root directory of the plugin.

When hacking on the plugin, you may need the following plugins -

* **[Grammar-Kit](https://plugins.jetbrains.com/plugin/6606-grammar-kit)** - 
BNF Grammars and JFlex lexers editor. Readable parser/PSI code generator.
* **[PsiViewer](https://plugins.jetbrains.com/plugin/227-psiviewer)** - 
A Program Structure Interface (PSI) tree viewer.

## Travis CI

The project is configured to build and run tests with Travis CI, which you can enable in your forks.

<!-- Badges -->
[travis-build-status]: https://travis-ci.org/JetBrains/intellij-arend?branch=dev
[travis-build-status-svg]: https://travis-ci.org/JetBrains/intellij-arend.svg?branch=dev
