package co.icesi.fractal.client;

import co.icesi.fractal.core.MandelbrotCore;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class BaselineCache {

    private static final int MAGIC = 0x46524331;

    private BaselineCache() {
    }

    public static final class Baseline {
        public final int[][] matrix;
        public final long tSec;

        private Baseline(int[][] matrix, long tSec) {
            this.matrix = matrix;
            this.tSec = tSec;
        }
    }

    public static Baseline loadOrCompute(int width, int height, double xMin, double xMax,
                                         double yMin, double yMax, int maxIter,
                                         String cachePath) throws IOException {
        if (cachePath != null && !cachePath.isEmpty()) {
            Baseline cached = load(cachePath, width, height, xMin, xMax, yMin, yMax, maxIter);
            if (cached != null) {
                System.out.printf("[BASELINE] Cache reutilizado: %s (T_sec = %d ms)%n", cachePath, cached.tSec);
                return cached;
            }
        }

        long t0 = System.currentTimeMillis();
        int[][] matrix = compute(width, height, xMin, xMax, yMin, yMax, maxIter);
        long tSec = Math.max(1, System.currentTimeMillis() - t0);

        if (cachePath != null && !cachePath.isEmpty()) {
            save(cachePath, width, height, xMin, xMax, yMin, yMax, maxIter, tSec, matrix);
            System.out.printf("[BASELINE] Cache guardado: %s%n", cachePath);
        }

        return new Baseline(matrix, tSec);
    }

    private static int[][] compute(int width, int height, double xMin, double xMax,
                                   double yMin, double yMax, int maxIter) {
        int[][] matrix = new int[height][width];
        double dx = (width > 1) ? (xMax - xMin) / (width - 1) : 0.0;
        double dy = (height > 1) ? (yMax - yMin) / (height - 1) : 0.0;

        for (int y = 0; y < height; y++) {
            double ci = yMin + y * dy;
            for (int x = 0; x < width; x++) {
                double cr = xMin + x * dx;
                matrix[y][x] = MandelbrotCore.computePoint(cr, ci, maxIter);
            }
        }
        return matrix;
    }

    private static Baseline load(String cachePath, int width, int height,
                                 double xMin, double xMax, double yMin, double yMax,
                                 int maxIter) {
        File file = new File(cachePath);
        if (!file.isFile()) {
            return null;
        }

        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(new FileInputStream(file)))) {
            if (in.readInt() != MAGIC
                || in.readInt() != width
                || in.readInt() != height
                || in.readInt() != maxIter
                || Double.compare(in.readDouble(), xMin) != 0
                || Double.compare(in.readDouble(), xMax) != 0
                || Double.compare(in.readDouble(), yMin) != 0
                || Double.compare(in.readDouble(), yMax) != 0) {
                return null;
            }

            long tSec = in.readLong();
            int[][] matrix = new int[height][width];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    matrix[y][x] = in.readInt();
                }
            }
            return new Baseline(matrix, tSec);
        } catch (IOException | RuntimeException e) {
            System.err.println("[BASELINE] [WARN] Cache invalido, se recalculara: " + e.getMessage());
            return null;
        }
    }

    private static void save(String cachePath, int width, int height,
                             double xMin, double xMax, double yMin, double yMax,
                             int maxIter, long tSec, int[][] matrix) throws IOException {
        File target = new File(cachePath);
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null) {
            Files.createDirectories(parent.toPath());
        }
        File temp = new File(cachePath + ".tmp");

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(temp)))) {
            out.writeInt(MAGIC);
            out.writeInt(width);
            out.writeInt(height);
            out.writeInt(maxIter);
            out.writeDouble(xMin);
            out.writeDouble(xMax);
            out.writeDouble(yMin);
            out.writeDouble(yMax);
            out.writeLong(tSec);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    out.writeInt(matrix[y][x]);
                }
            }
        }

        Path source = temp.toPath();
        Path destination = target.toPath();
        try {
            Files.move(source, destination,
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
