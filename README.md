# Arend plugin for IntelliJ IDEA

![](https://arend-lang.github.io/assets/images/Logo_byJB.svg)

[![JetBrains incubator project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![Actions Status](https://github.com/JetBrains/intellij-arend/workflows/check/badge.svg)](https://github.com/JetBrains/intellij-arend/actions)
[![Downloads][d-svg]][jb-url]
[![Version][v-svg]][jb-url]

 [d-svg]: https://img.shields.io/jetbrains/plugin/d/11162.svg
 [v-svg]: https://img.shields.io/jetbrains/plugin/v/11162.svg
 [jb-url]: https://plugins.jetbrains.com/plugin/11162

Plugin that implements [Arend](https://github.com/JetBrains/Arend) support
in [IntelliJ IDEA](https://www.jetbrains.com/idea/) and other IntelliJ-based products.
Arend is a theorem prover based on [Homotopy Type Theory](https://ncatlab.org/nlab/show/homotopy+type+theory).
Visit [arend-lang.github.io](https://arend-lang.github.io/) for more information about the Arend language.

## Clone

```
git clone https://github.com/JetBrains/Arend
git clone https://github.com/JetBrains/intellij-arend.git
cd intellij-arend
```

## Building

We use gradle to build the plugin. It comes with a wrapper script (`gradlew` or `gradlew.bat` in
the root of the repository) which downloads appropriate version of gradle
automatically as long as you have JDK (version >= 11) installed.

Common tasks are

  - `./gradlew buildPlugin` — fully build plugin and create an archive at
    `build/distributions` which can be installed into IntelliJ IDEA via `Install
    plugin from disk` action found in **File | Settings | Plugins**.

  - `./gradlew runIde` — run a development IDE with the plugin installed.

  - `./gradlew test` — run all tests.

## Developing

You can get the latest IntelliJ IDEA Community Edition
[here](https://www.jetbrains.com/idea/download/).

To import this project in IntelliJ, use **File | New | Project from Existing Sources**
and select the root directory of the plugin source code.

When hacking on the plugin, you may need the following plugins -

* **[Grammar-Kit](https://plugins.jetbrains.com/plugin/6606-grammar-kit)** -
BNF Grammars and JFlex lexers editor. Readable parser/PSI code generator.
* **[PsiViewer](https://plugins.jetbrains.com/plugin/227-psiviewer)** -
A Program Structure Interface (PSI) tree viewer.
