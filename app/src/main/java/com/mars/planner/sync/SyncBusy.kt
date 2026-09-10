package com.mars.planner.sync

/**
 * Общий каркас busy-флага для UI синхронизации.
 * Гарантирует снятие busy даже при неожиданном исключении.
 */
suspend fun runWithBusyFlag(setBusy: (Boolean) -> Unit, block: suspend () -> Unit) {
    setBusy(true)
    try {
        block()
    } finally {
        setBusy(false)
    }
}
