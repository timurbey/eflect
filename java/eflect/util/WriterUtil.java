package eflect.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Utility to write csv data. */
// TODO(timur): the entire data exchange will eventually have to change, so this will eventually go
// away
public final class WriterUtil {
  private static final Logger logger = LoggerUtil.getLogger();

  public static void writeCsv(String directory, String fileName, Iterable<Sample> samples) {
    try (PrintWriter writer = new PrintWriter(new FileWriter(new File(directory, fileName)))) {
      for (Sample sample : samples) {
        String sampleString = sample.toString();
        if (!sampleString.isEmpty()) {
          writer.println(sampleString);
        }
      }
    } catch (IOException e) {
      logger.log(Level.INFO, "unable to write " + new File(directory, fileName), e);
    }
  }

  public static void writeCsv(
      String directory, String fileName, String header, Iterable<Sample> samples) {
    try (PrintWriter writer = new PrintWriter(new FileWriter(new File(directory, fileName)))) {
      writer.println(header);
      for (Sample sample : samples) {
        String sampleString = sample.toString();
        if (!sampleString.isEmpty()) {
          writer.println(sampleString);
        }
      }
    } catch (IOException e) {
      logger.log(Level.INFO, "unable to write " + new File(directory, fileName), e);
    }
  }
}
