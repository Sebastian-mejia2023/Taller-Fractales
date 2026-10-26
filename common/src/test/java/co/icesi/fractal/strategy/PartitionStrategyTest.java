package co.icesi.fractal.strategy;

import FractalApp.RenderConfig;
import FractalApp.TaskTile;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PartitionStrategyTest {

    private static RenderConfig config(int width, int height) {
        return new RenderConfig("test-job", width, height,
            -2.0, 0.5, -1.25, 1.25, 500, 1, 32);
    }

    @Test
    void fixedTileSizeCoversEveryPixelExactlyOnce() {
        int width = 100;
        int height = 70;
        List<TaskTile> tiles = new FixedTileSizePartitionStrategy(32).partition(config(width, height));

        assertEquals(12, tiles.size());
        assertCoverage(tiles, width, height);
    }

    @Test
    void fixedCountCoversEveryPixelExactlyOnce() {
        int width = 100;
        int height = 70;
        List<TaskTile> tiles = new FixedCountPartitionStrategy(4).partition(config(width, height));

        assertEquals(4, tiles.size());
        assertCoverage(tiles, width, height);
    }

    @Test
    void tileSizeIsClampedToMinimum() {
        List<TaskTile> tiles = new FixedTileSizePartitionStrategy(1).partition(config(8, 8));
        assertEquals(1, tiles.size());
        assertEquals(8, tiles.get(0).tileWidth);
        assertEquals(8, tiles.get(0).tileHeight);
    }

    private static void assertCoverage(List<TaskTile> tiles, int width, int height) {
        boolean[][] covered = new boolean[height][width];
        int taskId = 0;

        for (TaskTile tile : tiles) {
            assertTrue(tile.hasTask);
            assertEquals(taskId++, tile.taskId);
            assertEquals(width, tile.totalWidth);
            assertEquals(height, tile.totalHeight);
            assertTrue(tile.startX >= 0 && tile.startY >= 0);
            assertTrue(tile.startX + tile.tileWidth <= width);
            assertTrue(tile.startY + tile.tileHeight <= height);

            for (int y = tile.startY; y < tile.startY + tile.tileHeight; y++) {
                for (int x = tile.startX; x < tile.startX + tile.tileWidth; x++) {
                    assertFalse(covered[y][x], "Pixel repetido en (" + x + ", " + y + ")");
                    covered[y][x] = true;
                }
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                assertTrue(covered[y][x], "Pixel sin cubrir en (" + x + ", " + y + ")");
            }
        }
    }
}
