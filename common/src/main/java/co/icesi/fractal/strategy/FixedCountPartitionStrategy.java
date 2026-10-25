package co.icesi.fractal.strategy;

import FractalApp.RenderConfig;
import FractalApp.TaskTile;
import java.util.ArrayList;
import java.util.List;

/**
 * Estrategia de particionamiento en N tareas fijas (Franjas horizontales).
 * Divide la altura total de la imagen en N bloques contiguos.
 */
public class FixedCountPartitionStrategy implements TaskPartitionStrategy {

    private final int taskCount;

    public FixedCountPartitionStrategy(int taskCount) {
        this.taskCount = Math.max(1, taskCount);
    }

    @Override
    public List<TaskTile> partition(RenderConfig config) {
        List<TaskTile> tiles = new ArrayList<>();
        int width = config.width;
        int height = config.height;
        int n = Math.min(taskCount, height);

        int rowsPerTask = (int) Math.ceil((double) height / n);

        int taskId = 0;
        int currentY = 0;

        while (currentY < height) {
            int currentTileHeight = Math.min(rowsPerTask, height - currentY);

            TaskTile tile = new TaskTile(
                true,
                config.jobId,
                taskId++,
                0,                  // startX
                currentY,           // startY
                width,              // tileWidth (ancho completo)
                currentTileHeight,  // tileHeight
                width,              // totalWidth
                height,             // totalHeight
                config.xMin,
                config.xMax,
                config.yMin,
                config.yMax,
                config.maxIterations
            );

            tiles.add(tile);
            currentY += currentTileHeight;
        }

        return tiles;
    }

    @Override
    public String getStrategyName() {
        return "FixedCountStrategy (N = " + taskCount + " franjas)";
    }
}
