package co.icesi.fractal.worker;

import FractalApp.WorkerObserver;
import com.zeroc.Ice.Current;

/**
 * Servant observador alojado en el Worker.
 * Permite que el Master emita una notificacion asincrona cuando hay tareas disponibles.
 */
public class WorkerObserverI implements WorkerObserver {

    private final WorkerMain worker;

    public WorkerObserverI(WorkerMain worker) {
        this.worker = worker;
    }

    @Override
    public void notifyTasksAvailable(String jobId, Current current) {
        // Ejecuta en hilo desacoplado en el Worker para retornar de inmediato al Master
        worker.onTasksAvailable(jobId);
    }
}
