package eflect.data.jiffies;

import eflect.data.Accountant;
import eflect.data.EnergyAccountant;
import eflect.data.EnergyFootprint;
import eflect.data.EnergySample;
import eflect.data.Sample;
import eflect.util.TimeUtil;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;

/**
 * Processor that merges jiffies and energy samples into a single footprint.
 *
 * <p>This processor implements the eflect algorithm by tracking absolute values from samples and
 * computing the difference as needed.
 */
public final class JiffiesEnergyAccountant implements EnergyAccountant {
  private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

  private final int domainCount;
  private final double wrapAroundEnergy;
  private final long[] domainMin;
  private final long[] domainMax;
  private final HashMap<Long, TaskStat> taskStatsMin = new HashMap<>();
  private final HashMap<Long, TaskStat> taskStatsMax = new HashMap<>();
  private final double[] energyMin;
  private final double[] energyMax;

  private Instant start = Instant.MAX;
  private Instant end = Instant.MIN;
  private ArrayList<EnergyFootprint> data;

  public JiffiesEnergyAccountant(int domainCount, double wrapAroundEnergy) {
    this.domainCount = domainCount;
    this.wrapAroundEnergy = wrapAroundEnergy;

    domainMin = new long[domainCount];
    domainMax = new long[domainCount];
    energyMin = new double[domainCount];
    energyMax = new double[domainCount];
  }

  /** Puts the sample data into the correct container and adjust the values. */
  public void add(Sample s) {
    if (s instanceof ProcStatSample) {
      long[] jiffies = ((ProcStatSample) s).getJiffies();
      long[] domainJiffies = new long[domainCount];
      for (int cpu = 0; cpu < CPU_COUNT; cpu++) {
        int domain = cpu / (CPU_COUNT / domainCount);
        domainJiffies[domain] += jiffies[cpu];
      }
      for (int domain = 0; domain < domainCount; domain++) {
        if (domainJiffies[domain] < domainMin[domain] || domainMin[domain] == 0) {
          domainMin[domain] = domainJiffies[domain];
        }
        if (domainJiffies[domain] > domainMax[domain]) {
          domainMax[domain] = domainJiffies[domain];
        }
      }
    } else if (s instanceof ProcTaskSample) {
      for (TaskStat stat : ((ProcTaskSample) s).getTaskStats()) {
        if (!taskStatsMin.containsKey(stat.id)
            || stat.jiffies < taskStatsMin.get(stat.id).jiffies) {
          taskStatsMin.put(stat.id, stat);
        }
        if (!taskStatsMax.containsKey(stat.id)
            || stat.jiffies > taskStatsMax.get(stat.id).jiffies) {
          taskStatsMax.put(stat.id, stat);
        }
      }
    } else if (s instanceof EnergySample) {
      double[][] energyStats = ((EnergySample) s).getEnergy();
      for (int domain = 0; domain < domainCount; domain++) {
        double energy = 0;
        for (double e : energyStats[domain]) {
          energy += e;
        }
        if (energy < energyMin[domain] || energyMin[domain] == 0) {
          energyMin[domain] = energy;
        }
        if (energy > energyMax[domain]) {
          energyMax[domain] = energy;
        }
      }
    } else {
      return; // break out so the timestamp isn't touched
    }

    Instant timestamp = s.getTimestamp();
    start = TimeUtil.min(timestamp, start);
    end = TimeUtil.max(timestamp, end);
  }

  /**
   * Add all samples from the other accountant to this if it is a {@link JiffiesEnergyAccountant}.
   */
  @Override
  public <T extends Accountant<Collection<EnergyFootprint>>> void add(T other) {
    if (other instanceof JiffiesEnergyAccountant) {
      JiffiesEnergyAccountant otherAccountant = ((JiffiesEnergyAccountant) other);
      start = TimeUtil.min(start, otherAccountant.start);
      end = TimeUtil.max(end, otherAccountant.end);
      for (int domain = 0; domain < domainCount; domain++) {
        domainMin[domain] = minUnderZero(domainMin[domain], otherAccountant.domainMin[domain]);
        domainMax[domain] = Math.max(domainMax[domain], otherAccountant.domainMax[domain]);
        energyMin[domain] = minUnderZero(energyMin[domain], otherAccountant.energyMin[domain]);
        energyMax[domain] = Math.max(energyMax[domain], otherAccountant.energyMax[domain]);
      }

      for (long id : otherAccountant.taskStatsMin.keySet()) {
        if (!taskStatsMin.containsKey(id)
            || otherAccountant.taskStatsMin.get(id).jiffies < taskStatsMin.get(id).jiffies) {
          taskStatsMin.put(id, otherAccountant.taskStatsMin.get(id));
        }
        if (!taskStatsMax.containsKey(id)
            || otherAccountant.taskStatsMax.get(id).jiffies > taskStatsMax.get(id).jiffies) {
          taskStatsMax.put(id, otherAccountant.taskStatsMax.get(id));
        }
      }
    }
  }

  /**
   * Attempts to account the stored data.
   *
   * <p>Returns Result.UNACCOUNTABLE if a domain has no jiffies or energy.
   *
   * <p>Returns Result.OVERACCOUNTED if the application jiffies are greater than the domain jiffies.
   *
   * <p>Returns Result.ACCOUNTED otherwise.
   */
  @Override
  public Accountant.Result isAccountable() {
    // check the timestamps
    if (TimeUtil.equal(start, end)
        || TimeUtil.equal(start, Instant.MAX)
        || TimeUtil.equal(end, Instant.MIN)) {
      return Accountant.Result.UNACCOUNTABLE;
    }

    // check the energy and cpu jiffies
    double[] energy = new double[domainCount];
    long[] domainJiffies = new long[domainCount];
    for (int domain = 0; domain < domainCount; domain++) {
      domainJiffies[domain] = domainMax[domain] - domainMin[domain];
      if (domainJiffies[domain] == 0) {
        return Accountant.Result.UNACCOUNTABLE;
      }
      energy[domain] = energyMax[domain] - energyMin[domain];
      if (energy[domain] == 0) {
        return Accountant.Result.UNACCOUNTABLE;
      } else if (energy[domain] < 0) {
        energy[domain] += wrapAroundEnergy;
      }
    }

    // check the task jiffies
    long[] applicationJiffies = new long[domainCount];
    ArrayList<TaskStat> tasks = new ArrayList<>();
    for (long id : taskStatsMin.keySet()) {
      TaskStat task = taskStatsMin.get(id);
      long jiffies = taskStatsMax.get(id).jiffies - task.jiffies;
      int domain = task.cpu / (CPU_COUNT / domainCount);
      applicationJiffies[domain] += jiffies;
      tasks.add(new TaskStat(task.id, task.name, domain, jiffies));
    }

    for (int domain = 0; domain < domainCount; domain++) {
      if (applicationJiffies[domain] == 0) {
        return Accountant.Result.UNACCOUNTABLE;
      }
    }

    // if we got here, we can produce **something**
    // Accountant.Result result = Accountant.Result.ACCOUNTED;
    for (int domain = 0; domain < domainCount; domain++) {
      // check if the application jiffies exceeds the system jiffies; if it does, we use
      // total app jiffies instead of system reported
      // TODO(timur): reason about other policies
      if (applicationJiffies[domain] > domainJiffies[domain]) {
        // data = accountTasks(energy, applicationJiffies, tasks);
        // return Accountant.Result.OVERACCOUNTED;
      }
    }
    data = accountTasks(energy, domainJiffies, tasks);
    return Accountant.Result.ACCOUNTED;
  }

  /** Compute an {@link EnergyFootprint} from the stored data. */
  public Collection<EnergyFootprint> process() {
    if (data != null || isAccountable() == Accountant.Result.ACCOUNTED) {
      start = end;
      for (int domain = 0; domain < domainCount; domain++) {
        domainMin[domain] = domainMax[domain];
        energyMin[domain] = energyMax[domain];
      }
      taskStatsMin.clear();
      taskStatsMin.putAll(taskStatsMax);
    }
    return data;
  }

  private ArrayList<EnergyFootprint> accountTasks(
      double[] domainEnergy, long[] domainJiffies, ArrayList<TaskStat> tasks) {
    ArrayList<EnergyFootprint> footprints = new ArrayList<>();
    for (TaskStat task : tasks) {
      double energy =
          domainEnergy[task.cpu]
              * (double) Math.min(task.jiffies, domainJiffies[task.cpu])
              / domainJiffies[task.cpu];
      if (energy > 0) {
        footprints.add(
            new EnergyFootprint.Builder()
                .setId(task.id)
                .setName(task.name)
                .setStart(start)
                .setEnd(end)
                .setEnergy(energy)
                .build());
      }
    }
    return footprints;
  }

  private long minUnderZero(long first, long second) {
    if (first < second || first != 0) {
      return first;
    } else {
      return second;
    }
  }

  private double minUnderZero(double first, double second) {
    if (first < second || first != 0) {
      return first;
    } else {
      return second;
    }
  }
}
