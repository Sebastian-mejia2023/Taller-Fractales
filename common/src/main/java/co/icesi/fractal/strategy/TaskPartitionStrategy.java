package co.icesi.fractal.strategy;

import FractalApp.RenderConfig;
import FractalApp.TaskTile;
import java.util.List;

/**
 * Interfaz del patron Strategy para la descomposicion del problema en tareas.
 */
public interface TaskPartitionStrategy {

    /**
     * Divide la configuracion global de la imagen en una lista de tareas granulares TaskTile.
     */
    List<TaskTile> partition(RenderConfig config);

    /**
     * Nombre identificador de la estrategia de particion.
     */
    String getStrategyName();
}
