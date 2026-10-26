package co.icesi.fractal.core;

import FractalApp.TaskTile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MandelbrotCoreTest {

    @Test
    void knownPointsHaveExpectedIterations() {
        assertEquals(100, MandelbrotCore.computePoint(0.0, 0.0, 100));
        assertEquals(100, MandelbrotCore.computePoint(-1.0, 0.0, 100));
        assertEquals(2, MandelbrotCore.computePoint(2.0, 0.0, 100));
    }

    @Test
    void tileUsesTheSameGlobalGridAsTheSequentialRenderer() {
        int width = 17;
        int height = 13;
        int startX = 3;
        int startY = 5;
        int tileWidth = 7;
        int tileHeight = 6;
        int maxIter = 200;
        double xMin = -2.0;
        double xMax = 0.5;
        double yMin = -1.25;
        double yMax = 1.25;

        TaskTile task = new TaskTile(true, "test-job", 1, startX, startY,
            tileWidth, tileHeight, width, height, xMin, xMax, yMin, yMax, maxIter);
        int[][] actual = MandelbrotCore.computeTile(task);

        double dx = (xMax - xMin) / (width - 1);
        double dy = (yMax - yMin) / (height - 1);
        int[][] expected = new int[tileHeight][tileWidth];
        for (int r = 0; r < tileHeight; r++) {
            for (int c = 0; c < tileWidth; c++) {
                expected[r][c] = MandelbrotCore.computePoint(
                    xMin + (startX + c) * dx, yMin + (startY + r) * dy, maxIter);
            }
        }

        for (int r = 0; r < tileHeight; r++) {
            assertArrayEquals(expected[r], actual[r]);
        }
    }
}
