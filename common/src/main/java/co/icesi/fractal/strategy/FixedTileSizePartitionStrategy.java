package co.icesi.fractal.strategy;

import FractalApp.RenderConfig;
import FractalApp.TaskTile;
import java.util.ArrayList;
import java.util.List;

/**
 * Estrategia de particionamiento en baldosas (tiles) de tamano fijo (SxS pixeles).
 * Permite balanceo dinamico optimo mediante Bag of Tasks.
 */
public class FixedTileSizePartitionStrategy implements TaskPartitionStrategy {

    private final int tileSize;

    public FixedTileSizePartitionStrategy(int tileSize) {
        this.tileSize = Math.max(8, tileSize);
    }

    @Override
    public List<TaskTile> partition(RenderConfig config) {
        List<TaskTile> tiles = new ArrayList<>();
        int width = config.width;
        int height = config.height;

        int taskId = 0;

        for (int y = 0; y < height; y += tileSize) {
            int currentTileHeight = Math.min(tileSize, height - y);

            for (int x = 0; x < width; x += tileSize) {
                int currentTileWidth = Math.min(tileSize, width - x);

                TaskTile tile = new TaskTile(
                    true,
                    config.jobId,
                    taskId++,
                    x,
                    y,
                    currentTileWidth,
                    currentTileHeight,
                    width,
                    height,
                    config.xMin,
                    config.xMax,
                    config.yMin,
                    config.yMax,
                    config.maxIterations
                );

                tiles.add(tile);
            }
        }

        return tiles;
    }

    @Override
    public String getStrategyName() {
        return "FixedTileSizeStrategy (" + tileSize + "x" + tileSize + " px)";
    }
}
