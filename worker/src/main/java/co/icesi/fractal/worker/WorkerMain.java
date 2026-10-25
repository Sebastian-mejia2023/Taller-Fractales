package co.icesi.fractal.worker;

import FractalApp.*;
import co.icesi.fractal.core.MandelbrotCore;
import com.zeroc.Ice.*;
import java.util.concurrent.*;

/**
 * Punto de entrada del Nodo Trabajador (Worker).
 * 
 * Flujo:
 * 1. Inicializa su propio ObjectAdapter para exponer WorkerObserver.
 * 2. Se registra en el Master pasando su proxy WorkerObserver.
 * 3. Permanece en espera pasiva (standby).
 * 4. Al recibir la notificacion 'notifyTasksAvailable', sus hilos de trabajo extraen
 *    tareas concurrentemente del Master ('pollTask'), las computan y reportan
 *    incrementalmente ('reportResult') hasta que la cola del Master este vacia.
 */
public class WorkerMain {

    private final String workerId;
    private final int workerPort;
    private final String masterHost;
    private final int masterPort;
    private final int threadsCount;

    private Communicator communicator;
    private MasterServicePrx master;
    private ExecutorService executor;

    public WorkerMain(String workerId, int workerPort, String masterHost, int masterPort, int threadsCount) {
        this.workerId = workerId;
        this.workerPort = workerPort;
        this.masterHost = masterHost;
        this.masterPort = masterPort;
        this.threadsCount = Math.max(1, threadsCount);
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

            // 1. Crear el adaptador de objetos propio del Worker para exponer el Observer
            String endpoint = "tcp -h 0.0.0.0 -p " + workerPort;
            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("WorkerAdapter", endpoint);

            WorkerObserverI observerServant = new WorkerObserverI(this);
            adapter.add(observerServant, Util.stringToIdentity("WorkerObserver"));
            adapter.activate();

            // 2. Conectar al Master
            String masterEndpoint = String.format("MasterService:tcp -h %s -p %d", masterHost, masterPort);
            master = MasterServicePrx.checkedCast(communicator.stringToProxy(masterEndpoint));
            if (master == null) {
                System.err.println("[ERROR] No se pudo obtener el proxy valido de MasterService en " + masterEndpoint);
                return;
            }

            // 3. Registrarse en el Master pasando el proxy con endpoint explicito
            String observerEndpoint = String.format("WorkerObserver:tcp -h 127.0.0.1 -p %d", workerPort);
            WorkerObserverPrx observer = WorkerObserverPrx.checkedCast(communicator.stringToProxy(observerEndpoint));
            master.registerWorker(workerId, observer);

            // Hook de apagado limpio
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
            System.out.printf("Maestro Conect.: %s%n", masterEndpoint);
            System.out.printf("Hilos de Computo:%d hilos en ThreadPool%n", threadsCount);
            System.out.println("Estado actual:   EN ESPERA (STANDBY)");
            System.out.println("Presione Ctrl+C para finalizar.");
            System.out.println("=================================================");

            communicator.waitForShutdown();

        } catch (java.lang.Exception e) {
            System.err.println("[ERROR] Fallo en el Worker: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Invocado cuando el Master notifica que hay tareas encoladas para un nuevo trabajo.
     */
    public void onTasksAvailable(String jobId) {
        System.out.printf("[%s] [EVENTO] Tareas disponibles para el trabajo '%s'. Despertando %d hilos de computo...%n",
            workerId, jobId, threadsCount);

        for (int i = 0; i < threadsCount; i++) {
            final int threadId = i + 1;
            executor.submit(() -> {
                while (true) {
                    // PULL: Pedir tarea al Master
                    TaskTile task = null;
                    try {
                        task = master.pollTask(workerId);
                    } catch (java.lang.Exception e) {
                        System.err.printf("[%s-H%d] Error al hacer pollTask: %s%n", workerId, threadId, e.getMessage());
                        break;
                    }

                    // Si no hay tareas pendientes en el Master, salir del bucle de trabajo
                    if (task == null || !task.hasTask) {
                        break;
                    }

                    // PROCESAR: Computo matematico intensivo de la baldosa asignada
                    long t0 = System.currentTimeMillis();
                    int[][] pixels = MandelbrotCore.computeTile(task);
                    long computeTimeMs = System.currentTimeMillis() - t0;

                    // REPORTAR: Enviar el resultado parcial al Master
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
                        System.err.printf("[%s-H%d] Error al reportar resultado de tarea #%d: %s%n",
                            workerId, threadId, task.taskId, e.getMessage());
                        break;
                    }
                }
            });
        }
    }

    public static void main(String[] args) {
        int workerPort = (args.length >= 1) ? Integer.parseInt(args[0]) : 10001;
        String workerId = (args.length >= 2) ? args[1] : "Worker-" + workerPort;
        String masterHost = (args.length >= 3) ? args[2] : "localhost";
        int masterPort = (args.length >= 4) ? Integer.parseInt(args[3]) : 10000;
        int threads = (args.length >= 5) ? Integer.parseInt(args[4]) : Runtime.getRuntime().availableProcessors();

        WorkerMain worker = new WorkerMain(workerId, workerPort, masterHost, masterPort, threads);
        worker.start(args);
    }
}
