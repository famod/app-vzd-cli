package vzd.admin.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.associate
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.pair
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.bouncycastle.util.encoders.Base64
import vzd.admin.client.toCertificateInfo
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.useLines
import kotlin.io.path.writeBytes

private val logger = KotlinLogging.logger {}


class SaveCertCommand : CliktCommand(name = "save-cert", help = "Saves certificate to DER files") {
    private val paramFile: Pair<String, String>? by option("-f", "--param-file",
        help = "Read parameter values from file", metavar = "PARAM FILENAME").pair()
    private val params: Map<String, String> by option("-p", "--param",
        help = "Specify query parameters to find matching entries").associate()
    private val outputDir by option("-o", "--output-dir", metavar = "OUTPUT_DIR", help = "Output directory for certificate files")
        .path(mustExist = true, canBeFile = false)
    private val context by requireObject<CommandContext>()

    override fun run() = catching {
        paramFile?.let { paramFile ->
            val file = Path(paramFile.second)
            if (!file.exists()) throw CliktError("File not found: ${paramFile.second}")
            file.useLines { line ->
                line.forEach {
                    runQuery(params + Pair(paramFile.first, it))
                }
            }
        } ?: run {
            runQuery(params)
        }
    }

    private fun runQuery(params: Map<String, String>) {
        if (params.isEmpty()) {
            throw UsageError("Specify at least one query parameter")
        }

        val result = runBlocking { context.client.readDirectoryCertificates(params) }

        result?.forEach {
            val cert = it.userCertificate?.toCertificateInfo() ?: return
            val filename = "${cert.admissionStatement.registrationNumber}-${cert.serialNumber}.der"
            val path = outputDir?.resolve(filename) ?: return
            logger.info { "Writing certificate to file ${path.toRealPath()}" }
            path.writeBytes(Base64.decode(it.userCertificate?.base64String))
        }
    }
}