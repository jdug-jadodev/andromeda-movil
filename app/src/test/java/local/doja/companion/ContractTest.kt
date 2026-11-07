package local.doja.companion

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContractTest {
    private fun json(name: String): JSONObject =
        JSONObject(File("contract/home-api-v1/$name").readText())

    @Test
    fun versionMatches() {
        assertEquals(Contract.VERSION, json("policies.json").getInt("contract"))
    }

    @Test
    fun gatewayPolicyMatches() {
        assertEquals(Contract.GATEWAY_POLICY, json("policies.json").getString("gateway"))
    }

    @Test
    fun remoteVoicePolicyMatches() {
        assertEquals(Contract.REMOTE_VOICE_POLICY, json("policies.json").getString("remote_voice"))
    }

    @Test
    fun limitsArePositive() {
        val limits = json("limits.json")
        listOf("command_chars", "message_bytes", "camera_minutes").forEach { name ->
            assertTrue("$name debe ser positivo", limits.getInt(name) > 0)
        }
    }

    @Test
    fun cameraHasOneViewer() {
        assertEquals(1, json("limits.json").getInt("camera_viewers"))
    }
}
