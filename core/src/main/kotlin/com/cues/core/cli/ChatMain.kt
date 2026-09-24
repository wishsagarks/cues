package com.cues.core.cli

import com.cues.core.CueService
import com.cues.core.assistant.Conversation
import com.cues.core.drafting.GrammarParser
import com.cues.core.drafting.PairedDevice
import com.cues.core.model.ActionState
import com.cues.core.ports.ActionExecutor
import com.cues.core.ports.ActionOutcome
import com.cues.core.ports.CapabilityProvider
import com.cues.core.ports.Clock
import com.cues.core.store.JsonFileStore
import kotlinx.coroutines.runBlocking
import java.io.File

/** One deterministic Ask Cues turn for phone-friendly Red Light development. */
object ChatMain {
@JvmStatic
fun main(args: Array<String>) {
runBlocking {
    val text = args.joinToString(" ").trim()
    require(text.isNotBlank()) { "Usage: ./dev chat \"ask Cues about a cue\"" }
    val store = JsonFileStore(File("build/cli-chat"))
    val conversation = Conversation().apply {
        currentDraft = store.all().maxByOrNull { it.version }
        lastRoutineId = currentDraft?.id
    }
    val service = CueService(
        routines = store,
        sessions = store,
        receipts = store,
        executor = object : ActionExecutor {
            override fun execute(
                actionId: com.cues.core.model.ActionId,
                args: com.cues.core.model.ActionArgs,
                sessionId: String,
            ) = ActionOutcome(ActionState.BLOCKED, "CLI never executes device actions.")

            override fun release(resource: com.cues.core.model.OwnedResource, sessionId: String) =
                ActionOutcome(ActionState.BLOCKED, "CLI owns no device resources.")
        },
        clock = Clock(System::currentTimeMillis),
        capabilities = CapabilityProvider { emptySet() },
        drafter = GrammarParser(
            listOf(PairedDevice("AA:BB:CC:DD:EE:FF", "TWS Air Pro", setOf("earbuds", "buds", "headphones"))),
        ),
        facts = store,
    )

    val turn = service.converse(conversation, text)
    turn.draft?.let(store::save)
    println(turn.reply.text)
    println("answered from: ${turn.reply.answeredFrom.name.lowercase().replace('_', ' ')}")
    turn.reply.chips.forEach { println("[${it}]") }
    turn.draft?.let { println("draft: ${it.title} (${it.status.name.lowercase()})") }
}
}
}
