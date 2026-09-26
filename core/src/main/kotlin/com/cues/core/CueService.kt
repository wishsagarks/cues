package com.cues.core

import com.cues.core.approval.ArmResult
import com.cues.core.approval.Approvals
import com.cues.core.approval.DeleteResult
import com.cues.core.assistant.AssistantIntent
import com.cues.core.assistant.AssistantReply
import com.cues.core.assistant.ControlKind
import com.cues.core.assistant.Conversation
import com.cues.core.assistant.IntentRouter
import com.cues.core.assistant.OnDeviceRefinePhraser
import com.cues.core.assistant.PendingCommand
import com.cues.core.assistant.Refiner
import com.cues.core.assistant.ReplyChip
import com.cues.core.assistant.ReplyCode
import com.cues.core.assistant.ReplySource
import com.cues.core.assistant.Turn
import com.cues.core.assistant.UnsupportedRoute
import com.cues.core.context.SnapshotBuilder
import com.cues.core.context.PersonalIndex
import com.cues.core.context.ReferenceResolution
import com.cues.core.drafting.ClauseAccounting
import com.cues.core.drafting.DraftResult
import com.cues.core.drafting.ModelDraftGuard
import com.cues.core.model.DraftSourceId
import com.cues.core.drafting.RoutineDrafter
import com.cues.core.model.Capability
import com.cues.core.model.ContextValue
import com.cues.core.model.EventKind
import com.cues.core.model.Routine
import com.cues.core.model.RoutineStatus
import com.cues.core.model.Session
import com.cues.core.model.TriggerEvent
import com.cues.core.model.UnknownReason
import com.cues.core.ports.ActionExecutor
import com.cues.core.ports.CapabilityProvider
import com.cues.core.ports.Clock
import com.cues.core.ports.ReceiptSink
import com.cues.core.ports.RoutineStore
import com.cues.core.ports.SessionStore
import com.cues.core.ports.NamedContextStore
import com.cues.core.ports.PatchStore
import com.cues.core.ports.PlaceStore
import com.cues.core.ports.FactStore
import com.cues.core.ports.Embedder
import com.cues.core.inference.InferenceCost
import com.cues.core.inference.InferenceLedger
import com.cues.core.inference.InferenceLedgerEntry
import com.cues.core.inference.InferenceReport
import com.cues.core.receipt.ReceiptRecord
import com.cues.core.receipt.ReceiptRecords
import com.cues.core.model.EventProvenance
import com.cues.core.session.EngineResult
import com.cues.core.session.SessionEngine
import com.cues.core.signals.SignalRegistry
import com.cues.core.signals.ContextualStores
import com.cues.core.model.Patch
import com.cues.core.model.PatchKind
import com.cues.core.eval.Reason
import com.cues.core.eval.ReasonCode
import com.cues.core.eval.Truth
import java.time.ZoneId
import com.cues.core.coach.LedgerEvent
import com.cues.core.coach.Suggestion
import com.cues.core.coach.UsageLedger

/**
 * The one class Android calls for everything. Android supplies ports and a
 * UI; every decision — what a request means, whether a cue may arm, what an
 * event does — happens on this side of the line.
 *
 * Nothing here talks to a device directly. It orchestrates the pieces that
 * already do: [RoutineDrafter] proposes, [Approvals] gates, [SessionEngine]
 * decides and acts through [ActionExecutor], [SnapshotBuilder] turns raw
 * readings into what the evaluator consumes, and every outcome is written
 * through [ReceiptSink] before it is handed back.
 */
class CueService(
    private val routines: RoutineStore,
    private val sessions: SessionStore,
    private val receipts: ReceiptSink,
    executor: ActionExecutor,
    private val clock: Clock,
    private val capabilities: CapabilityProvider,
    private val drafter: RoutineDrafter,
    private val zoneId: () -> ZoneId = { ZoneId.systemDefault() },
    private val patches: PatchStore? = null,
    contexts: NamedContextStore? = null,
    places: PlaceStore? = null,
    private val intentRouter: IntentRouter = IntentRouter(),
    private val facts: FactStore? = null,
    private val usageLedger: UsageLedger? = null,
    /**
     * Optional semantic ranker for [PersonalIndex], e.g. "my deep work thing"
     * -> the Study cue. `null` — the default until a verified on-device
     * embedding runtime exists — means fuzzy references fall back to
     * [PersonalIndex]'s own lexical matching, exactly as before this field
     * existed.
     */
    private val embedder: Embedder? = null,
    /**
     * Whether someone is at the phone right now. Defaults to "always
     * present" — today's behaviour for every action that predates this
     * parameter — so every existing caller (the CLI, every test, and every
     * `:app` construction site until it supplies a real reading) is
     * unaffected. See [com.cues.core.ports.DeviceAttention] and CLEANUP.md
     * CL-25 for why a real phone needs a real one wired in.
     */
    private val attention: com.cues.core.ports.DeviceAttention = com.cues.core.ports.DeviceAttention { true },
    /**
     * Where structured receipts go, alongside the text ones. Optional, like
     * [usageLedger]: `null` records text receipts only, exactly as before this
     * parameter existed. Nothing that decides behaviour reads from it.
     */
    private val receiptLog: com.cues.core.ports.ReceiptLog? = null,
    /**
     * Where per-call token/cost records go for the Insights tab's usage
     * section, alongside [usageLedger]'s signal-pattern events — a separate
     * concern, its own opt-in ([InferenceLedger.usageTrackingEnabled]).
     * Optional, like [usageLedger]: `null` means no bookkeeping at all,
     * exactly as before this parameter existed.
     */
    private val inferenceLedger: InferenceLedger? = null,
    /**
     * C1: the model-assisted fallback for edit phrasing — reached only when
     * [intentRouter]'s deterministic patterns find no match at all.
     * `null`, the default, means every unmatched edit request stays
     * [AssistantIntent.Unsupported] exactly as before this parameter existed.
     */
    private val refinePhraser: OnDeviceRefinePhraser? = null,
    /**
     * A live read of whether a model-backed drafter is actually worth
     * asking right now — `null`, the default, reports [com.cues.core.ports.ModelAvailability.NOT_INSTALLED]
     * in [diagnostics], which is honest for every caller that predates this
     * parameter (the CLI, most tests): no model was ever configured for
     * them. See CLEANUP.md CL-18: "the drafter label is always ON-DEVICE
     * MODEL" — this is the one place `:app` tells the truth about it.
     */
    private val modelAvailabilityProbe: com.cues.core.ports.ModelAvailabilityProbe? = null,
    /** Whether the caller has opted into cloud assist right now — `null`/absent reports `false`, same discipline as [modelAvailabilityProbe]. */
    private val cloudOptInProbe: (() -> Boolean)? = null,
) {

    private val engine = SessionEngine(sessions, executor, clock, attention = attention, zoneId = zoneId)
    private val _lastTrace = kotlinx.coroutines.flow.MutableStateFlow<com.cues.core.drafting.DraftTrace?>(null)

    /** The most recent draft's full [com.cues.core.drafting.DraftTrace], updated by every [draft] call and by [refinePhraser]'s own calls. Observable so `:app` never has to re-read a snapshot to notice a change (CLEANUP.md CL-18's "diagnostics is read once per composition"). */
    val lastTrace: kotlinx.coroutines.flow.StateFlow<com.cues.core.drafting.DraftTrace?> = _lastTrace

    init {
        contexts?.let { ContextualStores.contexts = it }
        places?.let { ContextualStores.places = it }
    }

    // ------------------------------------------------------------ authoring

    /**
     * Proposes a routine from a spoken or typed request.
     *
     * Stamps the accepted result with which drafter actually produced it —
     * [DraftResult.source] already carries that, this just also puts it on
     * the routine itself so it survives into storage and the review screen.
     * Nothing is persisted yet; a draft is not reviewable until [review] and
     * not armed until [approveAndArm].
     */
    suspend fun draft(text: String): DraftResult {
        val result = drafter.draft(text)
        recordDraftResult(result)
        return when (result) {
            // A model never gets to assert that it understood a clause.
            is DraftResult.Drafted -> ClauseAccounting.stamp(text, result)
            is DraftResult.NeedsClarification, is DraftResult.Failed -> result
        }
    }

    /**
     * The explicit, opt-in "third opinion" (docs/FDD.md's "Optional cloud
     * assist" section) — a cloud-backed [RoutineDrafter] the caller only
     * ever reaches for after the offline pair ([drafter]) has already
     * disagreed or both failed, and only with cloud assist turned on.
     *
     * Runs through the same funnel every other draft does — [ModelDraftGuard],
     * [ClauseAccounting], the ledger via [recordDraftResult] — so a cloud
     * draft that reaches [Turn.draft] is exactly as trusted as any other.
     * Before Sprint 8 this call bypassed `CueService` entirely
     * (`CuesApplication.tryCloudAssist`): a cloud draft never became a
     * ledger entry or a conversation [Turn], and the Insights CLOUD row was
     * always zero regardless of real Sarvam usage (CLEANUP.md CL-18/CL-35).
     *
     * Like [draft], nothing is persisted — the caller takes [Turn.draft] to
     * Review, same as any other proposal.
     */
    suspend fun draftWithCloud(conversation: Conversation, text: String, cloud: RoutineDrafter): Turn {
        val raw = cloud.draft(text)
        val guarded = if (raw is DraftResult.Drafted && ModelDraftGuard.rejects(raw)) {
            DraftResult.Failed(
                raw.source,
                "Cloud assist produced a cue that did not pass independent validation.",
                raw.elapsedMillis,
                raw.inferenceReport,
                raw.trace,
            )
        } else {
            raw
        }
        recordDraftResult(guarded)
        val turn = when (guarded) {
            is DraftResult.Drafted -> {
                val stamped = ClauseAccounting.stamp(text, guarded)
                Turn(
                    text,
                    AssistantIntent.Create(text),
                    AssistantReply(
                        ReplyCode.DRAFT_READY,
                        stamped.inferenceReport?.let { mapOf("backend" to it.backend.name) }.orEmpty(),
                        ReplySource.SARVAM_CLOUD,
                    ),
                    draft = stamped.routine,
                    trace = stamped.trace,
                )
            }
            is DraftResult.NeedsClarification -> Turn(
                text,
                AssistantIntent.Create(text),
                AssistantReply(ReplyCode.NEEDS_CLARIFICATION, mapOf("question" to guarded.question), ReplySource.SARVAM_CLOUD),
                trace = guarded.trace,
            )
            is DraftResult.Failed -> Turn(
                text,
                AssistantIntent.Create(text),
                AssistantReply(ReplyCode.DRAFT_FAILED, mapOf("reason" to guarded.reason), ReplySource.SARVAM_CLOUD),
                trace = guarded.trace,
            )
        }
        turn.draft?.let {
            conversation.currentDraft = it
            conversation.lastRoutineId = it.id
        }
        conversation.turns += turn
        return turn
    }

    /**
     * Applies a coach [Suggestion] straight to its named routine through
     * [Refiner] — never by handing [Suggestion.proposal] back to a drafter
     * as a sentence (CL-27: no drafter has ever been able to parse "make
     * routine-<uuid> 22 minutes" as a request, because it was never one).
     *
     * Returns `null`, refusing rather than guessing, when the suggestion
     * names no routine to edit ([Suggestion.routineId]/[Suggestion.operation]
     * are `null` for [com.cues.core.coach.SuggestionKind.FIX_PERMISSION] and
     * [com.cues.core.coach.SuggestionKind.ADD_SIGNAL_CUE], and for any
     * suggestion whose evidence turned out to span more than one cue) or
     * when that routine has since been deleted.
     *
     * Like [draft], this does not persist anything — the caller takes the
     * returned draft to Review, the same as any other proposal, and nothing
     * is saved or reapproved until the user acts there.
     */
    fun acceptSuggestion(suggestion: Suggestion): Routine? {
        val routineId = suggestion.routineId ?: return null
        val operation = suggestion.operation ?: return null
        val routine = routines.findRoutine(routineId) ?: return null
        return Refiner.apply(routine, operation).routine
    }

    /**
     * Handles one authoring conversation turn.
     *
     * This method can create inert drafts and pending commands, but it never
     * approves a draft or executes a control command. Those remain explicit
     * user actions in Review and confirmation UI respectively.
     */
    suspend fun converse(conversation: Conversation, text: String): Turn {
        val intent = intentRouter.route(text)
        val turn = when (intent) {
            is AssistantIntent.Create -> createTurn(text, intent)
            is AssistantIntent.Refine -> refineTurn(conversation, text, intent)
            is AssistantIntent.Control -> controlTurn(conversation, text, intent)
            is AssistantIntent.Remember -> rememberTurn(text, intent)
            is AssistantIntent.Explain -> explainTurn(conversation, text, intent)
            AssistantIntent.Forecast -> Turn(
                text,
                intent,
                AssistantReply(
                    ReplyCode.FORECAST_READY,
                    mapOf("summary" to "${routines.armed().size} armed cue${if (routines.armed().size == 1) "" else "s"} can be evaluated today."),
                    ReplySource.FORECAST,
                ),
            )
            AssistantIntent.ListCues -> {
                val all = routines.all()
                Turn(
                    text,
                    intent,
                    AssistantReply(
                        ReplyCode.CUES_LIST,
                        mapOf("summary" to all.joinToString(prefix = "Your cues: ", separator = ", ") { it.title }),
                        ReplySource.ROUTINE_STORE,
                    ),
                )
            }
            AssistantIntent.Capabilities -> Turn(
                text,
                intent,
                AssistantReply(ReplyCode.CAPABILITIES, answeredFrom = ReplySource.ROUTINE_STORE),
            )
            is AssistantIntent.Unsupported -> unsupportedTurn(conversation, text, intent)
        }
        turn.draft?.let {
            conversation.currentDraft = it
            conversation.lastRoutineId = it.id
        }
        conversation.turns += turn
        return turn
    }

    private suspend fun createTurn(text: String, intent: AssistantIntent.Create): Turn = when (val result = draft(intent.text)) {
        is DraftResult.Drafted -> Turn(
            text,
            intent,
            AssistantReply(
                ReplyCode.DRAFT_READY,
                // Present only for a model-backed draft, per InferenceReport's
                // own caveat about what "backend" does and does not confirm.
                result.inferenceReport?.let { mapOf("backend" to it.backend.name) }.orEmpty(),
                result.source.replySource(),
            ),
            draft = result.routine,
            trace = result.trace,
        )
        is DraftResult.NeedsClarification -> Turn(
            text,
            intent,
            AssistantReply(
                ReplyCode.NEEDS_CLARIFICATION,
                // "appQuery" is the app-layer's cue to run an installed-app
                // query and show its own picker — the parser has no such list
                // to offer chips from, unlike a paired device.
                buildMap {
                    put("question", result.question)
                    result.appQuery?.let { put("appQuery", it) }
                },
                result.source.replySource(),
                result.deviceCandidates.map { ReplyChip.Choice(it.id, it.label) },
            ),
            trace = result.trace,
        )
        is DraftResult.Failed -> Turn(
            text,
            intent,
            AssistantReply(ReplyCode.DRAFT_FAILED, mapOf("reason" to result.reason), result.source.replySource()),
            trace = result.trace,
        )
    }

    private fun refineTurn(
        conversation: Conversation,
        text: String,
        intent: AssistantIntent.Refine,
    ): Turn {
        val draft = conversation.currentDraft
            ?: return Turn(text, intent, AssistantReply(ReplyCode.NO_DRAFT_TO_REFINE, answeredFrom = ReplySource.PARSER))
        val refined = Refiner.apply(draft, intent.operation).routine
        return Turn(
            text,
            intent,
            AssistantReply(ReplyCode.DRAFT_REFINED, answeredFrom = ReplySource.PARSER),
            draft = refined,
        )
    }

    /**
     * C1: [intentRouter] found no deterministic match at all. A system-agent
     * request is routed as before; anything else gets one more chance — only
     * when there is an active draft to edit and [refinePhraser] is wired —
     * to be restated by the model in [RefineGrammarParser]'s closed
     * vocabulary and applied through [Refiner.apply], the same independent
     * revalidation every other edit already goes through. The model's own
     * words never become the [RefineOperation] directly; only a sentence
     * that parses deterministically does.
     */
    private suspend fun unsupportedTurn(
        conversation: Conversation,
        text: String,
        intent: AssistantIntent.Unsupported,
    ): Turn {
        if (intent.routeTo == UnsupportedRoute.SYSTEM_AGENT) {
            return Turn(
                text,
                intent,
                AssistantReply(
                    ReplyCode.HANDOFF_TO_SYSTEM_AGENT,
                    answeredFrom = ReplySource.PARSER,
                    chips = listOf(ReplyChip.Handoff()),
                ),
            )
        }
        val draft = conversation.currentDraft
        if (draft != null && refinePhraser != null) {
            val phrasing = refinePhraser.phrase(text)
            phrasing?.report?.report?.let { report ->
                recordSingleInference(
                    DraftSourceId.ON_DEVICE_LLM,
                    report,
                    if (phrasing.operation != null) com.cues.core.drafting.AttemptOutcome.DRAFTED else com.cues.core.drafting.AttemptOutcome.FAILED,
                )
            }
            val operation = phrasing?.operation
            if (operation != null) {
                val refined = Refiner.apply(draft, operation).routine
                return Turn(
                    text,
                    AssistantIntent.Refine(operation),
                    AssistantReply(ReplyCode.DRAFT_REFINED, answeredFrom = ReplySource.ON_DEVICE_LLM),
                    draft = refined,
                )
            }
            // The model was tried — there was a draft to edit and the
            // deterministic router found no match — but its restatement
            // didn't parse into RefineGrammarParser's closed vocabulary, or
            // the call itself failed. Distinct from UNSUPPORTED below, which
            // means nothing was ever tried. See CLEANUP.md CL-39.
            return Turn(text, intent, AssistantReply(ReplyCode.REFINE_MODEL_UNPARSED, answeredFrom = ReplySource.ON_DEVICE_LLM))
        }
        return Turn(text, intent, AssistantReply(ReplyCode.UNSUPPORTED, answeredFrom = ReplySource.PARSER))
    }

    private fun controlTurn(
        conversation: Conversation,
        text: String,
        intent: AssistantIntent.Control,
    ): Turn {
        val routineId = when {
            intent.reference.lowercase() in setOf("this", "this cue", "that", "that one") ->
                conversation.currentDraft?.id ?: conversation.lastRoutineId
            else -> when (val resolution = PersonalIndex(routines, embedder).resolveRoutine(intent.reference, conversation.lastRoutineId)) {
                is ReferenceResolution.Resolved -> resolution.routine.id
                else -> null
            }
        }
        if (routineId == null) {
            return Turn(text, intent, AssistantReply(ReplyCode.ROUTINE_NOT_FOUND, answeredFrom = ReplySource.ROUTINE_STORE))
        }
        val pending = PendingCommand(kind = intent.kind, routineId = routineId)
        return Turn(
            text,
            intent,
            AssistantReply(
                ReplyCode.CONFIRM_COMMAND,
                answeredFrom = ReplySource.ROUTINE_STORE,
                chips = listOf(ReplyChip.Confirm(pending.id, "Confirm ${intent.kind.name.lowercase().replace('_', ' ')}")),
            ),
            pendingCommand = pending,
        )
    }

    private fun explainTurn(
        conversation: Conversation,
        text: String,
        intent: AssistantIntent.Explain,
    ): Turn {
        val resolution = PersonalIndex(routines, embedder).resolveRoutine(intent.reference, conversation.lastRoutineId)
        val reply = when (resolution) {
            is ReferenceResolution.Resolved -> AssistantReply(
                ReplyCode.EXPLANATION,
                mapOf("summary" to "${resolution.routine.title} is ${resolution.routine.status.name.lowercase()}. Its recorded run reasons appear in Receipts."),
                ReplySource.RECEIPTS,
            )
            is ReferenceResolution.NeedsClarification -> AssistantReply(
                ReplyCode.NEEDS_CLARIFICATION,
                mapOf("question" to "Which cue did you mean?"),
                ReplySource.ROUTINE_STORE,
                resolution.candidates.map { ReplyChip.Choice(it.id, it.title) },
            )
            is ReferenceResolution.NeedsConfirmation -> AssistantReply(
                ReplyCode.NEEDS_CLARIFICATION,
                mapOf("question" to "Did you mean ${resolution.candidates.first().title}?"),
                ReplySource.ROUTINE_STORE,
                resolution.candidates.map { ReplyChip.Choice(it.id, it.title) },
            )
            ReferenceResolution.NotFound -> AssistantReply(ReplyCode.ROUTINE_NOT_FOUND, answeredFrom = ReplySource.ROUTINE_STORE)
        }
        return Turn(text, intent, reply)
    }

    private fun rememberTurn(text: String, intent: AssistantIntent.Remember): Turn {
        val store = facts
            ?: return Turn(text, intent, AssistantReply(ReplyCode.UNSUPPORTED, answeredFrom = ReplySource.ROUTINE_STORE))
        val id = intent.label.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "fact" }
        val existing = store.findFact(id)
        val fact = com.cues.core.model.Fact(
            id = id,
            version = (existing?.version ?: 0) + 1,
            kind = inferFactKind(intent.value),
            label = intent.label,
            value = intent.value,
            source = com.cues.core.model.FactSource.SAID,
            createdAt = clock.nowMillis(),
        )
        saveFact(fact)
        return Turn(
            text,
            intent,
            AssistantReply(
                ReplyCode.FACT_REMEMBERED,
                mapOf("label" to fact.label, "value" to fact.value),
                ReplySource.ROUTINE_STORE,
            ),
        )
    }

    fun saveFact(fact: com.cues.core.model.Fact) {
        val store = facts ?: return
        val previous = store.findFact(fact.id)
        store.saveFact(fact)
        if (previous != null && previous != fact) invalidateFactDependents(fact.id, fact.version)
    }

    fun deleteFact(id: String) {
        facts?.deleteFact(id)
        invalidateFactDependents(id, null)
    }

    fun listFacts(): List<com.cues.core.model.Fact> = facts?.allFacts().orEmpty()

    private fun invalidateFactDependents(id: String, currentVersion: Int?) {
        routines.all().filter { routine ->
            routine.factDependencies.any { it.id == id && (currentVersion == null || it.version != currentVersion) }
        }.forEach { routine ->
            routines.save(routine.copy(approvedDigest = null, status = RoutineStatus.REVIEWABLE))
        }
    }

    private fun inferFactKind(value: String): com.cues.core.model.FactKind = when {
        Regex("^(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\s+\\d{1,2}(?:,?\\s+\\d{4})?$", RegexOption.IGNORE_CASE)
            .matches(value.trim()) -> com.cues.core.model.FactKind.DATE
        Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(value.trim()) -> com.cues.core.model.FactKind.DATE
        else -> com.cues.core.model.FactKind.TEXT
    }

    /** Runs only after a confirmation chip/tap. Never called from [converse]. */
    fun confirm(command: PendingCommand): Boolean = when (command.kind) {
        ControlKind.PAUSE -> command.routineId?.let(::pause) != null
        ControlKind.RESUME -> command.routineId?.let(::resume) != null
        ControlKind.SKIP_TODAY -> command.routineId?.let(::skipToday) != null
        ControlKind.STOP -> command.sessionId?.let(::onManualStop) != null
    }

    private fun DraftSourceId.replySource(): ReplySource = when (this) {
        DraftSourceId.GRAMMAR_PARSER -> ReplySource.PARSER
        DraftSourceId.ON_DEVICE_LLM -> ReplySource.ON_DEVICE_LLM
        DraftSourceId.SARVAM_CLOUD -> ReplySource.SARVAM_CLOUD
        // Never actually reached today: a Cue Card import goes straight to
        // Review, not through converse(). Kept exhaustive anyway, because an
        // exhaustive `when` is exactly what caught every other drafter
        // provenance gap in this file.
        DraftSourceId.IMPORTED_CARD -> ReplySource.ROUTINE_STORE
        // Hub calls do not draft routines and must never reach converse();
        // retain a safe, non-model reply if a future caller misroutes one.
        DraftSourceId.EXTERNAL_GEMMA_CALL, DraftSourceId.SYSTEM_AGENT_CALL -> ReplySource.ROUTINE_STORE
    }

    /** Normalizes and validates without changing status. Safe to call repeatedly while editing. */
    fun review(routine: Routine): Approvals.ReviewResult = Approvals.review(routine)

    /**
     * A live read of which required capabilities are still missing, without
     * attempting to approve or arm anything (4.7 / AC-03).
     *
     * [Approvals.arm] already re-reads capabilities at the moment of arming
     * rather than trusting the review; this is the same read, exposed so the
     * Review screen can repeat it in `onResume` — the case that actually
     * matters is a user who leaves the app to grant a permission and comes
     * back, which nothing before 4.7 ever re-checked.
     */
    fun missingCapabilities(routine: Routine): Set<Capability> = routine.requiredCapabilities - capabilities.granted()

    /**
     * Approves the routine's current form, then attempts to arm it.
     *
     * The approved version is persisted even if arming then fails on a
     * missing capability: the user can grant that permission and retry
     * without re-approving, because nothing about what they agreed to has
     * changed.
     */
    fun approveAndArm(routine: Routine): ArmResult<Routine> {
        val approved = when (val result = Approvals.approve(routine)) {
            is ArmResult.Ok -> result.routine
            is ArmResult.Invalid -> return result
            else -> error("Approvals.approve never returns $result")
        }
        routines.save(approved)

        return when (val armed = Approvals.arm(approved, capabilities)) {
            is ArmResult.Ok -> {
                routines.save(armed.routine)
                armed
            }

            else -> armed
        }
    }

    fun list(): List<Routine> = routines.all()

    fun pause(routineId: String): Routine? {
        val routine = routines.findRoutine(routineId) ?: return null
        return Approvals.pause(routine).also { routines.save(it) }
    }

    fun resume(routineId: String): ArmResult<Routine>? {
        val routine = routines.findRoutine(routineId) ?: return null
        return when (val result = Approvals.resume(routine)) {
            is ArmResult.Ok -> {
                routines.save(result.routine)
                result
            }

            else -> result
        }
    }

    /** No-op, successfully, on an id that is already gone — deletion is idempotent. */
    fun delete(routineId: String): DeleteResult {
        val routine = routines.findRoutine(routineId) ?: return DeleteResult.Ok
        return when (val result = Approvals.delete(routine, sessions)) {
            DeleteResult.Ok -> {
                routines.delete(routineId)
                DeleteResult.Ok
            }

            is DeleteResult.Blocked -> result
        }
    }

    /** Skip just this local calendar day; the record remains visible and self-expires. */
    fun skipToday(routineId: String): Patch? {
        val routine = routines.findRoutine(routineId) ?: return null
        val date = java.time.Instant.ofEpochMilli(clock.nowMillis()).atZone(zoneId()).toLocalDate().toString()
        return Patch(routineId, routine.version, PatchKind.SkipOccurrence(date), clock.nowMillis()).also {
            patches?.savePatch(it)
            usageLedger?.appendLedger(LedgerEvent.PatchCreated("skip-today", localDay(clock.nowMillis()), clock.nowMillis(), routineId))
        }
    }

    fun pauseUntil(routineId: String, millis: Long): Patch? {
        val routine = routines.findRoutine(routineId) ?: return null
        require(millis > clock.nowMillis()) { "Pause expiry must be in the future." }
        return Patch(routineId, routine.version, PatchKind.SkipUntil(millis), clock.nowMillis()).also {
            patches?.savePatch(it)
            usageLedger?.appendLedger(LedgerEvent.PatchCreated("pause-until", localDay(clock.nowMillis()), clock.nowMillis(), routineId))
        }
    }

    fun clearPatch(routineId: String) = patches?.clearPatch(routineId)

    /** The routine's active patch, if any — read, never inferred, for the detail screen. */
    fun currentPatch(routineId: String): Patch? = patches?.findPatch(routineId)

    // -------------------------------------------------------------- runtime

    /**
     * Handles a Bluetooth or power transition observed by an adapter.
     *
     * Tries every armed routine against the event: [SessionEngine.onExitEvent]
     * first (does this end an already-running session?), then
     * [SessionEngine.onTriggerEvent] (does this start a new one?). A routine
     * whose trigger has nothing to do with this event resolves as a harmless
     * mismatch in both calls, so the caller never has to pre-filter which
     * routines might care — that filtering already lives in the evaluator.
     *
     * Readings the caller could not take are passed through as
     * [ContextValue.Unknown] and stay that way all the way to the receipt;
     * this never fills one in on the caller's behalf.
     *
     * The snapshot describes the moment the event happened — [TriggerEvent.atMillis]
     * — not the moment this method got around to running. A broadcast that was
     * queued behind other work must still be judged against the conditions
     * that held when the earbuds actually connected, not a later "now".
     *
     * [SessionEngine.onTriggerEvent] is only asked about a routine whose
     * trigger could plausibly be this event — right kind, right device.
     * Its own admission checks (duplicate, already-active) run before trigger
     * matching, so asking it about an event that was never this routine's
     * business would report "already running" instead of the truth, which
     * is that the event had nothing to do with this routine at all. Filtering
     * here means every result that does come back is worth a receipt.
     */
    fun onDeviceEvent(
        event: TriggerEvent,
        charging: ContextValue<Boolean> = unread(UnknownReason.NEVER_OBSERVED),
        connectedDeviceIds: ContextValue<Set<String>> = unread(UnknownReason.NEVER_OBSERVED),
        wifi: ContextValue<com.cues.core.model.WifiState> = unread(UnknownReason.NEVER_OBSERVED),
        audioOutputs: ContextValue<Set<com.cues.core.model.AudioKind>> = unread(UnknownReason.NEVER_OBSERVED),
        batteryPercent: ContextValue<Int> = unread(UnknownReason.NEVER_OBSERVED),
        insidePlaces: ContextValue<Set<String>> = unread(UnknownReason.NEVER_OBSERVED),
    ): List<EngineResult> {
        usageLedger?.takeIf { it.signalOptIn }?.appendLedger(
            LedgerEvent.SignalObserved(
                kind = event.kind.name.lowercase(),
                key = event.deviceId ?: event.kind.name.lowercase(),
                minuteOfDay = java.time.Instant.ofEpochMilli(event.atMillis).atZone(zoneId()).let { it.hour * 60 + it.minute },
                weekday = localDay(event.atMillis),
                atMillis = event.atMillis,
            ),
        )
        val snapshot = SnapshotBuilder.build(
            nowMillis = event.atMillis,
            zoneId = zoneId(),
            charging = charging,
            connectedDeviceIds = connectedDeviceIds,
            wifi = wifi,
            audioOutputs = audioOutputs,
            batteryPercent = batteryPercent,
            insidePlaces = insidePlaces,
        )

        return routines.armed().flatMap { routine ->
            val results = mutableListOf<EngineResult>()

            val exit = engine.onExitEvent(routine, event)
            if (exit !is EngineResult.Ignored) {
                recordReceipt(routine, exit, event.provenance)
                results += exit
            }

            if (event.couldStart(routine)) {
                patchReason(routine, event.atMillis)?.let { reason ->
                    val skipped = EngineResult.Skipped(listOf(reason))
                    recordReceipt(routine, skipped, event.provenance, snapshot)
                    results += skipped
                    return@flatMap results
                }
                val start = engine.onTriggerEvent(routine, event, snapshot)
                recordReceipt(routine, start, event.provenance, snapshot)
                results += start
            }

            results
        }
    }

    private fun patchReason(routine: Routine, atMillis: Long): Reason? {
        val patch = patches?.findPatch(routine.id) ?: return null
        if (patch.baseVersion != routine.version) {
            patches?.clearPatch(routine.id)
            return null
        }
        return when (val kind = patch.kind) {
            is PatchKind.SkipUntil -> if (atMillis < kind.epochMillis) Reason(
                ReasonCode.PATCH_SKIPPED_UNTIL, Truth.NO_MATCH, "Paused until ${java.time.Instant.ofEpochMilli(kind.epochMillis).atZone(zoneId()).toLocalDateTime()}.",
            ) else { patches?.clearPatch(routine.id); null }
            is PatchKind.SkipOccurrence -> {
                val date = java.time.Instant.ofEpochMilli(atMillis).atZone(zoneId()).toLocalDate().toString()
                if (date == kind.date) Reason(ReasonCode.PATCH_SKIPPED_TODAY, Truth.NO_MATCH, "Skipped for today by your temporary patch.")
                else if (date > kind.date) { patches?.clearPatch(routine.id); null } else null
            }
        }
    }

    /** True when [routine]'s trigger is even the right shape for this event — same kind, same device. */
    private fun TriggerEvent.couldStart(routine: Routine): Boolean {
        // A manual run names its target explicitly (CL-28): Trigger.Manual
        // itself is a singleton with nothing of its own to match against, so
        // without this check every armed manual cue would start from one
        // MANUAL_RUN event. An unscoped one (routineId == null) starts none,
        // rather than guessing.
        if (kind == EventKind.MANUAL_RUN && routine.trigger is com.cues.core.model.Trigger.Manual) {
            return routineId == routine.id
        }
        return SignalRegistry.listensFor(routine.trigger, kind) &&
            SignalRegistry.match(routine.trigger, this).truth == com.cues.core.eval.Truth.MATCH
    }

    /**
     * The exact-alarm callback for one session's deadline.
     *
     * Takes only the session id, not a routine id — that is deliberately all
     * the alarm's own PendingIntent has to carry. The routine is looked up
     * from the session record itself, which is also simpler at every real
     * call site: an alarm, a grace-window timer and a manual stop button all
     * naturally know which session they're about, never which routine.
     */
    fun onDeadline(sessionId: String): EngineResult? = exitFor(sessionId, EventKind.DEADLINE_REACHED)

    /** The reconnect grace window for one session has elapsed without a reconnect. */
    fun onGraceElapsed(sessionId: String): EngineResult? {
        val routine = routineForSession(sessionId) ?: return null
        return engine.onGraceElapsed(routine, sessionId).also { recordReceipt(routine, it, EventProvenance.PHYSICAL) }
    }

    /** The user's own stop control for a running session. */
    fun onManualStop(sessionId: String): EngineResult? = exitFor(sessionId, EventKind.MANUAL_STOP)

    private fun exitFor(sessionId: String, kind: EventKind): EngineResult? {
        val routine = routineForSession(sessionId) ?: return null
        // A stop button is the user's own hand; a deadline alarm is a real
        // occurrence. Neither is a rehearsal, and the structured receipt keeps
        // the difference rather than filing both as PHYSICAL.
        val provenance = if (kind == EventKind.MANUAL_STOP) EventProvenance.MANUAL else EventProvenance.PHYSICAL
        val event = TriggerEvent(kind, clock.nowMillis(), provenance = provenance)
        return engine.onExitEvent(routine, event, sessionId).also { recordReceipt(routine, it, provenance) }
    }

    private fun routineForSession(sessionId: String): Routine? {
        val session = sessions.find(sessionId) ?: return null
        return routines.findRoutine(session.routineId)
    }

    /**
     * Reconciles state after a process restart: an expired session is cleaned
     * up, never replayed, per [SessionEngine.reconcile]. Also retries any
     * cleanup that was left outstanding from a previous run.
     */
    fun onBoot(): List<Session> {
        val byId = routines.all().associateBy { it.id }
        val reconciled = engine.reconcile(byId)
        val retried = engine.retryPendingCleanup(byId)

        (reconciled + retried).forEach { session ->
            byId[session.routineId]?.let { recordReceipt(it, EngineResult.Ended(session), provenance = null) }
        }
        return reconciled + retried
    }

    /**
     * Attempts every [com.cues.core.model.ActionState.PENDING] action across
     * every live session, if [attention] agrees someone is at the phone right
     * now — checked here, not left to the caller, because
     * [SessionEngine.retryPendingActions] itself trusts whoever calls it to
     * have already confirmed that.
     *
     * Safe to call on every resume whether or not anything is pending: a
     * session with nothing waiting is left alone, and `attention` is read
     * only when there is a `PENDING` action to justify asking. On a phone,
     * the moment the app comes back to the foreground is a reasonable proxy
     * for presence, the same moment [checkBluetoothCoverage] already
     * re-checks from `ON_RESUME` (CL-25).
     */
    fun retryPendingActions(): List<Session> {
        val byId = routines.all().associateBy { it.id }
        val pending = sessions.allUnfinished()
            .filter { session -> session.actions.any { it.state == com.cues.core.model.ActionState.PENDING } }
        if (pending.isEmpty() || !attention.isUserPresent()) return emptyList()

        return pending.mapNotNull { session ->
            val routine = byId[session.routineId] ?: return@mapNotNull null
            engine.retryPendingActions(routine, session.id)?.also { updated ->
                // Text and structure from one record, the same rule recordReceipt follows.
                val record = ReceiptRecords.resumed(routine, updated, clock.nowMillis())
                receipts.record(updated.id, record.textLines)
                receiptLog?.append(record)
            }
        }
    }

    /**
     * Checks for Bluetooth coverage gaps (R6, task 4.6): a live session whose
     * device Android now reports as not connected, with no disconnect ever
     * observed for it. [currentlyConnectedDeviceIds] is a live platform
     * reading the caller supplies — same discipline as [onDeviceEvent]'s
     * readings — never assumed here. Called on resume and after every
     * device event, per R6's method.
     */
    fun checkBluetoothCoverage(currentlyConnectedDeviceIds: Set<String>): List<Session> {
        val byId = routines.all().associateBy { it.id }
        val gaps = engine.checkBluetoothCoverage(byId, currentlyConnectedDeviceIds)
        gaps.forEach { session -> byId[session.routineId]?.let { recordReceipt(it, EngineResult.Ended(session), provenance = null) } }
        return gaps
    }

    // -------------------------------------------------------------- insights

    /**
     * The Insights report for [window], computed from this phone's own records.
     *
     * Reads are bounded: sessions through [SessionStore.recent] over two
     * windows (the second only feeds the time-in-cues delta), structured
     * receipts over one, and the ledger's own 14-day retention. Unfinished
     * sessions are added from [SessionStore.allUnfinished] whatever their
     * age, because an outstanding cleanup must never fall out of view just
     * because nothing has touched its record lately. Blocking I/O — call it
     * off the main thread.
     */
    fun insights(window: com.cues.core.insights.InsightsWindow): com.cues.core.insights.InsightsReport {
        val now = clock.nowMillis()
        val windowed = sessions.recent(now - 2 * window.millis)
        val unfinished = sessions.allUnfinished()
        return com.cues.core.insights.Insights.compute(
            sessions = (windowed + unfinished).distinctBy { it.id },
            receiptRecords = receiptLog?.receiptRecords(now - window.millis).orEmpty(),
            ledger = usageLedger?.let { com.cues.core.insights.LedgerView(it.signalOptIn, it.ledgerEvents()) },
            routines = routines.all(),
            window = window,
            clock = clock,
            zone = zoneId(),
            inferenceLedger = inferenceLedger?.let {
                com.cues.core.insights.InferenceLedgerView(it.usageTrackingEnabled, it.inferenceEntries())
            },
        )
    }

    // ---------------------------------------------------------- diagnostics

    /**
     * How this service is configured to draft, independent of any one
     * call's outcome — [com.cues.core.ports.ModelAvailability.NOT_INSTALLED]/
     * [com.cues.core.ports.ModelAvailability.INSTALLED_OFF] must read as
     * plainly as [com.cues.core.ports.ModelAvailability.READY], never
     * papered over by a drafter identity that claims a model regardless
     * (CLEANUP.md CL-18: "the drafter label is always ON-DEVICE MODEL").
     */
    data class DrafterSetup(
        val model: com.cues.core.ports.ModelAvailability,
        val parser: Boolean = true,
        val cloudOptIn: Boolean = false,
    )

    data class Diagnostics(
        val setup: DrafterSetup,
        /**
         * Every drafter the most recent [draft] call actually asked, and
         * what each one did — `null` before any draft has run, or when
         * neither the call nor its drafter ever produced a report to trace.
         * See [com.cues.core.review.DraftCredit] for how this becomes a
         * label, and [lastTrace] for the observable form of the same value.
         */
        val lastTrace: com.cues.core.drafting.DraftTrace?,
    )

    /**
     * Names the actual drafting setup, for [com.cues.core.drafting.DraftSourceId]'s
     * whole reason for existing: a canonical parser must never be presented as
     * language understanding, on screen or in a slide. A snapshot of
     * [lastTrace]; prefer collecting that flow directly wherever the caller
     * can, so the UI updates without re-reading a stale snapshot every frame.
     */
    fun diagnostics(): Diagnostics = Diagnostics(
        setup = DrafterSetup(
            model = modelAvailabilityProbe?.current() ?: com.cues.core.ports.ModelAvailability.NOT_INSTALLED,
            cloudOptIn = cloudOptInProbe?.invoke() ?: false,
        ),
        lastTrace = _lastTrace.value,
    )

    /**
     * Records whichever [com.cues.core.drafting.DraftTrace] [result] carries
     * — from [DifferentialDrafter][com.cues.core.drafting.DifferentialDrafter] —
     * or, for a single, unwrapped drafter that still attached its own
     * [InferenceReport] (no trace to speak of), synthesizes a one-attempt
     * trace so the same bookkeeping applies either way. A plain parser draft
     * with neither carries nothing, exactly as before this existed.
     */
    private fun recordDraftResult(result: DraftResult) {
        val trace = result.trace ?: run {
            val (report, outcome, elapsedMillis) = when (result) {
                is DraftResult.Drafted -> Triple(result.inferenceReport, com.cues.core.drafting.AttemptOutcome.DRAFTED, result.elapsedMillis)
                is DraftResult.NeedsClarification -> Triple(result.inferenceReport, com.cues.core.drafting.AttemptOutcome.CLARIFY, result.elapsedMillis)
                is DraftResult.Failed -> Triple(result.inferenceReport, com.cues.core.drafting.AttemptOutcome.FAILED, result.elapsedMillis)
            }
            report ?: return
            com.cues.core.drafting.DraftTrace(
                listOf(com.cues.core.drafting.DrafterAttempt(result.source, outcome, elapsedMillis = elapsedMillis, inferenceReport = report)),
                com.cues.core.drafting.DraftVerdict.SINGLE,
            )
        }
        recordTrace(trace)
    }

    /**
     * The narrower funnel [refinePhraser]'s single, non-differential model
     * call passes through — recorded as its own one-attempt trace so
     * Checks' "last run" reflects a refine call too, not only a fresh draft.
     */
    private fun recordSingleInference(source: DraftSourceId, report: InferenceReport, outcome: com.cues.core.drafting.AttemptOutcome) {
        recordTrace(
            com.cues.core.drafting.DraftTrace(
                listOf(com.cues.core.drafting.DrafterAttempt(source, outcome, elapsedMillis = 0, inferenceReport = report)),
                com.cues.core.drafting.DraftVerdict.SINGLE,
            ),
        )
    }

    /**
     * Updates [lastTrace] and — when [inferenceLedger] is wired and its own
     * opt-in is on — appends one [InferenceLedgerEntry] per attempt that
     * carried a report, not only the one whose output was used. Before
     * Sprint 8 this was the reverse: only a winning model draft was kept at
     * all, so an agreed, disagreed, invalid or timed-out model attempt left
     * no record anywhere it ran (CLEANUP.md CL-18/CL-34).
     */
    private fun recordTrace(trace: com.cues.core.drafting.DraftTrace) {
        _lastTrace.value = trace
        trace.attempts.forEach { attempt ->
            attempt.inferenceReport?.let { report ->
                appendLedgerEntry(attempt.source, report, attempt.outcome, trace.verdict, trace.promptId)
            }
        }
    }

    private fun appendLedgerEntry(
        source: DraftSourceId,
        report: InferenceReport,
        outcome: com.cues.core.drafting.AttemptOutcome,
        verdict: com.cues.core.drafting.DraftVerdict,
        promptId: String?,
    ) {
        inferenceLedger?.takeIf { it.usageTrackingEnabled }?.let { ledger ->
            val (costUsd, costBasis) = InferenceCost.costFor(source, report.estimatedTokens)
            ledger.appendInference(
                InferenceLedgerEntry(
                    atMillis = clock.nowMillis(),
                    source = source,
                    backend = report.backend,
                    estimatedTokens = report.estimatedTokens,
                    latencyMs = report.loadMs + report.generationMs,
                    costUsd = costUsd,
                    costBasis = costBasis,
                    outcome = outcome,
                    verdict = verdict,
                    promptId = promptId,
                ),
            )
        }
    }

    // ------------------------------------------------------------- receipts

    /**
     * Files one outcome, as text and as structure.
     *
     * Both come from a single [ReceiptRecord], built once from [result]: the
     * text log is written from the record's own headline and lines, so the
     * text a person reads and the structure a screen counts cannot disagree.
     * [provenance] is null only when no event drove this at all — boot
     * reconciliation and coverage checks.
     */
    private fun recordReceipt(
        routine: Routine,
        result: EngineResult,
        provenance: EventProvenance?,
        observed: com.cues.core.model.ContextSnapshot? = null,
    ) {
        val key = sessionIdFor(routine, result)
        val record = ReceiptRecords.forResult(routine, result, key, clock.nowMillis(), provenance, observed)
        receipts.record(key, record.textLines)
        receiptLog?.append(record)
        when (result) {
            is EngineResult.Started -> {
                result.session.actions.filter { it.state == com.cues.core.model.ActionState.BLOCKED }.forEach {
                    usageLedger?.appendLedger(LedgerEvent.ActionBlocked(it.actionId.name, clock.nowMillis(), routine.id))
                }
                if (routine.trigger is com.cues.core.model.Trigger.Manual) {
                    val time = java.time.Instant.ofEpochMilli(clock.nowMillis()).atZone(zoneId())
                    usageLedger?.appendLedger(LedgerEvent.ManualStart(time.hour * 60 + time.minute, localDay(clock.nowMillis()), clock.nowMillis(), routine.id))
                }
            }
            is EngineResult.Ended -> {
                val planned = routine.actions.firstOrNull { it.actionId == com.cues.core.model.ActionId.START_FOCUS_TIMER }
                    ?.let { (it.args as? com.cues.core.model.ActionArgs.FocusTimer)?.durationMinutes } ?: 0
                val actual = ((result.session.endedAtMillis ?: clock.nowMillis()) - result.session.startedAtMillis)
                    .coerceAtLeast(0) / 60_000L
                usageLedger?.appendLedger(
                    LedgerEvent.SessionEnded(
                        routine.id, planned, actual.toInt(), result.session.endReason?.name.orEmpty(),
                        result.session.endedAtMillis ?: clock.nowMillis(),
                    ),
                )
            }
            is EngineResult.Skipped -> result.reasons.firstOrNull()?.let {
                usageLedger?.appendLedger(LedgerEvent.Skipped(it.code.name, atMillis = clock.nowMillis()))
            }
            else -> Unit
        }
    }

    private fun localDay(atMillis: Long): com.cues.core.model.Day = when (
        java.time.Instant.ofEpochMilli(atMillis).atZone(zoneId()).dayOfWeek
    ) {
        java.time.DayOfWeek.MONDAY -> com.cues.core.model.Day.MON
        java.time.DayOfWeek.TUESDAY -> com.cues.core.model.Day.TUE
        java.time.DayOfWeek.WEDNESDAY -> com.cues.core.model.Day.WED
        java.time.DayOfWeek.THURSDAY -> com.cues.core.model.Day.THU
        java.time.DayOfWeek.FRIDAY -> com.cues.core.model.Day.FRI
        java.time.DayOfWeek.SATURDAY -> com.cues.core.model.Day.SAT
        java.time.DayOfWeek.SUNDAY -> com.cues.core.model.Day.SUN
    }

    private fun sessionIdFor(routine: Routine, result: EngineResult): String = when (result) {
        is EngineResult.Started -> result.session.id
        is EngineResult.Ended -> result.session.id
        is EngineResult.ExitScheduled -> result.session.id
        is EngineResult.ExitCancelled -> result.session.id
        // No session exists yet for these — a stable, readable id still lets a
        // UI group "everything that happened to this routine" together.
        is EngineResult.Skipped -> "skip-${routine.id}-${clock.nowMillis()}"
        EngineResult.Ignored -> "ignored-${routine.id}-${clock.nowMillis()}"
    }

    private fun <T> unread(reason: UnknownReason): ContextValue<T> =
        ContextValue.Unknown(reason, com.cues.core.model.ContextSource.USER)

}
