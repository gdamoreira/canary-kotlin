package org.opentibia.canary

import kotlin.system.exitProcess
import org.opentibia.canary.bootstrap.ServerLaunch

fun main(args: Array<String>) {
    exitProcess(ServerLaunch.run(args))
}
