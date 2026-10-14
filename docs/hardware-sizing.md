# Hardware Sizing and Capacity Planning

> Adapted from canary/docs/hardware-sizing.md for the Kotlin/JVM engine.
> Canary-Kotlin status: the upstream measurement methodology is preserved; JVM
> sizing guidance (heap, GC, Netty direct memory) is added. Upstream numbers are
> labeled as upstream C++ illustrative values, not Kotlin measurements.

Size a Canary Kotlin server for its map, scripts, player activity and target
response times. Estimate a range for that workload and build, with reserve for
busy periods; online population alone does not determine RAM or CPU
requirements. On the JVM, the split between the Java heap, Netty direct memory,
metaspace and the operating system changes how the same workload maps to RAM.

## Measure Player Activity

The same number of connected players can generate very different workloads:

| Activity | Work to account for |
| --- | --- |
| Idle or online training | Connected player state, periodic updates, training actions and any recurring spells or custom scripts. Training still consumes resources. |
| Hunting | Movement, pathfinding, active monster AI, combat, loot, scripts and updates sent to nearby players. |
| Crowded events or PvP | Interactions between nearby creatures, area effects and updates reaching many spectators. Concentrated activity can cost more than the same population spread across the map. |

Compare similar activity mixes; a multiplier such as "one hunter equals five
trainers" needs measurement. Count actual connected sessions consistently:
website/status counters may apply filters, and offline training is not a
connected session. Total spawned monsters also differ from active monsters.

## Collect a Representative Baseline

Start with a full weekly cycle, including busy hours, saves and events; two to
four weeks can improve coverage. Even a long record with little load variation
cannot establish the cost of growth.

Record these measurements over aligned time windows:

- **Population:** actual online sessions and available aggregate activity counters.
- **CPU:** process, dispatcher and worker utilization, plus host contention and
  VM limits. On the JVM, GC threads and JIT compiler threads also consume CPU;
  prefer process-wide CPU accounting over summing application threads.
- **Memory:** JVM heap used/committed/max, GC pause time and frequency, Netty
  direct memory, metaspace, RSS, available host memory, swap and memory
  pressure. Account for PostgreSQL 16 and other services on the machine.
- **Service quality:** queue/scheduling delays, errors and rejected work where
  exposed. Keep averages, p95/p99, maxima and overload duration, with the
  sampling window stated. A p99 covers 99% of observations; it is not a maximum.
- **Context:** build, configuration, map, hardware and uptime. Separate records
  after substantial changes.

On the JVM, collect CPU and memory with:

```bash
jcmd <pid> GC.heap_info
jcmd <pid> Thread.print
jstat -gcutil <pid> <interval>
jcmd <pid> VM.native_memory summary   # when -XX:NativeMemoryTracking=summary is enabled
```

Optionally use JDK Flight Recorder or async-profiler for sampled CPU and
allocation profiles. Unlike upstream thread naming (`canary-dispatch`,
`canary-wrk-N`, `canary-ai`), the JVM uses runtime-chosen thread names; identify
the single world-mutation dispatcher from a thread dump and watch it grow with
player/monster activity (`GameDispatcher` executes serial lanes on one
`Dispatchers.Default.limitedParallelism(1)` worker).

Consider cheap OS counters every 5 seconds and population snapshots every
30–60 seconds. Validate overhead, bound retention and analyze outside the game
process. Five-second averages can hide shorter spikes. Avoid frequent scans of
game entities, full memory-map scans, continuous profiling and per-player
logging for this initial estimate.

Utilization should be interpreted alongside latency and saturation, as
described in [Google SRE's monitoring guidance](https://sre.google/sre-book/monitoring-distributed-systems/).
Linux documents [RSS and available memory](https://docs.kernel.org/filesystems/proc.html)
and [resource pressure (PSI)](https://docs.kernel.org/accounting/psi.html).
RSS is approximate and does not represent total host memory demand.

## JVM Memory Placement

Budget RAM in layers:

| Layer | Typical shape | Notes |
| --- | --- | --- |
| Java heap (`-Xmx`) | Largest single share | Holds world objects, caches, loaded content. Keep `-Xms` close to `-Xmx` in production to avoid resizing pauses. |
| Netty direct memory | Hundreds of MiB to low GiB | Pooled `ByteBuf` buffers live outside the heap. Bound with `-XX:MaxDirectMemorySize` and monitor; unbounded direct memory can OOM the process while the heap looks healthy. |
| Metaspace | Tens of MiB | Class metadata; bounded by `-XX:MaxMetaspaceSize` if the host must be protected. |
| Thread stacks | ~1 MiB per thread | Netty event loop groups, dispatcher/barrier workers, HikariCP and GC threads all add up. |
| GC/JIT overhead | 5–15% of process CPU | Native structures and compilation work. |

Starting points (planning examples to validate, not measured requirements):

```text
Small       2 CPU cores, heap -Xmx2g,  direct memory ~256m,  4 GB total RAM
Medium      4 CPU cores, heap -Xmx5g,  direct memory ~512m,  8 GB total RAM
Large       8+ CPU cores, heap -Xmx12g+, direct memory ~1g,  16+ GB total RAM
PostgreSQL  separate host or dedicated share of RAM (see below)
```

G1 is the default collector for typical server-class JVMs on Java 25 and is a
reasonable default here. Evaluate concurrent collectors (ZGC/Shenandoah) only
with a measured pause or throughput problem, and keep database latency in the
comparison. See the [Java tuning documentation](https://docs.oracle.com/en/java/javase/25/gctuning/)
for collector behavior.

## Calculate Average and Incremental Resource Use

Express process CPU in **core equivalents** so percentages have an explicit
denominator:

```text
CPU core equivalents = change in process CPU seconds / elapsed wall seconds
```

For example, 0.40 core equivalents means 40% of one logical CPU, not 40% of the
whole machine. A multithreaded JVM can exceed 1.0 (Netty, GC and barrier
workers are separate threads).

For comparable, stable windows, let `N` be the average online session count,
`R` the average process resident memory (or heap + direct memory) in MiB and `C`
the CPU core equivalents:

```text
Average RAM per online session = R / N                         (N > 0)
Average CPU per online session = C / N                         (N > 0)
Estimated additional RAM per session = (R2 - R1) / (N2 - N1)   (N2 > N1)
Estimated additional CPU per session = (C2 - C1) / (N2 - N1)   (N2 > N1)
```

**Illustrative upstream values only; these are not measured Canary-Kotlin
requirements** (they come from the upstream C++ profiling notes):

| Comparable window | Average online sessions | Average RSS | CPU core equivalents |
| --- | ---: | ---: | ---: |
| A | 100 | 4,000 MiB | 0.40 |
| B | 200 | 5,000 MiB | 0.65 |

The average RAM per session falls from **40 MiB** to **25 MiB**, because shared
memory is spread across more sessions. The estimated incremental use is
**10 MiB** and **0.0025 core equivalents** per additional session. The implied
RAM baseline is **3,000 MiB**: doubling population need not double total memory.

Real estimates need more windows with comparable activity, world state and
uptime. Map loading, items, Lua state, caches and allocator retention affect
RAM; heap used may stay high after players leave until a collection reclaims
it, and RSS may never return to baseline. Investigate persistent growth at
similar loads. Estimate peaks separately from averages, validate predictions on
later days, and report uncertainty. Do not project far beyond the observed
population or infer a player limit from average CPU alone: consumed CPU can
plateau while queues and delays continue growing.

## Choose Hardware and Review Capacity

Compare machines using the same representative Canary Kotlin workload in a test
environment. Per-core performance, cache, memory latency/bandwidth, JVM flags,
GC choice and VPS contention matter. DDR4/DDR5 describes performance
characteristics, not a conversion of required RAM capacity. GHz and vCPU counts
alone are insufficient; the [SPEC overview](https://www.spec.org/cpu2017/Docs/overview.html)
explains why the actual application and workload are the best comparison.

- **Increase capacity** before expected busy-period demand exhausts the limiting
  resource. On the JVM, the limiting resource is often heap headroom before GC
  pauses become visible, or direct memory before allocation failures.
- **Keep capacity** when service targets and reserve are met, or evidence for a
  smaller machine is insufficient.
- **Consider reducing capacity** after sustained spare capacity across busy
  periods, with a smaller candidate able to cover peaks, retained memory and
  other services. An initial reserve of 25–30% unused capacity is a planning
  example to validate, not a universal safe threshold. A quiet night or restart
  is insufficient evidence.

Use different thresholds and observation periods for increases and reductions,
with a minimum interval between changes. Update recommendations as data arrives;
schedule resizing around maintenance and provider constraints.

State the workload, observed population range, limiting resource, uncertainty
and reserve with each recommendation. For financial cost, divide the period's
actual hosting cost by its total connected player-hours; an upgrade instead
depends on plan price differences and migration costs.
