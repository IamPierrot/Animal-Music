package dev.pierrot

import dev.pierrot.handlers.GuildMusicManager
import dev.pierrot.listeners.JDAListener
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.GuildVoiceState
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

fun getLogger(name: String): Logger {
    return LoggerFactory.getLogger(name)
}

fun getLogger(clazz: Class<*>): Logger {
    return LoggerFactory.getLogger(clazz)
}

fun getOrCreateMusicManager(guildId: String, metadata: MessageChannelUnion? = null): GuildMusicManager {
    synchronized(JDAListener::class.java) {
        val guildMusicManager = App.ServiceLocator.musicManagerStrategy.getMusicManager(guildId)

        if (guildMusicManager.metadata?.id !== metadata?.id) {
            guildMusicManager.metadata = metadata
        }
        return guildMusicManager
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun setTimeout(block: () -> Any, delayMillis: Long) {
    try {
        GlobalScope.launch {
            delay(delayMillis)
            block()
        }
    } catch (err: Exception) {
        getLogger("Error").error(err.message)
    }
}

fun joinHelper(event: MessageReceivedEvent) {
    val member = checkNotNull(event.member)
    val memberVoiceState = checkNotNull(member.voiceState)

    if (memberVoiceState.inAudioChannel()) {
        Objects.requireNonNull(memberVoiceState.channel)?.let { event.jda.directAudioController.connect(it) }
    }

    getOrCreateMusicManager(member.guild.id, event.channel)
}

fun String.toCapital(): String {
    if (this.isEmpty()) {
        return this
    }
    return this.substring(0, 1).uppercase(Locale.getDefault()) + this.substring(1)
}

fun isNotSameVoice(user1: GuildVoiceState?, user2: GuildVoiceState?, message: Message): Boolean {
    if (user1 == null || user2 == null) return true

    user1.channel ?: run {
        message.replyEmbeds(
            EmbedBuilder()
                .setAuthor("❌ | Bạn cần vào voice để thực hiện lệnh này!")
                .build()
        ).queue()
        return true
    }

    if (user2.channel != null && user1.channel?.idLong != user2.channel?.idLong) {
        message.replyEmbeds(
            EmbedBuilder()
                .setAuthor("❌ | Bạn không có ở cùng voice với tui~~")
                .build()
        ).queue()
        return true
    }

    return false
}

fun tempReply(context: Message, message: Any, delayMillis: Long = 20000) {
    val sentMessage: Message = when (message) {
        is String -> context.reply(message).complete()
        is MessageEmbed -> context.replyEmbeds(message).complete()
        else -> throw IllegalArgumentException("Unsupported message type: Must be String or MessageEmbed")
    }

    setTimeout({
        sentMessage.delete().queue()
    }, delayMillis)
}

enum class LoopMode {
    NONE,
    TRACK,
    QUEUE
}