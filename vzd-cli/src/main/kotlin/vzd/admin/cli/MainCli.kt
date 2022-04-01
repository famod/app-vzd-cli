package vzd.admin.cli

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import io.github.cdimascio.dotenv.Dotenv
import io.ktor.client.network.sockets.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import mu.KotlinLogging
import vzd.admin.client.*
import java.util.*

private val logger = KotlinLogging.logger {}
private val JSON = Json { prettyPrint = true }

/**
 * Must love Kotlin - create a simple try / catch function and use in all classes that throws these exceptions
 */
fun catching(throwingBlock: () -> Unit = {}) {
    try {
        throwingBlock()
    } catch (e: VZDResponseException) {
        throw CliktError(e.details)
    } catch (e: SerializationException) {
        throw CliktError(e.message)
    } catch (e: VaultException) {
        throw CliktError(e.message)
    } catch (e: ConnectTimeoutException) {
        throw CliktError("${e.message}. Try using proxy: vzd-cli admin -x ...")
    } catch (e: IllegalStateException) {
        // dirty, but no other way atm
        if (e.message?.contains("Unsupported byte code, first byte is 0xfc") == true) {
            throw CliktError("ACCESS_TOKEN is invalid. Please login again using `vzd-cli admin login`.")
        } else {
            throw e
        }
    }
}

enum class OutputFormat {
    HUMAN, JSON, YAML, CSV, SHORT
}

class CommandContext(
    private val clientDelegate: () -> Client,
    val outputFormat: OutputFormat,
    val env: String,
    val useProxy: Boolean,
    var firstCommand: Boolean = true,
) {

    val client by lazy {
        clientDelegate.invoke()
    }

}

class DirectoryAdministrationCli :
    CliktCommand(name = "admin", allowMultipleSubcommands = true, help = """CLI for DirectoryAdministration API""".trimMargin()) {
    private val dotenv by requireObject<Dotenv>()
    private val outputFormat by option().switch(
        "--human" to OutputFormat.HUMAN,
        "--json" to OutputFormat.JSON,
        "--yaml" to OutputFormat.YAML,
        "--csv" to OutputFormat.CSV,
        "--short" to OutputFormat.SHORT,
    ).default(OutputFormat.HUMAN)

    private val env by option(
        "-e", "--env",
        help="Environment. Either tu, ru or pu. If not specified default env is used.")
        .choice("tu", "ru", "pu")

    private val useProxy: Boolean? by option(
        "--proxy-on", "-x", help="Forces the use of the proxy, overrides the configuration")
        .flag("--proxy-off", "-X")

    override fun run() = catching {
        val provider = FileConfigProvider()
        val clientEnv =
            env ?: provider.config.currentEnvironment ?: throw CliktError("Default environment is not configured")

        logger.info { "Using environment: $clientEnv" }

        val clientDelegate: () -> Client = {
            val envcfg =
                provider.config.environment(clientEnv) ?: throw CliktError("Default environment not configired: $env")

            var clientApiURL = envcfg.apiURL
            var clientAccessToken = provider.config.tokens?.get(clientEnv)?.accessToken
            var clientHttpProxyURL = provider.config.httpProxy?.proxyURL
            var clientHttpProxyEnabled = provider.config.httpProxy?.enabled

            dotenv.get("ADMIN_API_URL", null)?.let {
                logger.error { "ADMIN_API_URL is deprecated, use admin config instead" }
                clientApiURL = it
            }
            dotenv.get("HTTP_PROXY_URL", null)?.let {
                logger.error { "HTTP_PROXY_URL is deprecated, use 'vzd-cli admin config' instead" }
                clientHttpProxyURL = it
                clientHttpProxyEnabled = true
            }
            if (useProxy == true) {
                clientHttpProxyEnabled = true
            }
            dotenv.get("ADMIN_ACCESS_TOKEN", null)?.let {
                logger.error { "ADMIN_ACCESS_TOKEN is deprecated, use 'vzd-cli admin config' instead" }
                clientAccessToken = it
            }

            if (clientAccessToken == null) {
                dotenv.get("ADMIN_CLIENT_SECRET", null)?.let {
                    logger.error { "ADMIN_CLIENT_SECRET is deprecated, use 'vzd-cli admin vault' instead" }
                    val auth = ClientCredentialsAuthenticator(
                        dotenv["ADMIN_AUTH_URL"] ?: throw UsageError("Environment variable ADMIN_AUTH_URL is not set"),
                        dotenv.get("HTTP_PROXY_URL", null)
                    )
                    val authResponse = auth.authenticate(
                        dotenv["ADMIN_CLIENT_ID"]
                            ?: throw UsageError("Environment variable ADMIN_CLIENT_ID is not set"),
                        dotenv["ADMIN_CLIENT_SECRET"]
                    )
                    clientAccessToken = authResponse.accessToken
                }
            }

            if (clientAccessToken == null) {
                throw CliktError("You are not logged in. Use 'vzd-cli admin -e $clientEnv login'")
            }

            Client {
                apiURL = clientApiURL
                accessToken = clientAccessToken!!
                if (clientHttpProxyEnabled == true) {
                    httpProxyURL = clientHttpProxyURL
                }
            }
        }

        currentContext.obj = CommandContext(clientDelegate, outputFormat, clientEnv, useProxy == true)
    }

    init {
        subcommands(VaultCommand(), ConfigCommand(),
            LoginCommand(), AuthenticateAdmin(),
            Info(), ListCommand(), TempolateCommand(), AddBaseCommand(),
            LoadBaseCommand(), ModifyBaseDirectoryEntry(), ModifyBaseAttrCommand(), DeleteCommand(),
            ListCertCommand(), AddCertCommand(), SaveCertCommand(), DeleteCertCommand(), ClearCertCommand(), CertInfoCommand())
    }

}

class AuthenticateAdmin : CliktCommand(name = "auth", help = "Show current access token") {
    private val context by requireObject<CommandContext>()

    override fun run() = catching {
        echo(context.client.accessToken)
    }
}

class Info : CliktCommand(name = "info", help = "Show information about the API") {
    private val context by requireObject<CommandContext>()

    override fun run() = catching {
        val info = runBlocking { context.client.getInfo() }
        when (context.outputFormat) {
            OutputFormat.JSON -> Output.printJson(info)
            OutputFormat.HUMAN, OutputFormat.YAML -> Output.printYaml(info)
            else -> throw UsageError("Info is not available for format: ${context.outputFormat}")
        }
    }

}

class LoginCommand: CliktCommand(name = "login", help = "Login to OAuth2 Server and store token(s)") {
    private val context by requireObject<CommandContext>()
    private val password by option("--password", "-p", help="Password for protection of the Vault")
        .prompt("Enter Vault Password", hideInput = true)

    override fun run() = catching {
        val provider = FileConfigProvider()
        val envcfg = provider.config.environment(context.env) ?: throw CliktError("Environment is not configured: ${context.env}")
        val vaultProvider = KeyStoreVaultProvider()
        if (!vaultProvider.exists()) throw CliktError("Vault is not initialized. See vzd-cli admin vault --help")
        val vault = vaultProvider.open(password)
        val auth = ClientCredentialsAuthenticator(envcfg.authURL,
            if (provider.config.httpProxy?.enabled == true || context.useProxy) provider.config.httpProxy?.proxyURL else null)
        val secret = vault.get(context.env) ?: throw CliktError("Secret for env '${context.env}' not found in Vault:")
        val authResponse = auth.authenticate(secret.clientID, secret.secret)
        val tokens = provider.config.tokens ?: emptyMap()
        provider.config.tokens = tokens + mapOf(context.env to TokenConfig(accessToken = authResponse.accessToken))
        provider.save()
        // show token as pretty json
        val tokenParts = authResponse.accessToken.split(".")
        val tokenBody = String(Base64.getUrlDecoder().decode(tokenParts[1]), Charsets.UTF_8)
        val j: JsonObject = Json.decodeFromString(tokenBody)
        echo(JSON.encodeToString(j))
    }
}