package eflect.data;

import static eflect.util.WriterUtil.writeCsv;

import clerk.Processor;
import java.util.ArrayList;
import java.util.HashMap;

/** A processor that writes csv files from samples by sample type. */
public final class Writer implements Processor<Sample, Boolean> {
  private final HashMap<Class<?>, ArrayList<Sample>> data = new HashMap<>();
  protected final String outputPath;

  public Writer(String outputPath) {
    this.outputPath = outputPath;
  }

  @Override
  public final void add(Sample sample) {
    synchronized (data) {
      if (!data.containsKey(sample.getClass())) {
        data.put(sample.getClass(), new ArrayList<>());
      }
      data.get(sample.getClass()).add(sample);
    }
  }

  @Override
  public final Boolean process() {
    for (Class<?> cls : data.keySet()) {
      writeCsv(outputPath, cls.toString() + ".csv", data.get(cls));
    }
    return true;
  }
}
