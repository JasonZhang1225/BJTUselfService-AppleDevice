package team.bjtuss.bjtuselfservice.shared.feature.shell

import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppCommandBusTest {
    @Test
    fun commandIsDeliveredOnceToActiveShell() = runBlocking {
        val bus = AppCommandBus()
        val received = async(start = CoroutineStart.UNDISPATCHED) { bus.commands.first() }

        assertTrue(bus.send(AppCommand.NAVIGATE_SETTINGS))

        assertEquals(AppCommand.NAVIGATE_SETTINGS, received.await())
    }

    @Test
    fun commandSentWithoutShellIsNotReplayedAfterLogin() = runBlocking {
        val bus = AppCommandBus()
        assertTrue(bus.send(AppCommand.NAVIGATE_HOMEWORK))
        val received = async(start = CoroutineStart.UNDISPATCHED) { bus.commands.first() }

        assertTrue(bus.send(AppCommand.NAVIGATE_HOME))

        assertEquals(AppCommand.NAVIGATE_HOME, received.await())
    }
}
