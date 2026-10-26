package co.icesi.fractal.client;

import FractalApp.WorkerJobStat;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

public final class CsvResults {

    private static final String MAIN_HEADER =
        "run_label,job_id,workers,width,height,max_iter,preset,strategy_type,strategy_param,"
            + "tasks,t_sec_ms,t_dist_ms,speedup,efficiency_pct,errors";

    private static final String WORKER_HEADER =
        "run_label,job_id,worker_id,tasks_completed,compute_time_ms";

    private CsvResults() {
    }

    public static void appendMain(String path, String runLabel, String jobId, int workers,
                                  int width, int height, int maxIter, String preset,
                                  int strategyType, int strategyParam, int tasks,
                                  long tSec, long tDist, double speedup,
                                  double efficiency, int errors) throws IOException {
        if (path == null || path.isEmpty()) {
            return;
        }

        double safeDist = Math.max(1, tDist);
        String line = String.format(Locale.US, "%s,%s,%d,%d,%d,%d,%s,%d,%d,%d,%d,%d,%.4f,%.4f,%d%n",
            csv(runLabel), csv(jobId), workers, width, height, maxIter, csv(preset),
            strategyType, strategyParam, tasks, tSec, tDist,
            (double) tSec / safeDist, (double) tSec / safeDist / Math.max(1, workers) * 100.0,
            errors);

        append(path, MAIN_HEADER, line);
    }

    public static void appendWorkers(String path, String runLabel, String jobId,
                                     WorkerJobStat[] stats) throws IOException {
        if (path == null || path.isEmpty() || stats == null || stats.length == 0) {
            return;
        }

        StringBuilder rows = new StringBuilder();
        for (WorkerJobStat stat : stats) {
            rows.append(String.format(Locale.US, "%s,%s,%s,%d,%d%n",
                csv(runLabel), csv(jobId), csv(stat.workerId),
                stat.tasksCompleted, stat.computeTimeMs));
        }

        append(path, WORKER_HEADER, rows.toString());
    }

    private static void append(String path, String header, String rows) throws IOException {
        Path target = new File(path).toPath();
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        boolean writeHeader = !Files.exists(target) || Files.size(target) == 0;
        String content = (writeHeader ? header + System.lineSeparator() : "") + rows;
        Files.writeString(target, content, StandardCharsets.UTF_8,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
