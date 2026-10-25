package co.icesi.fractal.master;

import FractalApp.*;
import co.icesi.fractal.strategy.PartitionStrategyFactory;
import co.icesi.fractal.strategy.TaskPartitionStrategy;
import com.zeroc.Ice.Current;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servant del Maestro (MasterService).
 * 
 * Implementa la orquestacion independiente y asincrona:
 * 1. Mantiene una cola de tareas (Task Queue) poblada segun la estrategia de particion.
 * 2. Mantiene el registro de observadores (WorkerObserver) de los workers activos.
 * 3. Notifica a los workers cuando hay tareas disponibles sin mantener hilos bloqueados hacia ellos.
 * 4. Atiende las peticiones 'pollTask' donde cada worker extrae autonomamente su trabajo.
 * 5. Consolida incrementalmente los bloques recibidos en 'reportResult'.
 */
public class MasterServiceI implements MasterService {

    private final Map<String, WorkerObserverPrx> workers = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<TaskTile> taskQueue = new ConcurrentLinkedQueue<>();

    private final AtomicInteger totalTasksCount = new AtomicInteger(0);
    private final AtomicInteger completedTasksCount = new AtomicInteger(0);

    private int[][] finalImageMatrix;
    private CompletableFuture<int[][]> jobCompletionFuture;
    private long jobStartTime;
    private final Object consolidationLock = new Object();

    @Override
    public int[][] renderFractal(RenderConfig config, Current current) throws FractalException {
        if (workers.isEmpty()) {
            throw new FractalException("No hay Workers registrados en el Master. Inicie al menos un Worker.");
        }

        // 1. Aplicar patron Strategy para particionar la tarea global
        TaskPartitionStrategy strategy = PartitionStrategyFactory.createStrategy(
            config.strategyType, config.strategyParam
        );
        System.out.println("-------------------------------------------------");
        System.out.println("[MASTER] Nueva solicitud de renderizado: " + config.jobId);
        System.out.printf("[MASTER] Resolucion: %dx%d | MaxIter: %d%n", config.width, config.height, config.maxIterations);
        System.out.println("[MASTER] Estrategia de particion: " + strategy.getStrategyName());

        List<TaskTile> tiles = strategy.partition(config);
        if (tiles.isEmpty()) {
            throw new FractalException("La estrategia de particion no genero tareas.");
        }

        // 2. Encolar las tareas en la bolsa de tareas (Bag of Tasks)
        taskQueue.clear();
        taskQueue.addAll(tiles);
        totalTasksCount.set(tiles.size());
        completedTasksCount.set(0);

        finalImageMatrix = new int[config.height][config.width];
        jobCompletionFuture = new CompletableFuture<>();
        jobStartTime = System.currentTimeMillis();

        System.out.printf("[MASTER] %d tareas encoladas. Notificando a %d worker(s) en espera...%n",
            tiles.size(), workers.size());

        // 3. Notificacion asincrona a los observadores de los workers (sin hilos persistentes)
        for (Map.Entry<String, WorkerObserverPrx> entry : workers.entrySet()) {
            String workerId = entry.getKey();
            WorkerObserverPrx observer = entry.getValue();
            CompletableFuture.runAsync(() -> {
                try {
                    observer.notifyTasksAvailable(config.jobId);
                } catch (Exception e) {
                    System.err.printf("[MASTER] [WARN] No se pudo notificar a %s: %s%n", workerId, e.getMessage());
                }
            });
        }

        // 4. Esperar la consolidacion de la ultima tarea
        try {
            int[][] result = jobCompletionFuture.get(15, TimeUnit.MINUTES);
            long elapsed = System.currentTimeMillis() - jobStartTime;
            System.out.printf("[MASTER] Renderizado completado exitosamente en %d ms (%d tareas procesadas).%n",
                elapsed, totalTasksCount.get());
            return result;
        } catch (TimeoutException e) {
            throw new FractalException("Tiempo de espera agotado (Timeout) esperando respuesta de los workers.");
        } catch (Exception e) {
            throw new FractalException("Error durante la consolidacion del fractal: " + e.getMessage());
        }
    }

    @Override
    public void registerWorker(String workerId, WorkerObserverPrx observer, Current current) {
        if (observer != null) {
            workers.put(workerId, observer);
            System.out.printf("[MASTER] [+] Worker registrado: %s | Total activos: %d%n", workerId, workers.size());
        }
    }

    @Override
    public void unregisterWorker(String workerId, Current current) {
        workers.remove(workerId);
        System.out.printf("[MASTER] [-] Worker retirado: %s | Total activos: %d%n", workerId, workers.size());
    }

    @Override
    public TaskTile pollTask(String workerId, Current current) {
        TaskTile task = taskQueue.poll();
        if (task != null) {
            return task;
        }
        // Retorna un TaskTile inactivo (hasTask = false) para indicar que la cola esta vacia
        return new TaskTile(false, "", -1, 0, 0, 0, 0, 0, 0, 0.0, 0.0, 0.0, 0.0, 0);
    }

    @Override
    public void reportResult(TileResult result, Current current) throws FractalException {
        if (result == null || result.pixels == null) {
            throw new FractalException("Resultado nulo recibido en el Master.");
        }

        // Consolidacion incremental del bloque de pixeles en la matriz global
        synchronized (consolidationLock) {
            if (finalImageMatrix != null) {
                int startX = result.startX;
                int startY = result.startY;
                for (int r = 0; r < result.tileHeight; r++) {
                    for (int c = 0; c < result.tileWidth; c++) {
                        finalImageMatrix[startY + r][startX + c] = result.pixels[r][c];
                    }
                }
            }
        }

        int finished = completedTasksCount.incrementAndGet();
        int total = totalTasksCount.get();

        System.out.printf("[MASTER] [OK] Tarea #%03d entregada por %s (%d ms). Progreso: %d/%d (%.1f%%)%n",
            result.taskId, result.workerId, result.computeTimeMs, finished, total, (finished * 100.0 / total));

        // Cuando la ultima tarea es recibida, se completa el futuro de la operacion global
        if (finished >= total && jobCompletionFuture != null && !jobCompletionFuture.isDone()) {
            jobCompletionFuture.complete(finalImageMatrix);
        }
    }

    @Override
    public int getWorkerCount(Current current) {
        return workers.size();
    }

    @Override
    public int getPendingTasksCount(Current current) {
        return taskQueue.size();
    }
}
