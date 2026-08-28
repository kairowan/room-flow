import java.io.File
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtPsiFactory

fun main(args: Array<String>) {
    val disposable = Disposer.newDisposable()
    try {
        val environment = KotlinCoreEnvironment.createForProduction(disposable, CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES)
        val factory = KtPsiFactory(environment.project)
        fun violations(name: String, text: String): List<String> {
            val file = factory.createFile(name, text)
            val errors = mutableListOf<String>()
            if (PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java) != null) errors += "Kotlin 语法错误"
            val types = PsiTreeUtil.findChildrenOfType(file, KtClassOrObject::class.java).filter {
                it.name != null && it !is KtEnumEntry && !(it is KtObjectDeclaration && it.isCompanion())
            }
            val top = types.filter { it.parent == file }
            if (top.size > 1) errors += "一个文件只能有一个具名类型"
            if (top.singleOrNull()?.name?.let { "$it.kt" != name } == true) errors += "文件名必须与类型名一致"
            for (type in types.filter { it.parent != file }) {
                val owner = PsiTreeUtil.getParentOfType(type, KtClassOrObject::class.java)
                // 唯一例外：纯字段模型直接嵌套 data class，不接受函数/初始化块/接口等业务声明。
                val pureOwner = owner is KtClass && !owner.isInterface() && !owner.isEnum() &&
                    owner.declarations.all { it is KtClass && it.isData() } &&
                    owner.primaryConstructorParameters.all { it.hasValOrVar() }
                if (type !is KtClass || !type.isData() || !pureOwner || type.parent != owner?.body) {
                    errors += "禁止嵌套或局部具名类型 ${type.name}"
                }
            }
            return errors
        }
        check(violations("A.kt", "class A\ninterface B").isNotEmpty())
        check(violations("A.kt", "class A { class B }").isNotEmpty())
        check(violations("A.kt", "class A { fun f() { class B } }").isNotEmpty())
        check(violations("A.kt", "class A { interface B }").isNotEmpty())
        check(violations("A.kt", "class A { fun f() = 1; data class B(val n: Int) }").isNotEmpty())
        check(violations("A.kt", "class A { data class B(val n: Int) }").isEmpty())
        check(violations("A.kt", "class A { companion object; val callback = object : Runnable { override fun run() {} } }").isEmpty())
        check(violations("A.kt", "// class Fake\nclass A { val s = \"class Fake\" }").isEmpty())
        check(violations("Wrong.kt", "class A").isNotEmpty())
        val root = File(args.single())
        val sources = listOf("room-flow", "room-flow-debug", "room-flow-compiler", "app", "verification/consumer", "verification/legacy-fixture", "verification/typed-failures").flatMap { module ->
            File(root, "$module/src").walkTopDown().filter { it.isFile && it.extension == "kt" && !it.name.startsWith("._") }.toList()
        }
        val errors = sources.flatMap { file -> violations(file.name, file.readText()).map { "${file.relativeTo(root)}: $it" } }
        check(errors.isEmpty()) { errors.joinToString("\n") }
        println("Kotlin type rules: ${sources.size} files and 9 self-checks passed")
    } finally { Disposer.dispose(disposable) }
}
