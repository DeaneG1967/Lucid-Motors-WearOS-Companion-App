package com.droid.lucidgravitywatch.tools

import com.droid.lucidgravitywatch.model.GravityVehicleSnapshot
import com.droid.lucidgravitywatch.model.LucidRegion
import com.droid.lucidgravitywatch.network.LucidApiException
import com.droid.lucidgravitywatch.network.OkHttpLucidGrpcTransport
import com.droid.lucidgravitywatch.protocol.LucidProtobufCodec
import com.droid.lucidgravitywatch.repository.LucidCloudVehicleRepository
import com.droid.lucidgravitywatch.storage.InMemorySessionStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Manual probes of Lucid Cloud's rate limiting, using read-only calls only. They never send
 * vehicle commands and never wake a car. Skipped unless LUCID_PROBE_EMAIL is set:
 *
 *   LUCID_PROBE_EMAIL=... LUCID_PROBE_PASSWORD=... ./gradlew testDebugUnitTest --rerun -i \
 *       --tests '*LucidRateLimitProbe.probeGetUserVehiclesRateLimit'
 *
 * Close the watch app (and ideally the Lucid phone app) first so they don't share the quota.
 * Results are appended to a CSV under build/ as they happen.
 */
class LucidRateLimitProbe {

    private val email: String? = System.getenv("LUCID_PROBE_EMAIL")
    private val password: String? = System.getenv("LUCID_PROBE_PASSWORD")
    private val region: LucidRegion = System.getenv("LUCID_PROBE_REGION")
        ?.let { name -> LucidRegion.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        ?: LucidRegion.US
    private val probeStartMs = System.currentTimeMillis()
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    private lateinit var outFile: File
    private lateinit var transport: OkHttpLucidGrpcTransport
    private lateinit var sessionStore: InMemorySessionStore
    private lateinit var repository: LucidCloudVehicleRepository

    /**
     * Burst, recovery and sustained-rate behaviour of GetUserVehicles (~15 minutes). Found ~5 calls
     * per minute, resetting on the server's clock minute.
     */
    @Test
    fun probeGetUserVehiclesRateLimit() = runBlocking {
        signIn("build/rate-limit-probe.csv")

        // 1. Burst: back-to-back calls until the first rejection
        val burstAllowed = burst("burst-1", ::fetchVehicles)

        // 2. Recovery: how long until calls succeed again, then how much is allowed after that
        val recoveryMs = waitForRecovery("recovery-1")
        val refillAllowed = burst("burst-2", ::fetchVehicles)
        summary("burst-1 allowed $burstAllowed calls; recovered after ${recoveryMs / 1000}s; burst-2 allowed $refillAllowed")

        // 3. Sustained rates, slowest first, each after a cool-down
        for (intervalMs in listOf(10_000L, 7_000L, 5_000L, 3_000L)) {
            val phase = "every-${intervalMs / 1000}s"
            sleep(COOL_DOWN_MS)
            waitForRecovery("$phase-cooldown")
            val (ok, limited) = sustained(phase, intervalMs, SUSTAINED_DURATION_MS, ::fetchVehicles)
            summary("$phase for ${SUSTAINED_DURATION_MS / 1000}s: $ok ok, $limited rate-limited")
        }
        summary("done; results in ${outFile.absolutePath}")
    }

    /**
     * Whether GetVehicleState (VehicleStateService) and GetUserVehicles (LoginSession) share one
     * per-minute allowance or each have their own (~3 minutes). Each experiment starts 2s into a
     * fresh server minute: exhaust one call, then see how many of the other still succeed.
     */
    @Test
    fun probeQuotaSharingBetweenStateCalls() = runBlocking {
        val vehicles = signIn("build/quota-sharing-probe.csv")
        val vehicle = vehicles.maxBy { it.lastUpdatedMs }
        val offsetMs = measureServerClockOffsetMs()
        summary("server clock is ${offsetMs}ms ahead of local; probing ${vehicle.nickname} (${vehicle.vehicleId})")

        val getVehicleState: suspend () -> String = { getVehicleState(vehicle.vehicleId) }

        waitForFreshServerMinute(offsetMs)
        val m1State = burst("m1-GetVehicleState", getVehicleState)
        val m1Vehicles = burst("m1-GetUserVehicles", ::fetchVehicles)
        summary("minute 1: GetVehicleState allowed $m1State, then GetUserVehicles allowed $m1Vehicles")

        waitForFreshServerMinute(offsetMs)
        val m2Vehicles = burst("m2-GetUserVehicles", ::fetchVehicles)
        val m2State = burst("m2-GetVehicleState", getVehicleState)
        summary("minute 2: GetUserVehicles allowed $m2Vehicles, then GetVehicleState allowed $m2State")

        summary("done; results in ${outFile.absolutePath}")
    }

    /**
     * Sustained GetVehicleState at the proposed app rates (~3 minutes): 60s every 3s (idle), then
     * 120s every 1s (after a command). Every response is decoded and state changes are flagged, so
     * locking/unlocking from the phone app during the 1s phase shows how fast the cloud reflects it,
     * and a sleeping car's power state shows whether polling wakes it.
     */
    @Test
    fun probeVehicleStatePollingRates() = runBlocking {
        val vehicles = signIn("build/vehicle-state-polling-probe.csv")
        val vehicle = vehicles.maxBy { it.lastUpdatedMs }
        summary("probing ${vehicle.nickname}; GetUserVehicles says ${vehicle.describeState()}")

        var previous: GravityVehicleSnapshot? = null
        val pollState: suspend () -> String = {
            val state = fetchVehicleState(vehicle)
            val last = previous
            previous = state
            if (last != null && state.describeChanges(last).isNotEmpty()) {
                summary("CHANGE ${state.describeChanges(last)}; car reported at ${serverTime(state.lastUpdatedMs)}")
            }
            state.describeState()
        }

        val (idleOk, idleLimited) = sustained("idle-3s", 3_000L, 60_000L, pollState)
        summary("idle-3s for 60s: $idleOk ok, $idleLimited rate-limited")

        summary("NOW lock, then unlock, the car from the Lucid phone app (2 minutes)")
        val (fastOk, fastLimited) = sustained("fast-1s", 1_000L, 120_000L, pollState)
        summary("fast-1s for 120s: $fastOk ok, $fastLimited rate-limited")
        summary("done; results in ${outFile.absolutePath}")
    }

    private suspend fun signIn(defaultOut: String) = run {
        assumeTrue("Set LUCID_PROBE_EMAIL / LUCID_PROBE_PASSWORD to run", !email.isNullOrBlank())
        outFile = File(System.getenv("LUCID_PROBE_OUT") ?: defaultOut)
        outFile.parentFile?.mkdirs()
        outFile.writeText("elapsed_s,time,phase,call,result,latency_ms,detail\n")

        transport = OkHttpLucidGrpcTransport()
        sessionStore = InMemorySessionStore()
        repository = LucidCloudVehicleRepository(transport, sessionStore)
        val vehicles = repository.login(email!!, password.orEmpty(), region)
        report("setup", 0, "OK", 0, "signed in; ${vehicles.size} vehicle(s)")
        vehicles
    }

    private suspend fun fetchVehicles(): String = "${repository.fetchVehicles().size} vehicle(s)"

    private suspend fun fetchVehicleState(vehicle: GravityVehicleSnapshot): GravityVehicleSnapshot {
        val session = sessionStore.loadSession() ?: error("not signed in")
        val response = transport.unaryCall(
            region = region,
            serviceMethod = METHOD_GET_VEHICLE_STATE,
            requestProtobuf = LucidProtobufCodec.encodeGetVehicleStateRequest(vehicle.vehicleId),
            bearerToken = session.idToken
        )
        return LucidProtobufCodec.decodeGetVehicleStateResponse(response, vehicle)
    }

    private fun GravityVehicleSnapshot.describeState(): String =
        "power=${powerState.name} lock=${lockState.name} frunk=${frunkState.name} " +
            "trunk=${rearTrunkState.name} battery=${batteryPercent.toInt()}% reported=${serverTime(lastUpdatedMs)}"

    private fun GravityVehicleSnapshot.describeChanges(old: GravityVehicleSnapshot): String = listOfNotNull(
        "power ${old.powerState.name}->${powerState.name}".takeIf { powerState != old.powerState },
        "lock ${old.lockState.name}->${lockState.name}".takeIf { lockState != old.lockState },
        "frunk ${old.frunkState.name}->${frunkState.name}".takeIf { frunkState != old.frunkState },
        "trunk ${old.rearTrunkState.name}->${rearTrunkState.name}".takeIf { rearTrunkState != old.rearTrunkState }
    ).joinToString(", ")

    /** Formats a server epoch-ms timestamp as local wall-clock time (server clock, not corrected). */
    private fun serverTime(epochMs: Long): String =
        if (epochMs <= 0L) "?" else Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalTime().format(clock)

    private suspend fun getVehicleState(vehicleId: String): String {
        val session = sessionStore.loadSession() ?: error("not signed in")
        val response = transport.unaryCall(
            region = region,
            serviceMethod = METHOD_GET_VEHICLE_STATE,
            requestProtobuf = LucidProtobufCodec.encodeGetVehicleStateRequest(vehicleId),
            bearerToken = session.idToken
        )
        return "${response.size}B response"
    }

    /** Calls back-to-back until the first rate-limit error (or [MAX_BURST_CALLS]); returns calls allowed. */
    private suspend fun burst(phase: String, call: suspend () -> String): Int {
        for (n in 1..MAX_BURST_CALLS) {
            if (attempt(phase, n, call) == Result.RATE_LIMITED) return n - 1
            sleep(BURST_GAP_MS)
        }
        return MAX_BURST_CALLS
    }

    /** Polls every [RECOVERY_POLL_MS] until a call succeeds; returns how long that took. */
    private suspend fun waitForRecovery(phase: String): Long {
        val startedAt = System.currentTimeMillis()
        var n = 0
        while (attempt(phase, ++n, ::fetchVehicles) != Result.OK) {
            sleep(RECOVERY_POLL_MS)
        }
        return System.currentTimeMillis() - startedAt
    }

    private suspend fun sustained(
        phase: String,
        intervalMs: Long,
        durationMs: Long,
        call: suspend () -> String
    ): Pair<Int, Int> {
        val endAt = System.currentTimeMillis() + durationMs
        var n = 0
        var ok = 0
        var limited = 0
        while (System.currentTimeMillis() < endAt) {
            val callStartedAt = System.currentTimeMillis()
            when (attempt(phase, ++n, call)) {
                Result.OK -> ok++
                Result.RATE_LIMITED -> limited++
                Result.OTHER_ERROR -> Unit
            }
            sleep(intervalMs - (System.currentTimeMillis() - callStartedAt))
        }
        return ok to limited
    }

    private suspend fun attempt(phase: String, n: Int, call: suspend () -> String): Result {
        val startedAt = System.currentTimeMillis()
        return try {
            val detail = call()
            report(phase, n, "OK", System.currentTimeMillis() - startedAt, detail)
            Result.OK
        } catch (err: LucidApiException) {
            val result = if (err.grpcStatus == GRPC_RESOURCE_EXHAUSTED) Result.RATE_LIMITED else Result.OTHER_ERROR
            report(phase, n, "grpc-${err.grpcStatus}", System.currentTimeMillis() - startedAt, err.message)
            result
        } catch (err: Exception) {
            report(phase, n, "error", System.currentTimeMillis() - startedAt, err.toString())
            Result.OTHER_ERROR
        }
    }

    /**
     * Server clock minus local clock, to ~±100ms: polls the gateway's HTTP Date header (1s
     * resolution) until its seconds value ticks over, which pins the server's second boundary.
     */
    private fun measureServerClockOffsetMs(): Long {
        val client = OkHttpClient()
        val request = Request.Builder().url("https://${region.apiHost}/").build()
        fun serverDate(): Pair<Long, Long> {
            val sentAt = System.currentTimeMillis()
            val date = client.newCall(request).execute().use { it.header("Date") } ?: error("no Date header")
            val receivedAt = System.currentTimeMillis()
            val serverMs = ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
            return serverMs to (sentAt + receivedAt) / 2
        }
        val (firstServerMs, _) = serverDate()
        repeat(40) {
            val (serverMs, localMs) = serverDate()
            if (serverMs != firstServerMs) return serverMs - localMs
            sleep(OFFSET_POLL_MS)
        }
        error("server Date header never ticked over")
    }

    /** Sleeps until [SERVER_MINUTE_START_OFFSET_MS] into the next server-clock minute. */
    private fun waitForFreshServerMinute(offsetMs: Long) {
        val serverNow = System.currentTimeMillis() + offsetMs
        val nextMinute = (serverNow / 60_000L + 1) * 60_000L
        val waitMs = nextMinute + SERVER_MINUTE_START_OFFSET_MS - serverNow
        summary("waiting ${waitMs / 1000}s for the next server minute")
        sleep(waitMs)
    }

    private fun report(phase: String, n: Int, result: String, latencyMs: Long, detail: String) {
        val elapsedS = "%.1f".format((System.currentTimeMillis() - probeStartMs) / 1000.0)
        val time = LocalTime.now().format(clock)
        val csvDetail = detail.replace("\"", "'")
        outFile.appendText("$elapsedS,$time,$phase,$n,$result,$latencyMs,\"$csvDetail\"\n")
        println("[$elapsedS s] $phase #$n $result (${latencyMs}ms) $detail")
    }

    private fun summary(text: String) {
        outFile.appendText("# $text\n")
        println("=== $text")
    }

    private fun sleep(ms: Long) {
        if (ms > 0) Thread.sleep(ms)
    }

    private enum class Result { OK, RATE_LIMITED, OTHER_ERROR }

    private companion object {
        const val METHOD_GET_VEHICLE_STATE = "/mobilegateway.protos.VehicleStateService/GetVehicleState"
        const val GRPC_RESOURCE_EXHAUSTED = 8
        const val MAX_BURST_CALLS = 12
        const val BURST_GAP_MS = 250L
        const val RECOVERY_POLL_MS = 2_000L
        const val COOL_DOWN_MS = 60_000L
        const val SUSTAINED_DURATION_MS = 120_000L
        const val OFFSET_POLL_MS = 100L
        const val SERVER_MINUTE_START_OFFSET_MS = 2_000L
    }
}
