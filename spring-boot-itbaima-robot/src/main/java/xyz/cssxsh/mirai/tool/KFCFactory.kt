package xyz.cssxsh.mirai.tool

import kotlinx.coroutines.*
import net.mamoe.mirai.internal.spi.*
import net.mamoe.mirai.internal.utils.*
import net.mamoe.mirai.utils.*
import java.net.ConnectException
import java.net.URL

class KFCFactory : EncryptService.Factory {

    companion object {
        @JvmStatic
        internal var cola: Cola? = null;

        @JvmStatic
        internal var workDir: String = ""

        @JvmStatic
        internal val logger: MiraiLogger = MiraiLogger.Factory.create(KFCFactory::class)

        @JvmStatic
        fun install() {
            Services.register(
                EncryptService.Factory::class.qualifiedName!!,
                KFCFactory::class.qualifiedName!!,
                ::KFCFactory
            )
        }

        @JvmStatic
        internal val created: MutableSet<Long> = java.util.concurrent.ConcurrentHashMap.newKeySet()

        @JvmStatic
        fun initConfiguration(path: String, config: Cola) {
            workDir = "$path/"
            cola = config
        }
    }

    override fun createForBot(context: EncryptServiceContext, serviceSubScope: CoroutineScope): EncryptService {
        if (created.add(context.id).not()) {
            throw UnsupportedOperationException("repeated create EncryptService")
        }
        serviceSubScope.coroutineContext.job.invokeOnCompletion {
            created.remove(context.id)
        }
        try {
            org.asynchttpclient.Dsl.config()
        } catch (cause: NoClassDefFoundError) {
            throw RuntimeException("请参照 https://search.maven.org/artifact/org.asynchttpclient/async-http-client/2.12.3/jar 添加依赖", cause)
        }
        return when (val protocol = context.extraArgs[EncryptServiceContext.KEY_BOT_PROTOCOL]) {
            BotConfiguration.MiraiProtocol.ANDROID_PHONE, BotConfiguration.MiraiProtocol.ANDROID_PAD -> {
                @Suppress("INVISIBLE_MEMBER")
                val version = MiraiProtocolInternal[protocol].ver
                val server = cola!!
                when (val type = server.type.ifEmpty { throw IllegalArgumentException("need server type") }) {
                    "fuqiuluo/unidbg-fetch-qsign", "fuqiuluo", "unidbg-fetch-qsign" -> {
                        try {
                            val about = URL(server.base).readText()
                            logger.info("unidbg-fetch-qsign by ${server.base} about " + about.replace("\n", "").replace(" ", ""))
                            when {
                                "version" !in about -> {
                                    // 低于等于 1.1.3 的的版本 requestToken 不工作
                                    System.setProperty(UnidbgFetchQsign.REQUEST_TOKEN_INTERVAL, "0")
                                    logger.warning("请更新 unidbg-fetch-qsign")
                                }
                                version !in about -> {
                                    throw IllegalStateException("unidbg-fetch-qsign by ${server.base} 的版本与 ${protocol}(${version}) 似乎不匹配")
                                }
                            }
                        } catch (cause: ConnectException) {
                            throw RuntimeException("请检查 unidbg-fetch-qsign by ${server.base} 的可用性", cause)
                        } catch (cause: java.io.FileNotFoundException) {
                            throw RuntimeException("请检查 unidbg-fetch-qsign by ${server.base} 的可用性", cause)
                        }
                        UnidbgFetchQsign(
                            server = server.base,
                            key = server.key,
                            coroutineContext = serviceSubScope.coroutineContext
                        )
                    }
                    "kiliokuara/magic-signer-guide", "kiliokuara", "magic-signer-guide", "vivo50" -> {
                        try {
                            val about = URL(server.base).readText()
                            logger.info("magic-signer-guide by ${server.base} about \n" + about)
                            when {
                                "void" == about.trim() -> {
                                    logger.warning("请更新 magic-signer-guide 的 docker 镜像")
                                }
                                version !in about -> {
                                    throw IllegalStateException("magic-signer-guide by ${server.base} 与 ${protocol}(${version}) 似乎不匹配")
                                }
                            }
                        } catch (cause: ConnectException) {
                            throw RuntimeException("请检查 magic-signer-guide by ${server.base} 的可用性", cause)
                        } catch (cause: java.io.FileNotFoundException) {
                            throw RuntimeException("请检查 unidbg-fetch-qsign by ${server.base} 的可用性", cause)
                        }
                        ViVo50(
                            server = server.base,
                            serverIdentityKey = server.serverIdentityKey,
                            authorizationKey = server.authorizationKey,
                            coroutineContext = serviceSubScope.coroutineContext
                        )
                    }
                    "TLV544Provider" -> TLV544Provider()
                    else -> throw UnsupportedOperationException(type)
                }
            }
            BotConfiguration.MiraiProtocol.ANDROID_WATCH -> throw UnsupportedOperationException(protocol.name)
            BotConfiguration.MiraiProtocol.IPAD, BotConfiguration.MiraiProtocol.MACOS -> {
                logger.error("$protocol 尚不支持签名服务，大概率登录失败")
                TLV544Provider()
            }
        }
    }

    override fun toString(): String {
        return "KFCFactory(config=${cola})"
    }
}
