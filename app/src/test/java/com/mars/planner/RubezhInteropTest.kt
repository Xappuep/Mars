package com.mars.planner

import com.google.common.truth.Truth.assertThat
import com.mars.planner.sync.ENTITY_PROJECT
import com.mars.planner.sync.ENTITY_TASK
import com.mars.planner.sync.OP_CREATE
import com.mars.planner.sync.OP_DELETE
import com.mars.planner.sync.SyncOperation
import com.mars.planner.sync.SyncPackage
import com.mars.planner.sync.VersionHash
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.UUID

class RubezhInteropTest {
    @Test
    fun outgoingPackageIsAcceptedByPythonProtocolValidator() {
        assumeTrue(protocolResourceFile().exists())
        assumeTrue(canRunPython())

        val projectPayload = VersionHash.projectPayload(
            syncUuid = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            name = "Дом",
            description = "",
            archived = false,
            createdAtUtc = "2026-09-01T10:00:00+00:00"
        )
        val taskPayload = VersionHash.taskPayload(
            syncUuid = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
            title = "Купить краску",
            description = "",
            projectUuid = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
            priority = "normal",
            dueAtUtc = "2026-09-10T15:00:00.654321+00:00",
            status = "open",
            createdAtUtc = "2026-09-01T11:00:00.123456+00:00"
        )
        val standalonePayload = VersionHash.taskPayload(
            syncUuid = "cccccccc-cccc-cccc-cccc-cccccccccccc",
            title = "Позвонить домой",
            description = "",
            projectUuid = null,
            priority = "low",
            dueAtUtc = null,
            status = "open",
            createdAtUtc = "2026-09-02T08:30:00+00:00"
        )
        val pkg = SyncPackage(
            packageId = UUID.fromString("dddddddd-dddd-5ddd-8ddd-dddddddddddd").toString(),
            senderDeviceId = "22222222-2222-2222-2222-222222222222",
            createdAtUtc = "2026-09-08T12:00:00+00:00",
            operations = listOf(
                SyncOperation(
                    operationId = "11111111-1111-4111-8111-111111111111",
                    entityType = ENTITY_PROJECT,
                    entityUuid = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
                    op = OP_CREATE,
                    baseHash = null,
                    newHash = VersionHash.versionHash(projectPayload),
                    payload = projectPayload,
                    deviceId = "22222222-2222-2222-2222-222222222222"
                ),
                SyncOperation(
                    operationId = "22222222-2222-4222-8222-222222222222",
                    entityType = ENTITY_TASK,
                    entityUuid = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb",
                    op = OP_CREATE,
                    baseHash = null,
                    newHash = VersionHash.versionHash(taskPayload),
                    payload = taskPayload,
                    deviceId = "22222222-2222-2222-2222-222222222222"
                ),
                SyncOperation(
                    operationId = "33333333-3333-4333-8333-333333333333",
                    entityType = ENTITY_TASK,
                    entityUuid = "cccccccc-cccc-cccc-cccc-cccccccccccc",
                    op = OP_CREATE,
                    baseHash = null,
                    newHash = VersionHash.versionHash(standalonePayload),
                    payload = standalonePayload,
                    deviceId = "22222222-2222-2222-2222-222222222222"
                ),
                SyncOperation(
                    operationId = "44444444-4444-4444-8444-444444444444",
                    entityType = ENTITY_TASK,
                    entityUuid = "eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee",
                    op = OP_DELETE,
                    baseHash = "deadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef",
                    newHash = null,
                    payload = null,
                    deviceId = "22222222-2222-2222-2222-222222222222"
                )
            )
        )
        val json = pkg.toJson().toString()

        val result = runPythonValidation(json)
        assumeTrue(result.second, result.first == 0)
        assertThat(result.second).contains("OK")

        val parsed = JSONObject(json)
        assertThat(parsed.optInt("protocol_version")).isEqualTo(1)
        assertThat(parsed.getString("sender_device_id")).isEqualTo("22222222-2222-2222-2222-222222222222")
        assertThat(parsed.getJSONArray("operations").getJSONObject(1).getJSONObject("payload").isNull("project_uuid")).isFalse()
        assertThat(parsed.getJSONArray("operations").getJSONObject(2).getJSONObject("payload").isNull("project_uuid")).isTrue()
        assertThat(json).contains("Купить краску")
        assertThat(json).doesNotContain("sync_key")
        assertThat(json).doesNotContain("device_token")
        assertThat(json).doesNotContain("theme")
        assertThat(json).doesNotContain("demo")
    }

    private fun runPythonValidation(packageJson: String): Pair<Int, String> {
        val protocolFile = protocolResourceFile()
        val packageFile = File.createTempFile("mars-package-", ".json")
        packageFile.writeText(packageJson, Charsets.UTF_8)
        val script = """
import importlib.util, json, pathlib, sys
spec = importlib.util.spec_from_file_location("proto", sys.argv[1])
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)
pkg = json.loads(pathlib.Path(sys.argv[2]).read_text(encoding="utf-8"))
assert pkg["protocol_version"] == 1
assert pkg["sender_device_id"] == "22222222-2222-2222-2222-222222222222"
ops = pkg["operations"]
assert [op["entity_type"] for op in ops[:2]] == ["project", "task"]
for op in ops:
    assert op["protocol_version"] == 1
    assert op["device_id"] == pkg["sender_device_id"]
    if op["op"] == "delete":
        assert op["payload"] is None
        assert op["new_hash"] is None
    else:
        assert mod.version_hash(op["payload"]) == op["new_hash"]
        assert "updated_at" not in op["payload"]
        assert "device_token" not in op["payload"]
        assert "theme" not in op["payload"]
print("OK")
""".trimIndent()
        val process = ProcessBuilder(pythonBinary(), "-X", "utf8", "-c", script, protocolFile.absolutePath, packageFile.absolutePath)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
        val exit = process.waitFor()
        return exit to output
    }

    private fun protocolResourceFile(): File {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("rubezh_protocol_v1_a804.py")) {
            "Python protocol resource missing"
        }
        val target = File.createTempFile("rubezh-protocol-", ".py")
        stream.use { input -> target.outputStream().use(input::copyTo) }
        return target
    }

    private fun pythonBinary(): String =
        System.getenv("PYTHON")?.takeIf { it.isNotBlank() }
            ?: if ((System.getProperty("os.name") ?: "").lowercase(Locale.ROOT).contains("win")) "python" else "python3"

    private fun canRunPython(): Boolean = runCatching {
        val process = ProcessBuilder(pythonBinary(), "-c", "print('ok')").redirectErrorStream(true).start()
        process.waitFor() == 0
    }.getOrDefault(false)
}
