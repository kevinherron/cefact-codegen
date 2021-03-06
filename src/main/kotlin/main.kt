import com.google.common.collect.Maps
import com.squareup.javapoet.*
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText
import org.eclipse.milo.opcua.stack.core.types.structured.EUInformation
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import javax.lang.model.element.Modifier

private const val packageName = "org.eclipse.milo.opcua.sdk.core"

fun main() {
    generateBaseFile()
    generateIntermediateFiles()
    generateMainFile()
}

private fun generateBaseFile() {
    val typeSpecBuilder: TypeSpec.Builder = TypeSpec.classBuilder("CefactEngineeringUnitsBase")

    typeSpecBuilder.addModifiers(Modifier.ABSTRACT)

    // protected static final String CEFACT_NAMESPACE_URI = "http://www.opcfoundation.org/UA/units/un/cefact";
    FieldSpec.builder(
        String::class.java,
        "CEFACT_NAMESPACE_URI",
        Modifier.PROTECTED, Modifier.STATIC, Modifier.FINAL
    ).apply {

        initializer("\"http://www.opcfoundation.org/UA/units/un/cefact\"")

        typeSpecBuilder.addField(build())
    }

    // protected static final Map<Integer, EUInformation> BY_UNIT_ID = Maps.newConcurrentMap();
    FieldSpec.builder(
        ParameterizedTypeName.get(Map::class.java, Integer::class.java, EUInformation::class.java),
        "BY_UNIT_ID",
        Modifier.PROTECTED, Modifier.STATIC, Modifier.FINAL
    ).apply {

        initializer("\$T.newConcurrentMap()", Maps::class.java)

        typeSpecBuilder.addField(build())
    }


    val javaFile: JavaFile = JavaFile.builder(packageName, typeSpecBuilder.build()).build()

    javaFile.writeTo(System.out)
    javaFile.writeTo(File("/Users/kevin/Desktop"))
}

private fun generateIntermediateFiles() {
    val resourceAsStream: InputStream =
        ClassLoader.getSystemResourceAsStream("UNECE_to_OPCUA.csv")!!

    val records: List<CSVRecord> = CSVParser
        .parse(
            resourceAsStream,
            StandardCharsets.UTF_8,
            CSVFormat.DEFAULT
        )
        .records

    val windows: List<List<CSVRecord>> = records.windowed(size = 1000, step = 1000, partialWindows = true)

    windows.forEachIndexed { index: Int, recordList: List<CSVRecord> ->
        val typeSpecBuilder: TypeSpec.Builder = TypeSpec.classBuilder("CefactEngineeringUnits$index")

        typeSpecBuilder.addModifiers(Modifier.ABSTRACT)

        val superclassName: String = if (index + 1 == windows.size) {
            "CefactEngineeringUnitsBase"
        } else {
            "CefactEngineeringUnits${index + 1}"
        }

        typeSpecBuilder.superclass(ClassName.get(packageName, superclassName))

        val byUnitId = mutableMapOf<Int, String>()

        recordList.forEach { record: CSVRecord ->
            val fieldName = "CODE_${record[0]}".trim()
            val unitId: Int = record[1].toInt()
            val displayName: String = record[2]
                .removeSurrounding("\"")
                .replace("\"", "\\\"")
                .trim()
            val description: String = record[3]
                .removeSurrounding("\"")
                .replace("\"", "\\\"")
                .trim()

            FieldSpec.builder(
                EUInformation::class.java,
                fieldName,
                Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL
            ).apply {

                val initializerBlock = CodeBlock.builder().add(
                    """
                    new ${'$'}T(CEFACT_NAMESPACE_URI, $unitId, ${'$'}T.english("$displayName"), ${'$'}T.english("$description"))
                    """.trimIndent(),
                    EUInformation::class.java,
                    LocalizedText::class.java,
                    LocalizedText::class.java
                ).build()

                initializer(initializerBlock)

                typeSpecBuilder.addField(build())
            }

            byUnitId[unitId] = fieldName
        }

        CodeBlock.builder().apply {
            // BY_UNIT_ID.put(4405297, Radian);
            byUnitId.forEach { (unitId, fieldName) ->
                addStatement("BY_UNIT_ID.put($unitId, $fieldName)")
            }

            typeSpecBuilder.addStaticBlock(build())
        }

        val javaFile: JavaFile = JavaFile.builder(packageName, typeSpecBuilder.build()).build()

        javaFile.writeTo(System.out)
        javaFile.writeTo(File("/Users/kevin/Desktop"))
    }
}

private fun generateMainFile() {
    val typeSpecBuilder: TypeSpec.Builder = TypeSpec.classBuilder("CefactEngineeringUnits")

    typeSpecBuilder.addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)

    typeSpecBuilder.superclass(ClassName.get(packageName, "CefactEngineeringUnits0"))

    MethodSpec.constructorBuilder().apply {
        addModifiers(Modifier.PRIVATE)

        typeSpecBuilder.addMethod(build())
    }

    /*
    public static EUInformation[] getAll() {
        return BY_UNIT_ID.values()
            .toArray(new EUInformation[0]);
    }
    */
    MethodSpec.methodBuilder("getAll").apply {
        addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        returns(Array<EUInformation>::class.java)

        addStatement(
            """
             return BY_UNIT_ID.values()
                .toArray(new EUInformation[0])
            """.trimIndent()
        )

        typeSpecBuilder.addMethod(build())
    }

    /*
    public static EUInformation getByUnitId(int unitId) {
        return BY_UNIT_ID.get(unitId);
    }
    */
    MethodSpec.methodBuilder("getByUnitId").apply {
        addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        addParameter(Int::class.java, "unitId")
        returns(EUInformation::class.java)

        addStatement("return BY_UNIT_ID.get(unitId)")

        typeSpecBuilder.addMethod(build())
    }

    val javaFile: JavaFile = JavaFile.builder(packageName, typeSpecBuilder.build()).build()

    javaFile.writeTo(System.out)
    javaFile.writeTo(File("/Users/kevin/Desktop"))
}
