package co.icesi.fractal.monolith;

import co.icesi.fractal.core.FractalRegion;
import co.icesi.fractal.core.MandelbrotCore;
import java.io.IOException;

/**
 * Implementacion Monolitica Secuencial del Renderizador de Mandelbrot.
 * Opera en un solo hilo sobre una sola CPU, sirviendo como linea base (T_sec).
 * 
 * Permite evaluar configuraciones 4K, 5,000 iteraciones y presets de estres
 * como Seahorse Valley (Valle de los Caballitos de Mar).
 */
public class MonolithicMain {

    public static void main(String[] args) {
        int width = (args.length >= 1) ? Integer.parseInt(args[0]) : 3840;
        int height = (args.length >= 2) ? Integer.parseInt(args[1]) : 2160;
        int maxIter = (args.length >= 3) ? Integer.parseInt(args[2]) : 5000;

        String presetName = "seahorse";
        String outputPath = "output/mandelbrot_monolith.png";

        if (args.length >= 4) {
            if (args[3].endsWith(".png") || args[3].contains("/") || args[3].contains("\\")) {
                outputPath = args[3];
            } else {
                presetName = args[3];
                if (args.length >= 5) {
                    outputPath = args[4];
                }
            }
        }

        FractalRegion region = FractalRegion.fromName(presetName);
        double xMin = region.getXMin();
        double xMax = region.getXMax();
        double yMin = region.getYMin();
        double yMax = region.getYMax();

        // Ajuste isometrico de aspecto para evitar deformaciones
        if (width > 0 && height > 0) {
            double aspect = (double) width / (double) height;
            double realHeight = yMax - yMin;
            xMax = xMin + realHeight * aspect;
        }

        System.out.println("=================================================");
        System.out.println("      RENDERIZADO MONOLITICO SECUENCIAL");
        System.out.println("=================================================");
        System.out.printf("Preset / Región:       %s (%s)%n", region.getName(), region.getId());
        System.out.printf("Dimensiones:           %dx%d (%d pixeles)%n", width, height, (long) width * height);
        System.out.printf("Plano Complejo:        X=[%.6f, %.6f], Y=[%.6f, %.6f]%n", xMin, xMax, yMin, yMax);
        System.out.printf("Profundidad:           %d iteraciones maximas por pixel%n", maxIter);
        System.out.println("Iniciando computo en monohilo...");

        long startTime = System.currentTimeMillis();

        int[][] iterations = new int[height][width];
        double dx = (width > 1) ? (xMax - xMin) / (width - 1) : 0.0;
        double dy = (height > 1) ? (yMax - yMin) / (height - 1) : 0.0;

        for (int y = 0; y < height; y++) {
            double ci = yMin + y * dy;
            for (int x = 0; x < width; x++) {
                double cr = xMin + x * dx;
                iterations[y][x] = MandelbrotCore.computePoint(cr, ci, maxIter);
            }
        }

        long tSec = System.currentTimeMillis() - startTime;

        System.out.println("=================================================");
        System.out.printf("[MONOLITO] Tiempo Secuencial (T_sec): %d ms (%.2f s)%n", tSec, tSec / 1000.0);
        System.out.println("=================================================");

        try {
            MandelbrotCore.saveImage(iterations, width, height, maxIter, outputPath);
        } catch (IOException e) {
            System.err.println("[ERROR] No se pudo guardar la imagen: " + e.getMessage());
        }
    }
}
