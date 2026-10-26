package co.icesi.fractal.client;

import FractalApp.*;
import co.icesi.fractal.core.FractalRegion;
import co.icesi.fractal.strategy.PartitionStrategyFactory;
import co.icesi.fractal.strategy.TaskPartitionStrategy;
import com.zeroc.Ice.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ExperimentRunner {

    private static final int[][] TEST_CASES = {
        {0, 2},
        {0, 4},
        {0, 16},
        {1, 32},
        {1, 64},
        {1, 128},
        {1, 256}
    };

    public static void main(String[] args) {
        String masterHost = (args.length >= 1) ? args[0] : "localhost";
        int masterPort = (args.length >= 2) ? Integer.parseInt(args[1]) : 10000;
        int width = (args.length >= 3) ? Integer.parseInt(args[2]) : 3840;
        int height = (args.length >= 4) ? Integer.parseInt(args[3]) : 2160;
        int maxIter = (args.length >= 5) ? Integer.parseInt(args[4]) : 5000;
        String presetName = (args.length >= 6) ? args[5] : "seahorse";
        int repetitions = (args.length >= 7) ? Math.max(1, Integer.parseInt(args[6])) : 1;
        String csvPath = (args.length >= 8) ? args[7] : "results/experiment1.csv";
        String workerCsvPath = (args.length >= 9) ? args[8] : "results/experiment1_worker_stats.csv";

        FractalRegion region = FractalRegion.fromName(presetName);
        double xMin = region.getXMin();
        double xMax = region.getXMax();
        double yMin = region.getYMin();
        double yMax = region.getYMax();

        if (width > 0 && height > 0) {
            double aspect = (double) width / (double) height;
            double realHeight = yMax - yMin;
            xMax = xMin + realHeight * aspect;
        }

        InitializationData initData = new InitializationData();
        initData.properties = Util.createProperties(args);
        initData.properties.setProperty("Ice.MessageSizeMax", "204800");

        try (Communicator communicator = Util.initialize(initData)) {
            String masterEndpoint = String.format("MasterService:tcp -h %s -p %d", masterHost, masterPort);
            MasterServicePrx master = MasterServicePrx.checkedCast(communicator.stringToProxy(masterEndpoint));

            if (master == null) {
                System.err.println("[ERROR] No se pudo conectar al Master.");
                System.exit(1);
            }

            int p = master.getWorkerCount();
            if (p == 0) {
                System.err.println("[ERROR] No hay Workers registrados.");
                System.exit(1);
            }

            System.out.println("=================================================");
            System.out.println("     EXPERIMENTO 1: GRANULARIDAD DE TAREAS");
            System.out.println("=================================================");
            System.out.printf("Region / Preset: %s (%s)%n", region.getName(), region.getId());
            System.out.printf("Resolucion:      %dx%d | MaxIter: %d | Workers: %d | Repeticiones: %d%n%n",
                width, height, maxIter, p, repetitions);

            String cachePath = System.getenv().getOrDefault("FRACTAL_BASELINE_CACHE", "");
            BaselineCache.Baseline baseline = BaselineCache.loadOrCompute(
                width, height, xMin, xMax, yMin, yMax, maxIter, cachePath);
            long tSec = baseline.tSec;
            System.out.printf("T_sec = %d ms (%.2f s)%n%n", tSec, tSec / 1000.0);

            List<CaseResult> results = new ArrayList<>();
            boolean validationFailed = false;

            for (int[] testCase : TEST_CASES) {
                int strategyType = testCase[0];
                int strategyParam = testCase[1];
                String strategyName = (strategyType == 0)
                    ? "FixedCount (Franjas)" : "FixedTileSize (Baldosas)";

                String baseJobId = "exp1-" + UUID.randomUUID().toString().substring(0, 8);
                RenderConfig countConfig = new RenderConfig(
                    baseJobId, width, height, xMin, xMax, yMin, yMax,
                    maxIter, strategyType, strategyParam);
                TaskPartitionStrategy strategy = PartitionStrategyFactory.createStrategy(strategyType, strategyParam);
                int tasksCount = strategy.partition(countConfig).size();

                List<Long> distTimes = new ArrayList<>();
                int caseErrors = 0;

                for (int rep = 1; rep <= repetitions; rep++) {
                    String jobId = baseJobId + "-r" + rep;
                    RenderConfig config = new RenderConfig(
                        jobId, width, height, xMin, xMax, yMin, yMax,
                        maxIter, strategyType, strategyParam);

                    long t0Dist = System.currentTimeMillis();
                    int[][] distMatrix = master.renderFractal(config);
                    long tDist = System.currentTimeMillis() - t0Dist;
                    distTimes.add(tDist);

                    int errors = countErrors(baseline.matrix, distMatrix);
                    caseErrors += errors;
                    double speedup = (double) tSec / (double) Math.max(1, tDist);
                    double efficiency = (speedup / p) * 100.0;

                    String runLabel = String.format(Locale.US, "exp1_%s_%d_r%d",
                        (strategyType == 0 ? "count" : "tile"), strategyParam, rep);
                    CsvResults.appendMain(csvPath, runLabel, jobId, p, width, height, maxIter,
                        presetName, strategyType, strategyParam, tasksCount,
                        tSec, tDist, speedup, efficiency, errors);
                    CsvResults.appendWorkers(workerCsvPath, runLabel, jobId, master.getLastJobStats());

                    System.out.printf(Locale.US,
                        "[EXP1] %s=%d rep=%d/%d | T_dist=%d ms | S=%.2fx | E=%.1f%% | errores=%d%n",
                        (strategyType == 0 ? "N" : "tile"), strategyParam, rep, repetitions,
                        tDist, speedup, efficiency, errors);
                }

                long medianDist = median(distTimes);
                double speedup = (double) tSec / (double) Math.max(1, medianDist);
                double efficiency = (speedup / p) * 100.0;
                results.add(new CaseResult(strategyName, strategyParam, tasksCount,
                    medianDist, speedup, efficiency, caseErrors));

                if (caseErrors > 0) {
                    validationFailed = true;
                    System.err.printf("[EXP1] [FALLO] %d diferencias en %s=%d; se detiene el experimento.%n",
                        caseErrors, (strategyType == 0 ? "N" : "tile"), strategyParam);
                    break;
                }
            }

            printMarkdownTable(results, tSec, p, repetitions);
            System.out.printf("Resultados crudos: %s%n", csvPath);
            System.out.printf("Metricas por worker: %s%n", workerCsvPath);

            if (validationFailed) {
                System.exit(1);
            }

        } catch (java.lang.Exception e) {
            System.err.println("[ERROR] Error en el ejecutor de experimentos: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static int countErrors(int[][] baseline, int[][] distributed) {
        if (baseline.length != distributed.length) {
            return -1;
        }
        int errors = 0;
        for (int y = 0; y < baseline.length; y++) {
            if (baseline[y].length != distributed[y].length) {
                return -1;
            }
            for (int x = 0; x < baseline[y].length; x++) {
                if (baseline[y][x] != distributed[y][x]) {
                    errors++;
                }
            }
        }
        return errors;
    }

    private static long median(List<Long> values) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int n = sorted.size();
        if (n == 0) {
            return 0;
        }
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return Math.round((sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0);
    }

    private static void printMarkdownTable(List<CaseResult> results, long tSec, int workers, int repetitions) {
        System.out.println();
        System.out.printf("T_sec de referencia: %d ms | Workers: %d | Repeticiones: %d%n%n",
            tSec, workers, repetitions);
        System.out.println("| Estrategia | Parametro | Tareas | T_sec (ms) | T_dist mediana (ms) | Speedup (S) | Eficiencia (E) | Errores |");
        System.out.println("| :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: |");
        for (CaseResult result : results) {
            System.out.printf(Locale.US, "| %s | %d | %d | %d | %d | %.2fx | %.1f%% | %d |%n",
                result.strategyName, result.param, result.tasks, tSec, result.tDist,
                result.speedup, result.efficiency, result.errors);
        }
    }

    private static final class CaseResult {
        private final String strategyName;
        private final int param;
        private final int tasks;
        private final long tDist;
        private final double speedup;
        private final double efficiency;
        private final int errors;

        private CaseResult(String strategyName, int param, int tasks, long tDist,
                           double speedup, double efficiency, int errors) {
            this.strategyName = strategyName;
            this.param = param;
            this.tasks = tasks;
            this.tDist = tDist;
            this.speedup = speedup;
            this.efficiency = efficiency;
            this.errors = errors;
        }
    }
}
