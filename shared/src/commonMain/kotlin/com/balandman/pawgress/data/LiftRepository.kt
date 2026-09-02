package com.balandman.pawgress.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * All app state lives in one small JSON file in the app's private storage.
 *
 * A gym log is a few thousand rows over years, so a database would be more
 * machinery than the problem deserves — and the whole file is cheap to rewrite
 * on every change.
 *
 * The file holds a map of profiles keyed by Google account, plus which one is
 * active. Everything the UI sees is a view onto the active profile.
 *
 * [storage] is the one platform-specific piece — see [AppFileStorage].
 * androidApp constructs an `AndroidAppFileStorage(context)`; iosApp
 * constructs an `IosAppFileStorage()`. Everything else here is plain,
 * platform-independent Kotlin.
 */
@OptIn(ExperimentalUuidApi::class)
class LiftRepository(private val storage: AppFileStorage) {

    // The original Android version used Dispatchers.IO for the background
    // persistence write. Dispatchers.Default is used here instead purely to
    // avoid depending on IO-dispatcher availability/behavior parity across
    // Kotlin/Native versions that this environment can't verify by compiling
    // — for a few-KB JSON write this makes no practical difference.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val writeMutex = Mutex()

    private val _profiles = MutableStateFlow<Map<String, Profile>>(emptyMap())
    private val _activeKey = MutableStateFlow(Profile.LOCAL_KEY)

    init {
        load()
    }

    // ------------------------------------------------------------ derived views

    val active: StateFlow<Profile> =
        combine(_profiles, _activeKey) { profiles, key -> profiles[key] ?: blank(key) }
            .stateIn(scope, SharingStarted.Eagerly, current())

    val machines: StateFlow<List<Machine>> =
        active.map { it.machines }.stateIn(scope, SharingStarted.Eagerly, current().machines)

    val log: StateFlow<List<LogEntry>> =
        active.map { it.log }.stateIn(scope, SharingStarted.Eagerly, current().log)

    val sync: StateFlow<SyncState> =
        combine(active, _profiles) { profile, profiles -> profile.toSyncState(profiles.size) }
            .stateIn(scope, SharingStarted.Eagerly, current().toSyncState(_profiles.value.size))

    // ------------------------------------------------------------ direct reads
    // Callers that act on a decision (the sync manager, mostly) read these rather
    // than the flows above, because a derived flow updates a beat later and a
    // sync must never act on the profile that was active a moment ago.

    /** The active profile, always current. */
    fun current(): Profile = _profiles.value[_activeKey.value] ?: blank(_activeKey.value)

    fun visibleMachines(): List<Machine> =
        current().machines.filter { it.visible }.sortedBy { it.sortOrder }

    fun unsyncedEntries(): List<LogEntry> =
        current().log.filter { !it.synced }.sortedBy { it.loggedAt }

    fun pendingDeletions(): List<String> = current().pendingDeletions

    fun machine(id: String): Machine? = current().machines.firstOrNull { it.id == id }

    private fun previousWeightBefore(machineId: String, excludingEntryId: String): LogEntry? =
        current().log
            .filter { it.machineId == machineId && it.id != excludingEntryId }
            .maxByOrNull { it.loggedAt }

    // --------------------------------------------------------------- profiles

    /**
     * Point the app at [email]'s profile, creating it if this account is new here.
     *
     * The very first account to sign in adopts whatever was set up while signed
     * out, so nobody loses the machines they configured before connecting. Every
     * account after that starts from the default catalog with an empty history.
     */
    fun activateAccount(email: String): Profile {
        val profiles = _profiles.value
        val existing = profiles[email]

        if (existing != null) {
            val reconnected = existing.copy(connected = true)
            _profiles.value = profiles + (email to reconnected)
            _activeKey.value = email
            persist()
            return reconnected
        }

        val activeKey = _activeKey.value
        val activeProfile = profiles[activeKey]
        val onlyLocalExists = profiles.keys.all { it == Profile.LOCAL_KEY }

        // Splitting this into its own if/else (rather than a separate "adopt"
        // boolean checked twice) lets the compiler smart-cast activeProfile to
        // non-null right where it's used, instead of needing a second,
        // provably-redundant null check below.
        val adopt: Boolean
        val adopted: Profile
        if (activeKey == Profile.LOCAL_KEY && activeProfile != null && onlyLocalExists) {
            adopt = true
            adopted = activeProfile.copy(key = email, accountEmail = email, connected = true)
        } else {
            adopt = false
            adopted = blank(email).copy(accountEmail = email, connected = true)
        }

        _profiles.value =
            (if (adopt) profiles - Profile.LOCAL_KEY else profiles) + (email to adopted)
        _activeKey.value = email
        persist()
        return adopted
    }

    /** Stops syncing but keeps the profile, its grid and its history intact. */
    fun disconnect() {
        mutateActive { profile ->
            profile.copy(
                connected = false,
                spreadsheetId = null,
                spreadsheetUrl = null,
                lastSyncAt = null,
                lastError = null,
                pendingDeletions = emptyList(),
                log = profile.log.map { it.copy(synced = false) },
            )
        }
    }

    // ------------------------------------------------------------------ write

    /** Record a lift. Returns the new entry so the caller can kick off a sync. */
    fun logLift(machineId: String, weight: Int, difficulty: Difficulty? = null): LogEntry? {
        val machine = machine(machineId) ?: return null
        val clamped = Weights.clamp(weight)
        val now = currentEpochMillis()

        // One entry per machine per gym day: re-logging replaces today's entry
        // rather than stacking up duplicates.
        val existingToday = current().log.firstOrNull {
            it.machineId == machineId && GymDay.isToday(it.loggedAt)
        }

        val entry = LogEntry(
            id = existingToday?.id ?: Uuid.random().toString(),
            machineId = machineId,
            machineName = machine.name,
            weight = clamped,
            loggedAt = now,
            synced = false,
            machineGroup = machine.group,
            difficulty = difficulty,
        )

        // A pawprint is earned once per machine per gym day — only the *first*
        // log of the day, never a same-day correction/re-log.
        val earnedPawprint = existingToday == null

        mutateActive { profile ->
            // A stale row already in the sheet gets queued for removal, so the
            // corrected value does not end up sitting next to the wrong one.
            val deletions =
                if (existingToday != null && existingToday.synced) {
                    profile.pendingDeletions + existingToday.id
                } else {
                    profile.pendingDeletions
                }

            profile.copy(
                log = profile.log.filterNot { it.id == entry.id } + entry,
                machines = profile.machines.map {
                    if (it.id == machineId) {
                        it.copy(lastWeight = clamped, lastLoggedAt = now, lastDifficulty = difficulty)
                    } else {
                        it
                    }
                },
                pendingDeletions = deletions,
                pawprintsBalance = if (earnedPawprint) profile.pawprintsBalance + 1 else profile.pawprintsBalance,
                pawprintsEarnedTotal =
                    if (earnedPawprint) profile.pawprintsEarnedTotal + 1 else profile.pawprintsEarnedTotal,
            )
        }
        return entry
    }

    /**
     * Undo today's entry for a machine, restoring the previously shown weight.
     * Since only the first log of a gym day ever earns a pawprint, and there is
     * only ever one entry per machine per day, undoing today's entry always
     * refunds exactly the one pawprint it earned.
     */
    fun undoToday(machineId: String) {
        val entry = current().log.firstOrNull {
            it.machineId == machineId && GymDay.isToday(it.loggedAt)
        } ?: return

        val restored = previousWeightBefore(machineId, entry.id)

        mutateActive { profile ->
            profile.copy(
                log = profile.log.filterNot { it.id == entry.id },
                machines = profile.machines.map {
                    if (it.id == machineId) {
                        it.copy(
                            lastWeight = restored?.weight,
                            lastLoggedAt = restored?.loggedAt,
                            lastDifficulty = restored?.difficulty,
                        )
                    } else {
                        it
                    }
                },
                pendingDeletions =
                    if (entry.synced) profile.pendingDeletions + entry.id
                    else profile.pendingDeletions,
                pawprintsBalance = (profile.pawprintsBalance - 1).coerceAtLeast(0),
            )
        }
    }

    fun setVisible(machineId: String, visible: Boolean) = mutateActive { profile ->
        profile.copy(
            machines = profile.machines.map {
                if (it.id == machineId) it.copy(visible = visible) else it
            }
        )
    }

    fun setGroup(machineId: String, group: MachineGroup) = mutateActive { profile ->
        profile.copy(
            machines = profile.machines.map {
                if (it.id == machineId) it.copy(group = group) else it
            }
        )
    }

    fun setAllVisible(visible: Boolean) = mutateActive { profile ->
        profile.copy(machines = profile.machines.map { it.copy(visible = visible) })
    }

    fun rename(machineId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        mutateActive { profile ->
            profile.copy(
                machines = profile.machines.map {
                    if (it.id == machineId) it.copy(name = trimmed) else it
                }
            )
        }
    }

    fun setIcon(machineId: String, iconKey: String, illustrated: Boolean) = mutateActive { profile ->
        profile.copy(
            machines = profile.machines.map {
                if (it.id == machineId) it.copy(iconKey = iconKey, illustrated = illustrated) else it
            }
        )
    }

    fun addCustomMachine(
        name: String,
        iconKey: String,
        group: MachineGroup,
        illustrated: Boolean = true,
    ): Machine? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        val machine = Machine(
            id = "custom_" + Uuid.random().toString().take(8),
            name = trimmed,
            iconKey = iconKey,
            group = group,
            visible = true,
            custom = true,
            sortOrder = (current().machines.maxOfOrNull { it.sortOrder } ?: -1) + 1,
            illustrated = illustrated,
        )
        mutateActive { profile -> profile.copy(machines = profile.machines + machine) }
        return machine
    }

    /**
     * Undoes every entry logged today, one machine at a time — each one restores
     * that machine's tile to whatever it showed before today and, if that entry
     * had already synced, queues the matching sheet row for deletion. Nothing
     * from before today is touched.
     */
    fun resetToday() {
        val machineIds = current().log
            .filter { GymDay.isToday(it.loggedAt) }
            .map { it.machineId }
            .distinct()
        machineIds.forEach { undoToday(it) }
    }

    /**
     * Erases every lift ever logged in the app and blanks every tile back to
     * "—", locally. Machines, names, icons, hidden/shown state and the Google
     * connection are all left exactly as they are. The Google Sheet — an
     * append-only record by design — is never touched: old rows already synced
     * there stay put, since this is a reset of the phone, not of that history.
     * Pawprint balance, unlocked coaches and unlocked outfits are earned, paid
     * for and kept independently of the workout log, so this leaves all of them
     * untouched too.
     */
    fun fullReset() = mutateActive { profile ->
        profile.copy(
            log = emptyList(),
            machines = profile.machines.map { it.copy(lastWeight = null, lastLoggedAt = null) },
        )
    }

    // --------------------------------------------------------------- coaches

    /** Unlocks [coachId] for [cost] pawprints. Returns true if it just happened. */
    fun unlockCoach(coachId: Int, cost: Int): Boolean {
        val profile = current()
        if (coachId in profile.unlockedCoachIds) return false
        if (profile.pawprintsBalance < cost) return false
        mutateActive {
            it.copy(
                pawprintsBalance = it.pawprintsBalance - cost,
                unlockedCoachIds = it.unlockedCoachIds + coachId,
            )
        }
        return true
    }

    /** Switches which coach fronts the Fun Facts screen. No-op if not unlocked. */
    fun selectCoach(coachId: Int) {
        if (coachId !in current().unlockedCoachIds) return
        mutateActive { it.copy(selectedCoachId = coachId) }
    }

    /**
     * Unlocks [theme] for [coachId] at [cost] pawprints — the coach must
     * already be unlocked, and this exact coach+theme combo not already owned.
     * Returns true if it just happened.
     */
    fun unlockOutfit(coachId: Int, theme: CoachTheme, cost: Int): Boolean {
        val profile = current()
        if (coachId !in profile.unlockedCoachIds) return false
        val key = outfitKey(coachId, theme)
        if (key in profile.unlockedOutfits) return false
        if (profile.pawprintsBalance < cost) return false
        mutateActive {
            it.copy(
                pawprintsBalance = it.pawprintsBalance - cost,
                unlockedOutfits = it.unlockedOutfits + key,
            )
        }
        return true
    }

    /**
     * Equips [theme] for [coachId], or clears it back to the base look when
     * [theme] is null. Only an owned theme can be equipped. Whether the
     * equipped theme actually renders still depends on whether it's currently
     * in season and whether its art has been supplied — see `CoachArt.current`.
     */
    fun equipOutfit(coachId: Int, theme: CoachTheme?) {
        val profile = current()
        if (theme != null && outfitKey(coachId, theme) !in profile.unlockedOutfits) return
        mutateActive {
            val updated = it.equippedOutfits.toMutableMap()
            if (theme == null) updated.remove(coachId) else updated[coachId] = theme.slug
            it.copy(equippedOutfits = updated)
        }
    }

    /**
     * DEBUG ONLY — remove before release. Adds [amount] straight to the
     * spendable balance so the coach/outfit economy can be tested without
     * grinding out real gym-day logs. Deliberately leaves
     * [Profile.pawprintsEarnedTotal] untouched, since that number is meant to
     * reflect lifetime pawprints actually earned by lifting.
     */
    fun debugGrantPawprints(amount: Int) = mutateActive { profile ->
        profile.copy(pawprintsBalance = profile.pawprintsBalance + amount)
    }

    /**
     * Merges rows read back from the Google Sheet into the active profile — the
     * "restore from backup" flow. Purely additive: existing local entries are
     * kept, sheet rows fill in whatever the phone doesn't already have (matched
     * by Entry ID, so running this twice is harmless), and any exercise name
     * the sheet knows about that isn't already a machine here gets created —
     * hidden's not right for something with real history, so it comes back
     * visible. Every machine's tile is then recomputed from the merged log.
     * Restored history never retroactively earns pawprints — those are only
     * ever awarded live, at the moment a lift is logged.
     */
    fun restoreFromRows(rows: List<SheetRow>): RestoreSummary {
        val before = current()
        val existingIds = before.log.map { it.id }.toHashSet()
        val byNameLower = before.machines.associateBy { it.name.lowercase() }.toMutableMap()
        val createdMachines = mutableListOf<Machine>()
        var nextOrder = (before.machines.maxOfOrNull { it.sortOrder } ?: -1) + 1

        fun machineIdFor(row: SheetRow): String {
            byNameLower[row.exercise.lowercase()]?.let { return it.id }
            val group = MachineGroup.fromLabel(row.area) ?: MachineGroup.OTHER
            val created = Machine(
                id = "restored_" + Uuid.random().toString().take(8),
                name = row.exercise,
                iconKey = "machine",
                group = group,
                visible = true,
                custom = true,
                sortOrder = nextOrder++,
            )
            byNameLower[created.name.lowercase()] = created
            createdMachines += created
            return created.id
        }

        val restoredEntries = rows.map { row ->
            LogEntry(
                id = row.entryId,
                machineId = machineIdFor(row),
                machineName = row.exercise,
                weight = row.weight,
                loggedAt = row.loggedAt,
                synced = true,
                machineGroup = MachineGroup.fromLabel(row.area) ?: MachineGroup.OTHER,
                difficulty = Difficulty.fromLabel(row.difficultyLabel),
            )
        }

        val addedCount = restoredEntries.count { it.id !in existingIds }

        mutateActive { profile ->
            val mergedById = LinkedHashMap<String, LogEntry>()
            profile.log.forEach { mergedById[it.id] = it }
            // Sheet rows win on a collision — they're the backup being restored.
            restoredEntries.forEach { mergedById[it.id] = it }
            val mergedLog = mergedById.values.toList()

            val latestByMachine = mergedLog.groupBy { it.machineId }
                .mapValues { (_, entries) -> entries.maxByOrNull { it.loggedAt } }

            val allMachines = profile.machines + createdMachines
            val updatedMachines = allMachines.map { m ->
                val latest = latestByMachine[m.id]
                if (latest != null) {
                    m.copy(
                        lastWeight = latest.weight,
                        lastLoggedAt = latest.loggedAt,
                        lastDifficulty = latest.difficulty,
                    )
                } else {
                    m
                }
            }

            profile.copy(log = mergedLog, machines = updatedMachines)
        }

        return RestoreSummary(
            entriesAdded = addedCount,
            entriesTotal = rows.size,
            machinesCreated = createdMachines.size,
        )
    }

    /** Only custom machines can be deleted; built-ins are hidden instead. */
    fun deleteCustomMachine(machineId: String) {
        if (machine(machineId)?.custom != true) return
        mutateActive { profile ->
            profile.copy(machines = profile.machines.filterNot { it.id == machineId })
        }
    }

    fun recordSyncSuccess(spreadsheetId: String, spreadsheetUrl: String) = mutateActive { profile ->
        profile.copy(
            spreadsheetId = spreadsheetId,
            spreadsheetUrl = spreadsheetUrl,
            lastSyncAt = currentEpochMillis(),
            lastError = null,
        )
    }

    fun recordSyncError(message: String) = mutateActive { profile ->
        profile.copy(lastError = message)
    }

    fun markSynced(entryIds: Collection<String>) {
        if (entryIds.isEmpty()) return
        val ids = entryIds.toSet()
        mutateActive { profile ->
            profile.copy(log = profile.log.map { if (it.id in ids) it.copy(synced = true) else it })
        }
    }

    fun clearPendingDeletions(entryIds: Collection<String>) {
        if (entryIds.isEmpty()) return
        val ids = entryIds.toSet()
        mutateActive { profile ->
            profile.copy(pendingDeletions = profile.pendingDeletions.filterNot { it in ids })
        }
    }

    /**
     * Used when the spreadsheet had to be recreated: nothing previously pushed
     * exists any more, so the whole history gets rebuilt on the next sync.
     */
    fun markAllUnsynced() = mutateActive { profile ->
        profile.copy(
            log = profile.log.map { it.copy(synced = false) },
            pendingDeletions = emptyList(),
        )
    }

    // ------------------------------------------------------------ persistence

    private fun mutateActive(transform: (Profile) -> Profile) {
        val key = _activeKey.value
        val existing = _profiles.value[key] ?: blank(key)
        _profiles.value = _profiles.value + (key to transform(existing))
        persist()
    }

    private fun blank(key: String) = Profile(
        key = key,
        accountEmail = if (key == Profile.LOCAL_KEY) null else key,
        machines = MachineCatalog.defaults(),
    )

    private fun persist() {
        val snapshot = json.encodeToString(RootDto.serializer(), toDto())
        scope.launch {
            writeMutex.withLock {
                try {
                    storage.writeAtomic(snapshot)
                } catch (e: Exception) {
                    println("LiftRepository: could not save app state: ${e.message}")
                }
            }
        }
    }

    private fun load() {
        val text = storage.read()
        if (text == null) {
            seedLocal()
            return
        }
        try {
            val root = json.parseToJsonElement(text).jsonObject

            // A version 1 file held a single profile at the root.
            if (!root.containsKey("profiles")) {
                val legacy = json.decodeFromJsonElement(LegacyRootDto.serializer(), root).toProfile()
                _profiles.value = mapOf(legacy.key to legacy)
                _activeKey.value = legacy.key
                return
            }

            val rootDto = json.decodeFromJsonElement(RootDto.serializer(), root)
            val loaded = rootDto.profiles.mapValues { (key, dto) -> dto.toProfile(key) }

            if (loaded.isEmpty()) {
                seedLocal()
                return
            }

            _profiles.value = loaded
            _activeKey.value = if (rootDto.activeKey in loaded.keys) rootDto.activeKey else loaded.keys.first()
        } catch (e: Exception) {
            println("LiftRepository: saved state was unreadable; starting from the default catalog: ${e.message}")
            seedLocal()
        }
    }

    private fun seedLocal() {
        val local = blank(Profile.LOCAL_KEY)
        _profiles.value = mapOf(Profile.LOCAL_KEY to local)
        _activeKey.value = Profile.LOCAL_KEY
    }

    private fun toDto(): RootDto = RootDto(
        version = 2,
        activeKey = _activeKey.value,
        profiles = _profiles.value.mapValues { (_, profile) -> profile.toDto() },
    )

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
    }
}

// ------------------------------------------------------- JSON DTOs & conversions
//
// kotlinx.serialization needs @Serializable types with JSON-safe shapes, so
// the wire format is a small mirror of the domain models rather than
// annotating Profile/Machine/LogEntry directly (keeping those free of a
// persistence-library dependency). Enums round-trip as their `.name`, exactly
// like the original org.json version did.

private fun Profile.toSyncState(profileCount: Int) = SyncState(
    accountEmail = if (connected) accountEmail else null,
    profileEmail = accountEmail,
    profileCount = profileCount,
    spreadsheetId = spreadsheetId,
    spreadsheetUrl = spreadsheetUrl,
    lastSyncAt = lastSyncAt,
    lastError = lastError,
)

@Serializable
private data class MachineDto(
    val id: String,
    val name: String,
    val iconKey: String,
    val group: String,
    val visible: Boolean = true,
    val custom: Boolean = false,
    val sortOrder: Int = 0,
    val lastWeight: Int? = null,
    val lastLoggedAt: Long? = null,
    val illustrated: Boolean = true,
    val lastDifficulty: String? = null,
)

private fun Machine.toDto() = MachineDto(
    id = id,
    name = name,
    iconKey = iconKey,
    group = group.name,
    visible = visible,
    custom = custom,
    sortOrder = sortOrder,
    lastWeight = lastWeight,
    lastLoggedAt = lastLoggedAt,
    illustrated = illustrated,
    lastDifficulty = lastDifficulty?.name,
)

private fun MachineDto.toMachine() = Machine(
    id = id,
    name = name,
    iconKey = iconKey,
    group = MachineGroup.fromName(group),
    visible = visible,
    custom = custom,
    sortOrder = sortOrder,
    lastWeight = lastWeight,
    lastLoggedAt = lastLoggedAt,
    illustrated = illustrated,
    lastDifficulty = Difficulty.fromName(lastDifficulty),
)

@Serializable
private data class LogEntryDto(
    val id: String,
    val machineId: String,
    val machineName: String,
    val weight: Int,
    val loggedAt: Long,
    val synced: Boolean = false,
    val machineGroup: String = MachineGroup.OTHER.name,
    val difficulty: String? = null,
)

private fun LogEntry.toDto() = LogEntryDto(
    id = id,
    machineId = machineId,
    machineName = machineName,
    weight = weight,
    loggedAt = loggedAt,
    synced = synced,
    machineGroup = machineGroup.name,
    difficulty = difficulty?.name,
)

private fun LogEntryDto.toLogEntry() = LogEntry(
    id = id,
    machineId = machineId,
    machineName = machineName,
    weight = weight,
    loggedAt = loggedAt,
    synced = synced,
    machineGroup = MachineGroup.fromName(machineGroup),
    difficulty = Difficulty.fromName(difficulty),
)

@Serializable
private data class ProfileDto(
    val accountEmail: String? = null,
    val connected: Boolean = false,
    val spreadsheetId: String? = null,
    val spreadsheetUrl: String? = null,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
    val machines: List<MachineDto> = emptyList(),
    val log: List<LogEntryDto> = emptyList(),
    val pendingDeletions: List<String> = emptyList(),
    val pawprintsBalance: Int = 0,
    val pawprintsEarnedTotal: Int = 0,
    val unlockedCoachIds: List<Int> = listOf(1),
    val selectedCoachId: Int = 1,
    val unlockedOutfits: List<String> = emptyList(),
    // JSON object keys must be strings; the domain type keeps Int coach ids.
    val equippedOutfits: Map<String, String> = emptyMap(),
)

private fun Profile.toDto() = ProfileDto(
    accountEmail = accountEmail,
    connected = connected,
    spreadsheetId = spreadsheetId,
    spreadsheetUrl = spreadsheetUrl,
    lastSyncAt = lastSyncAt,
    lastError = lastError,
    machines = machines.map { it.toDto() },
    log = log.map { it.toDto() },
    pendingDeletions = pendingDeletions,
    pawprintsBalance = pawprintsBalance,
    pawprintsEarnedTotal = pawprintsEarnedTotal,
    unlockedCoachIds = unlockedCoachIds.toList(),
    selectedCoachId = selectedCoachId,
    unlockedOutfits = unlockedOutfits.toList(),
    equippedOutfits = equippedOutfits.mapKeys { (coachId, _) -> coachId.toString() },
)

private fun ProfileDto.toProfile(key: String): Profile {
    val machineList = machines.map { it.toMachine() }
    return Profile(
        key = key,
        accountEmail = accountEmail,
        machines = if (machineList.isEmpty()) MachineCatalog.defaults()
        else MachineCatalog.mergeNewSeeds(machineList),
        log = log.map { it.toLogEntry() },
        connected = connected,
        spreadsheetId = spreadsheetId,
        spreadsheetUrl = spreadsheetUrl,
        lastSyncAt = lastSyncAt,
        lastError = lastError,
        pendingDeletions = pendingDeletions,
        pawprintsBalance = pawprintsBalance,
        pawprintsEarnedTotal = pawprintsEarnedTotal,
        unlockedCoachIds = unlockedCoachIds.toSet().ifEmpty { setOf(1) },
        selectedCoachId = selectedCoachId,
        unlockedOutfits = unlockedOutfits.toSet(),
        equippedOutfits = equippedOutfits
            .mapNotNull { (k, v) -> k.toIntOrNull()?.let { it to v } }
            .toMap(),
    )
}

@Serializable
private data class RootDto(
    val version: Int = 2,
    val activeKey: String = Profile.LOCAL_KEY,
    val profiles: Map<String, ProfileDto> = emptyMap(),
)

/**
 * Reads the flat, single-profile layout written before profiles — and before
 * gamification — existed. No explicit parsing needed for the pawprint/coach
 * fields: [Profile]'s own constructor defaults (free coach unlocked, zero
 * balance) already cover a file this old.
 */
@Serializable
private data class LegacySyncDto(
    val accountEmail: String? = null,
    val spreadsheetId: String? = null,
    val spreadsheetUrl: String? = null,
    val lastSyncAt: Long? = null,
)

@Serializable
private data class LegacyRootDto(
    val sync: LegacySyncDto? = null,
    val machines: List<MachineDto> = emptyList(),
    val log: List<LogEntryDto> = emptyList(),
    val pendingDeletions: List<String> = emptyList(),
)

private fun LegacyRootDto.toProfile(): Profile {
    val email = sync?.accountEmail?.takeIf { it.isNotEmpty() }
    val machineList = machines.map { it.toMachine() }
    return Profile(
        key = email ?: Profile.LOCAL_KEY,
        accountEmail = email,
        machines = if (machineList.isEmpty()) MachineCatalog.defaults()
        else MachineCatalog.mergeNewSeeds(machineList),
        log = log.map { it.toLogEntry() },
        connected = email != null,
        spreadsheetId = sync?.spreadsheetId?.takeIf { it.isNotEmpty() },
        spreadsheetUrl = sync?.spreadsheetUrl?.takeIf { it.isNotEmpty() },
        lastSyncAt = sync?.lastSyncAt,
        lastError = null,
        pendingDeletions = pendingDeletions,
    )
}
