package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.internal.testing.SimpleCodeGenerator
import com.squareup.anvil.compiler.internal.testing.SimpleSourceFileTrackingBehavior.NO_SOURCE_TRACKING
import com.squareup.anvil.compiler.internal.testing.SimpleSourceFileTrackingBehavior.TRACKING_WITH_NO_SOURCES
import com.squareup.anvil.compiler.internal.testing.SimpleSourceFileTrackingBehavior.TRACK_SOURCE_FILES
import com.squareup.anvil.compiler.internal.testing.simpleCodeGenerator
import com.squareup.anvil.compiler.isError
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import org.junit.Test

class CodeGenerationExtensionTest {

  @Test fun `generated files with the same path and different content are an error`() {
    val codeGenerator = simpleCodeGenerator { clazz ->
      clazz
        .takeIf { it.isInterface() }
        ?.let {
          //language=kotlin
          """
          package generated.com.squareup.test
          
          class Abc
          
          private const val abc = "${clazz.shortName}"
        """
        }
    }

    compile(
      """
      package com.squareup.test

      interface ComponentInterface1
      
      interface ComponentInterface2
      """,
      codeGenerators = listOf(codeGenerator),
    ) {
      assertThat(exitCode).isError()

      val abcPath = outputDirectory
        .resolveSibling("build/anvil/generated/com/squareup/test/Abc.kt")
        .absolutePath

      assertThat(messages).contains(
        """
        There were duplicate generated files. Generating and overwriting the same file leads to unexpected results.

        The file was generated by: ${SimpleCodeGenerator::class}
        The file is: $abcPath
        """.trimIndent(),
      )
    }
  }

  @Test fun `generated files with the same path and same content are allowed`() {
    val codeGenerator = simpleCodeGenerator { clazz ->
      clazz
        .takeIf { it.isInterface() }
        ?.let {
          //language=kotlin
          """
          package generated.com.squareup.test
          
          class Abc
        """
        }
    }

    compile(
      """
      package com.squareup.test

      interface ComponentInterface1
      
      interface ComponentInterface2
      """,
      codeGenerators = listOf(codeGenerator),
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }

  @Test fun `a code generator that is not applicable is never called`() {

    val codeGenerator = simpleCodeGenerator(applicable = { false }) { null }

    //language=kotlin
    val componentInterface = """
      package com.squareup.test

      interface ComponentInterface
    """.trimIndent()

    compile(
      componentInterface,
      codeGenerators = listOf(codeGenerator),
    ) {
      assertThat(exitCode).isEqualTo(OK)
      assertThat(codeGenerator.isApplicableCalls).isEqualTo(1)
      assertThat(codeGenerator.getGenerateCallsForInputFileContent(componentInterface)).isEqualTo(0)
    }
  }

  @Test fun `compiling with no source tracking and trackSourceFiles enabled throws an exception`() {

    val codeGenerator = simpleCodeGenerator(NO_SOURCE_TRACKING) { clazz ->

      if (clazz.shortName == "Abc") {
        return@simpleCodeGenerator null
      }

      """
        package com.squareup.test
          
        class Abc
      """.trimIndent()
    }

    //language=kotlin
    val componentInterface = """
      package com.squareup.test

      interface ComponentInterface
    """.trimIndent()

    compile(
      componentInterface,
      codeGenerators = listOf(codeGenerator),
      trackSourceFiles = true,
    ) {
      assertThat(codeGenerator.isApplicableCalls).isEqualTo(1)
      assertThat(codeGenerator.getGenerateCallsForInputFileContent(componentInterface)).isEqualTo(1)

      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)

      val abcPath = outputDirectory
        .resolveSibling("build/anvil/com/squareup/test/Abc.kt")
        .absolutePath

      assertThat(messages).contains(
        """
        |Source file tracking is enabled but this generated file is not tracking them.
        |Please report this issue to the code generator's maintainers.
        |
        |The file was generated by: ${SimpleCodeGenerator::class}
        |The file is: $abcPath
        |
        |To stop this error, disable the `trackSourceFiles` property in the Anvil Gradle extension:
        |
        |   // build.gradle(.kts)
        |   anvil {
        |     trackSourceFiles = false
        |   }
        |
        |or disable the property in `gradle.properties`:
        |
        |   # gradle.properties
        |   com.squareup.anvil.trackSourceFiles=false
        |
        """.trimMargin(),

      )
    }
  }

  @Test fun `a code generator that tracks an empty source list can use trackSourceFiles`() {

    val codeGenerator = simpleCodeGenerator(TRACKING_WITH_NO_SOURCES) { clazz ->

      if (clazz.shortName == "Abc") {
        return@simpleCodeGenerator null
      }

      """
        package com.squareup.test
          
        class Abc
      """.trimIndent()
    }

    //language=kotlin
    val componentInterface = """
      package com.squareup.test

      interface ComponentInterface
    """.trimIndent()

    compile(
      componentInterface,
      codeGenerators = listOf(codeGenerator),
      trackSourceFiles = true,
    ) {
      assertThat(codeGenerator.isApplicableCalls).isEqualTo(1)
      assertThat(codeGenerator.getGenerateCallsForInputFileContent(componentInterface)).isEqualTo(1)

      assertThat(exitCode).isEqualTo(OK)
      assertThat(classLoader.loadClass("com.squareup.test.Abc")).isNotNull()
    }
  }

  @Test fun `a code generator that tracks an a single source per generated file can use trackSourceFiles`() {

    val codeGenerator = simpleCodeGenerator(TRACK_SOURCE_FILES) { clazz ->

      if (clazz.shortName == "Abc") {
        return@simpleCodeGenerator null
      }

      """
        package com.squareup.test
          
        class Abc
      """.trimIndent()
    }

    //language=kotlin
    val componentInterface = """
      package com.squareup.test

      interface ComponentInterface
    """.trimIndent()

    compile(
      componentInterface,
      codeGenerators = listOf(codeGenerator),
      trackSourceFiles = true,
    ) {
      assertThat(codeGenerator.isApplicableCalls).isEqualTo(1)
      assertThat(codeGenerator.getGenerateCallsForInputFileContent(componentInterface)).isEqualTo(1)

      assertThat(exitCode).isEqualTo(OK)
      assertThat(classLoader.loadClass("com.squareup.test.Abc")).isNotNull()
    }
  }

  @Test
  fun `a code generator that does not track sources can compile with trackSourceFiles disabled`() {
    val codeGenerator = simpleCodeGenerator(NO_SOURCE_TRACKING) { clazz ->

      if (clazz.shortName == "Abc") {
        return@simpleCodeGenerator null
      }

      """
        package com.squareup.test
          
        class Abc
      """.trimIndent()
    }

    //language=kotlin
    val componentInterface = """
      package com.squareup.test

      interface ComponentInterface
    """.trimIndent()

    compile(
      componentInterface,
      codeGenerators = listOf(codeGenerator),
      trackSourceFiles = false,
    ) {
      assertThat(codeGenerator.isApplicableCalls).isEqualTo(1)
      assertThat(codeGenerator.getGenerateCallsForInputFileContent(componentInterface)).isEqualTo(1)

      assertThat(exitCode).isEqualTo(OK)
      assertThat(classLoader.loadClass("com.squareup.test.Abc")).isNotNull()
    }
  }

  @Test fun `errors that require an opt-in annotation are suppressed in generated code`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding
      import javax.inject.Inject

      @Retention(AnnotationRetention.BINARY)
      @RequiresOptIn(
          message = "",
          level = RequiresOptIn.Level.ERROR
      )
      @MustBeDocumented
      annotation class InternalApi

      interface Type

      @InternalApi
      @ContributesBinding(Unit::class)
      class SomeClass @Inject constructor() : Type 
      """,
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }
  }
}
