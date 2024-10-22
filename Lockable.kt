package com.github.antonirokitnicki.loki

import java.util.*
import java.util.concurrent.Semaphore

abstract class Lockable<E : Enum<E>>(enumClass: Class<E>) {
    private val locks = EnumMap<E, Semaphore>(enumClass)
    private val currentlyLocked: EnumSet<E> = EnumSet.noneOf(enumClass)

    fun isLocked(value: E): Boolean = currentlyLocked.contains(value)

    fun lock(value: E) {
        val lock = locks.computeIfAbsent(value) { Semaphore(1) }
        lock.acquire()
        currentlyLocked.add(value)
    }

    fun unlock(value: E) {
        locks[value]?.also {
            it.release()
            currentlyLocked.remove(value)
            locks.remove(value)
        }
    }
}

enum class SystemOperation {
    CLEANUP, MAINTENANCE
}

class MaintenanceService : Lockable<SystemOperation>(SystemOperation::class.java) {
    fun run() {
        println("Service running")
        Thread.sleep(1_000)
        lock(SystemOperation.MAINTENANCE)
        println("Service done")
    }
}

class CleanupJob : Lockable<SystemOperation>(SystemOperation::class.java) {
    fun run() {
        println("Cleanup running")
        Thread.sleep(1_000)
        lock(SystemOperation.CLEANUP)
        println("Cleanup done")
    }
}

enum class UnlockOrder {
    SERVICE_FIRST, CLEANUP_FIRST
}

fun main() {
    UnlockOrder.entries.forEach { testCase ->
        println("\nRunning test case: $testCase")
        val service = MaintenanceService().apply { lock(SystemOperation.MAINTENANCE) }
        val cleanup = CleanupJob().apply { lock(SystemOperation.CLEANUP) }

        Thread {
            service.run()
        }.start()

        Thread {
            cleanup.run()
        }.start()

        while (!(service.isLocked(SystemOperation.MAINTENANCE) && cleanup.isLocked(SystemOperation.CLEANUP))) {
            Thread.sleep(10)
        }

        when (testCase) {
            UnlockOrder.SERVICE_FIRST -> {
                println("Unlocking service first")
                service.unlock(SystemOperation.MAINTENANCE)
                Thread.sleep(1_000)
                cleanup.unlock(SystemOperation.CLEANUP)
            }
            UnlockOrder.CLEANUP_FIRST -> {
                println("Unlocking cleanup first")
                cleanup.unlock(SystemOperation.CLEANUP)
                Thread.sleep(1_000)
                service.unlock(SystemOperation.MAINTENANCE)
            }
        }

        Thread.sleep(5_000)

    }
}