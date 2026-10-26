package co.icesi.fractal.worker;

import FractalApp.*;
import co.icesi.fractal.core.MandelbrotCore;
import com.zeroc.Ice.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkerMain {

    private final String workerId;
    private final int workerPort;
    private final String masterHost;
    private final int masterPort;
    private final int threadsCount;
    private final String advertisedHost;

    private Communicator communicator;
    private MasterServicePrx master;
    private ExecutorService executor;
    private final ConcurrentHashMap<String, JobWorkers> jobWorkers = new ConcurrentHashMap<>();

    public WorkerMain(String workerId, int workerPort, String masterHost, int masterPort,
                      int threadsCount, String advertisedHost) {
        this.workerId = workerId;
        this.workerPort = workerPort;
        this.masterHost = masterHost;
        this.masterPort = masterPort;
        this.threadsCount = Math.max(1, threadsCount);
        this.advertisedHost = advertisedHost;
    }

    public void start(String[] args) {
        executor = Executors.newFixedThreadPool(threadsCount);

        InitializationData initData = new InitializationData();
        initData.properties = Util.createProperties(args);
        initData.properties.setProperty("Ice.ThreadPool.Server.Size", "8");
        initData.properties.setProperty("WorkerAdapter.ThreadPool.Size", "8");
        initData.properties.setProperty("Ice.MessageSizeMax", "204800");

        try {
            communicator = Util.initialize(initData);

            String endpoint = "tcp -h 0.0.0.0 -p " + workerPort;
            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("WorkerAdapter", endpoint);

            WorkerObserverI observerServant = new WorkerObserverI(this);
            adapter.add(observerServant, Util.stringToIdentity("WorkerObserver"));
            adapter.activate();

            String masterEndpoint = String.format("MasterService:tcp -h %s -p %d", masterHost, masterPort);
            master = MasterServicePrx.checkedCast(communicator.stringToProxy(masterEndpoint));
            if (master == null) {
                System.err.println("[ERROR] No se pudo obtener el proxy valido de MasterService en " + masterEndpoint);
                executor.shutdownNow();
                return;
            }

            String observerEndpoint = String.format("WorkerObserver:tcp -h %s -p %d", advertisedHost, workerPort);
            WorkerObserverPrx observer = WorkerObserverPrx.checkedCast(communicator.stringToProxy(observerEndpoint));
            master.registerWorker(workerId, observer);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[SHUTDOWN] Desregistrando worker del Master...");
                try {
                    master.unregisterWorker(workerId);
                } catch (java.lang.Exception ignored) {
                }
                executor.shutdownNow();
            }));

            System.out.println("=================================================");
            System.out.println("        NODO TRABAJADOR (WORKER) ACTIVO");
            System.out.println("=================================================");
            System.out.printf("Identificador:   %s%n", workerId);
            System.out.printf("Puerto Local:    %d%n", workerPort);
            System.out.printf("Observer:        %s%n", observerEndpoint);
            System.out.printf("Maestro Conect.: %s%n", masterEndpoint);
            System.out.printf("Hilos de Computo:%d hilos en ThreadPool%n", threadsCount);
            System.out.println("Estado actual:   EN ESPERA (STANDBY)");
            System.out.println("Presione Ctrl+C para finalizar.");
            System.out.println("=================================================");

            communicator.waitForShutdown();

        } catch (java.lang.Exception e) {
            System.err.println("[ERROR] Fallo en el Worker: " + e.getMessage());
            e.printStackTrace();
            executor.shutdownNow();
        }
    }

    public void onTasksAvailable(String jobId) {
        if (jobId == null || jobId.isEmpty()) {
            return;
        }

        JobWorkers jw = jobWorkers.computeIfAbsent(jobId, k -> new JobWorkers());
        System.out.printf("[%s] [EVENTO] Tareas disponibles para '%s'. Asegurando %d hilos de computo...%n",
            workerId, jobId, threadsCount);

        while (true) {
            int started = jw.started.get();
            if (started >= threadsCount) {
                return;
            }
            if (!jw.started.compareAndSet(started, started + 1)) {
                continue;
            }

            jw.running.incrementAndGet();
            try {
                executor.submit(() -> workLoop(jobId, jw));
            } catch (RejectedExecutionException e) {
                jw.running.decrementAndGet();
                jw.started.decrementAndGet();
                return;
            }
        }
    }

    private void workLoop(String jobId, JobWorkers jw) {
        try {
            while (true) {
                TaskTile task;
                try {
                    task = master.pollTask(workerId, jobId);
                } catch (java.lang.Exception e) {
                    System.err.printf("[%s] Error al hacer pollTask: %s%n", workerId, e.getMessage());
                    break;
                }

                if (task == null || !task.hasTask) {
                    break;
                }

                long t0 = System.currentTimeMillis();
                int[][] pixels = MandelbrotCore.computeTile(task);
                long computeTimeMs = System.currentTimeMillis() - t0;

                TileResult result = new TileResult(
                    task.jobId,
                    task.taskId,
                    task.startX,
                    task.startY,
                    task.tileWidth,
                    task.tileHeight,
                    pixels,
                    computeTimeMs,
                    workerId
                );

                try {
                    master.reportResult(result);
                } catch (java.lang.Exception e) {
                    System.err.printf("[%s] Error al reportar resultado de tarea #%d: %s%n",
                        workerId, task.taskId, e.getMessage());
                    break;
                }
            }
        } finally {
            if (jw.running.decrementAndGet() == 0 && jw.started.get() >= threadsCount) {
                jobWorkers.remove(jobId, jw);
            }
        }
    }

    public static void main(String[] args) {
        int workerPort = (args.length >= 1) ? Integer.parseInt(args[0]) : 10001;
        String workerId = (args.length >= 2) ? args[1] : "Worker-" + workerPort;
        String masterHost = (args.length >= 3) ? args[2] : "localhost";
        int masterPort = (args.length >= 4) ? Integer.parseInt(args[3]) : 10000;
        int threads = (args.length >= 5) ? Integer.parseInt(args[4]) : Runtime.getRuntime().availableProcessors();
        String advertisedHost = (args.length >= 6)
            ? args[5]
            : System.getenv().getOrDefault("FRACTAL_WORKER_HOST", "127.0.0.1");

        WorkerMain worker = new WorkerMain(workerId, workerPort, masterHost, masterPort, threads, advertisedHost);
        worker.start(args);
    }

    private static final class JobWorkers {
        private final AtomicInteger started = new AtomicInteger(0);
        private final AtomicInteger running = new AtomicInteger(0);
    }
}
