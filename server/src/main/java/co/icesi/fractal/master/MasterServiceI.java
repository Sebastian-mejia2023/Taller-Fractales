package co.icesi.fractal.master;

import FractalApp.*;
import co.icesi.fractal.strategy.PartitionStrategyFactory;
import co.icesi.fractal.strategy.TaskPartitionStrategy;
import com.zeroc.Ice.Current;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MasterServiceI implements MasterService {

    private static final long JOB_TIMEOUT_MINUTES = 30;
    private static final boolean VERBOSE = Boolean.parseBoolean(
        System.getenv().getOrDefault("FRACTAL_VERBOSE", "false"));

    private final Map<String, WorkerObserverPrx> workers = new ConcurrentHashMap<>();
    private final Object jobLock = new Object();

    private volatile JobContext activeJob;
    private volatile WorkerJobStat[] lastJobStats = new WorkerJobStat[0];
    private volatile String lastJobId = "";

    @Override
    public int[][] renderFractal(RenderConfig config, Current current) throws FractalException {
        if (workers.isEmpty()) {
            throw new FractalException("No hay Workers registrados en el Master. Inicie al menos un Worker.");
        }
        if (config.width <= 0 || config.height <= 0 || config.maxIterations <= 0) {
            throw new FractalException("Configuracion de renderizado invalida.");
        }
        if (config.jobId == null || config.jobId.isEmpty()) {
            config.jobId = "job-" + UUID.randomUUID();
        }

        TaskPartitionStrategy strategy = PartitionStrategyFactory.createStrategy(
            config.strategyType, config.strategyParam
        );
        List<TaskTile> tiles = strategy.partition(config);
        if (tiles.isEmpty()) {
            throw new FractalException("La estrategia de particion no genero tareas.");
        }

        JobContext ctx;
        synchronized (jobLock) {
            if (activeJob != null) {
                throw new FractalException("Ya hay un trabajo en curso: " + activeJob.jobId);
            }
            ctx = new JobContext(config, tiles);
            activeJob = ctx;
        }

        System.out.println("-------------------------------------------------");
        System.out.println("[MASTER] Nueva solicitud de renderizado: " + ctx.jobId);
        System.out.printf("[MASTER] Resolucion: %dx%d | MaxIter: %d%n", config.width, config.height, config.maxIterations);
        System.out.println("[MASTER] Estrategia de particion: " + strategy.getStrategyName());
        System.out.printf("[MASTER] %d tareas encoladas. Notificando a %d worker(s)...%n",
            tiles.size(), workers.size());

        try {
            notifyWorkers(ctx.jobId);

            int[][] result = ctx.completion.get(JOB_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            long elapsed = System.currentTimeMillis() - ctx.startTime;
            System.out.printf(Locale.US,
                "[MASTER] Renderizado completado en %d ms (%d/%d tareas).%n",
                elapsed, ctx.completed.get(), ctx.totalTasks);
            return result;
        } catch (TimeoutException e) {
            throw new FractalException("Tiempo de espera agotado esperando resultados de los workers.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FractalException("Renderizado interrumpido.");
        } catch (ExecutionException e) {
            throw new FractalException("Error durante la consolidacion del fractal: " + e.getMessage());
        } finally {
            synchronized (jobLock) {
                if (activeJob == ctx) {
                    activeJob = null;
                    lastJobId = ctx.jobId;
                    lastJobStats = ctx.snapshotStats();
                    ctx.queue.clear();
                }
            }
            printWorkerSummary(ctx);
        }
    }

    private void notifyWorkers(String jobId) {
        for (Map.Entry<String, WorkerObserverPrx> entry : workers.entrySet()) {
            notifyWorkerAsync(entry.getKey(), entry.getValue(), jobId);
        }
    }

    private void notifyWorkerAsync(String workerId, WorkerObserverPrx observer, String jobId) {
        CompletableFuture.runAsync(() -> {
            try {
                observer.notifyTasksAvailable(jobId);
            } catch (Exception e) {
                System.err.printf("[MASTER] [WARN] No se pudo notificar a %s: %s%n", workerId, e.getMessage());
            }
        });
    }

    private void printWorkerSummary(JobContext ctx) {
        WorkerJobStat[] stats = ctx.snapshotStats();
        if (stats.length == 0) {
            return;
        }
        System.out.println("[MASTER] Carga por worker para " + ctx.jobId + ":");
        for (WorkerJobStat stat : stats) {
            System.out.printf("[MASTER]   %s: %d tareas, %d ms de computo%n",
                stat.workerId, stat.tasksCompleted, stat.computeTimeMs);
        }
    }

    @Override
    public void registerWorker(String workerId, WorkerObserverPrx observer, Current current) {
        if (observer == null) {
            return;
        }
        workers.put(workerId, observer);
        System.out.printf("[MASTER] [+] Worker registrado: %s | Total activos: %d%n", workerId, workers.size());

        JobContext ctx = activeJob;
        if (ctx != null) {
            notifyWorkerAsync(workerId, observer, ctx.jobId);
        }
    }

    @Override
    public void unregisterWorker(String workerId, Current current) {
        workers.remove(workerId);
        System.out.printf("[MASTER] [-] Worker retirado: %s | Total activos: %d%n", workerId, workers.size());
    }

    @Override
    public TaskTile pollTask(String workerId, String jobId, Current current) {
        JobContext ctx = activeJob;
        if (ctx == null || jobId == null || !ctx.jobId.equals(jobId)) {
            return emptyTask();
        }
        TaskTile task = ctx.queue.poll();
        return (task != null) ? task : emptyTask();
    }

    @Override
    public void reportResult(TileResult result, Current current) throws FractalException {
        if (result == null || result.pixels == null) {
            throw new FractalException("Resultado nulo recibido en el Master.");
        }

        JobContext ctx = activeJob;
        if (ctx == null || !ctx.jobId.equals(result.jobId)) {
            System.err.printf("[MASTER] [WARN] Resultado tardio o de otro trabajo ignorado: job=%s tarea=%d worker=%s%n",
                result.jobId, result.taskId, result.workerId);
            return;
        }

        validateResult(ctx, result);

        synchronized (ctx.consolidationLock) {
            if (!ctx.completedTaskIds.add(result.taskId)) {
                System.err.printf("[MASTER] [WARN] Resultado duplicado ignorado: job=%s tarea=%d%n",
                    result.jobId, result.taskId);
                return;
            }
            for (int r = 0; r < result.tileHeight; r++) {
                System.arraycopy(result.pixels[r], 0, ctx.image[result.startY + r], result.startX, result.tileWidth);
            }
        }

        int finished = ctx.completed.incrementAndGet();
        ctx.recordWorker(result.workerId, result.computeTimeMs);

        if (VERBOSE || finished == ctx.totalTasks || finished % ctx.progressInterval == 0) {
            System.out.printf(Locale.US,
                "[MASTER] [OK] Tarea #%d de %s en %d ms. Progreso: %d/%d (%.1f%%)%n",
                result.taskId, result.workerId, result.computeTimeMs,
                finished, ctx.totalTasks, (finished * 100.0 / ctx.totalTasks));
        }

        if (finished >= ctx.totalTasks) {
            ctx.completion.complete(ctx.image);
        }
    }

    private void validateResult(JobContext ctx, TileResult result) throws FractalException {
        if (result.taskId < 0 || result.taskId >= ctx.totalTasks) {
            throw new FractalException("taskId fuera de rango: " + result.taskId);
        }
        if (result.startX < 0 || result.startY < 0
            || result.tileWidth <= 0 || result.tileHeight <= 0
            || result.startX + result.tileWidth > ctx.width
            || result.startY + result.tileHeight > ctx.height) {
            throw new FractalException("Bloque fuera de los limites de la imagen.");
        }
        if (result.pixels.length != result.tileHeight) {
            throw new FractalException("Altura de pixeles inconsistente para la tarea " + result.taskId);
        }
        for (int r = 0; r < result.tileHeight; r++) {
            if (result.pixels[r] == null || result.pixels[r].length != result.tileWidth) {
                throw new FractalException("Ancho de pixeles inconsistente para la tarea " + result.taskId);
            }
        }
    }

    @Override
    public int getWorkerCount(Current current) {
        return workers.size();
    }

    @Override
    public int getPendingTasksCount(Current current) {
        JobContext ctx = activeJob;
        return (ctx != null) ? ctx.queue.size() : 0;
    }

    @Override
    public WorkerJobStat[] getLastJobStats(Current current) {
        return lastJobStats.clone();
    }

    public String getLastJobId() {
        return lastJobId;
    }

    private static TaskTile emptyTask() {
        return new TaskTile(false, "", -1, 0, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0);
    }

    private static final class WorkerAccumulator {
        private int tasksCompleted;
        private long computeTimeMs;

        synchronized void add(long computeMs) {
            tasksCompleted++;
            computeTimeMs += computeMs;
        }

        synchronized int tasks() {
            return tasksCompleted;
        }

        synchronized long computeMs() {
            return computeTimeMs;
        }
    }

    private static final class JobContext {
        private final String jobId;
        private final int width;
        private final int height;
        private final int totalTasks;
        private final int progressInterval;
        private final ConcurrentLinkedQueue<TaskTile> queue;
        private final int[][] image;
        private final CompletableFuture<int[][]> completion;
        private final AtomicInteger completed = new AtomicInteger(0);
        private final Set<Integer> completedTaskIds = ConcurrentHashMap.newKeySet();
        private final Map<String, WorkerAccumulator> workerStats = new ConcurrentHashMap<>();
        private final Object consolidationLock = new Object();
        private final long startTime;

        private JobContext(RenderConfig config, List<TaskTile> tiles) {
            this.jobId = (config.jobId == null || config.jobId.isEmpty())
                ? "job-" + UUID.randomUUID() : config.jobId;
            this.width = config.width;
            this.height = config.height;
            this.totalTasks = tiles.size();
            this.progressInterval = Math.max(1, totalTasks / 20);
            this.queue = new ConcurrentLinkedQueue<>(tiles);
            this.image = new int[config.height][config.width];
            this.completion = new CompletableFuture<>();
            this.startTime = System.currentTimeMillis();
        }

        private void recordWorker(String workerId, long computeTimeMs) {
            String id = (workerId == null || workerId.isEmpty()) ? "unknown" : workerId;
            workerStats.computeIfAbsent(id, k -> new WorkerAccumulator()).add(computeTimeMs);
        }

        private WorkerJobStat[] snapshotStats() {
            return workerStats.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new WorkerJobStat(e.getKey(), e.getValue().tasks(), e.getValue().computeMs()))
                .toArray(WorkerJobStat[]::new);
        }
    }
}
