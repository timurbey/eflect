package eflect.testing.data.jiffies;

import eflect.data.jiffies.ProcStatSample;
import java.time.Instant;

/** A class that builds a {@link ProcStatSample). */
public final class FakeProcStat {
  private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
  private static final String STATS_END = FakeProcUtil.createDummyStats(9);

  private static final String buildStat(int cpu, long jiffies) {
    return String.join(
        " ", String.join(" ", "cpu" + Integer.toString(cpu), Long.toString(jiffies)), STATS_END);
  }

  private Instant timestamp = Instant.EPOCH;
  private final long[] jiffies = new long[CPU_COUNT];

  public FakeProcStat() {}

  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }

  public void setJiffies(int cpu, long jiffies) {
    this.jiffies[cpu] = jiffies;
  }

  public void reset() {
    timestamp = Instant.EPOCH;
    for (int cpu = 0; cpu < CPU_COUNT; cpu++) {
      jiffies[cpu] = 0;
    }
  }

  public ProcStatSample read() {
    String[] stats = new String[CPU_COUNT];
    for (int i = 0; i < CPU_COUNT; i++) {
      stats[i] = buildStat(i, jiffies[i]);
    }
    return new ProcStatSample(timestamp, stats);
  }
}
