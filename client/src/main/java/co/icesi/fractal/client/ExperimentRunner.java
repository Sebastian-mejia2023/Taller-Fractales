package co.icesi.fractal.client;

import FractalApp.*;
import co.icesi.fractal.core.FractalRegion;
import co.icesi.fractal.core.MandelbrotCore;
import com.zeroc.Ice.*;
import java.util.UUID;

/**
 * Harness automatizado para la ejecucion de la matriz experimental.
 * Ejecuta multiples pruebas variando la estrategia y parametros, y emite
 * una tabla de resultados comparativa en formato Markdown lista para el informe.
 */
public class ExperimentRunner {

    public static void main(String[] args) {
        String masterHost = (args.length >= 1) ? args[0] : "localhost";
        int masterPort = (args.length >= 2) ? Integer.parseInt(args[1]) : 10000;
        int width = (args.length >= 3) ? Integer.parseInt(args[2]) : 3840;
        int height = (args.length >= 4) ? Integer.parseInt(args[3]) : 2160;
        int maxIter = (args.length >= 5) ? Integer.parseInt(args[4]) : 5000;
        String presetName = (args.length >= 6) ? args[5] : "seahorse";

        FractalRegion region = FractalRegion.fromName(presetName);
        double xMin = region.getXMin();
        double xMax = region.getXMax();
        double yMin = region.getYMin();
        double yMax = region.getYMax();

        // Ajuste isometrico de aspecto
        if (width > 0 && height > 0) {
            double aspect = (double) width / (double) height;
            double realHeight = yMax - yMin;
            xMax = xMin + realHeight * aspect;
        }

        InitializationData initData = new InitializationData();
        initData.properties = Util.createProperties(args);
        initData.properties.setProperty("Ice.MessageSizeMax", "204800");

        try (Communicator communicator = Util.initialize(initData)) {
            String masterEndpoint = String.format("MasterService:tcp -h %s -p %d", masterHost, masterPort);
            MasterServicePrx master = MasterServicePrx.checkedCast(communicator.stringToProxy(masterEndpoint));

            if (master == null) {
                System.err.println("[ERROR] No se pudo conectar al Master.");
                return;
            }

            int p = master.getWorkerCount();
            System.out.println("=================================================");
            System.out.println("     INICIANDO MATRIZ DE EXPERIMENTACIÓN");
            System.out.println("=================================================");
            System.out.printf("Region / Preset: %s (%s)%n", region.getName(), region.getId());
            System.out.printf("Resolucion:      %dx%d | MaxIter: %d | Workers: %d%n%n", width, height, maxIter, p);

            // 1. Linea base monohilo
            System.out.println("Calculando tiempo secuencial monohilo T_sec...");
            long t0Sec = System.currentTimeMillis();
            double dx = (width > 1) ? (xMax - xMin) / (width - 1) : 0.0;
            double dy = (height > 1) ? (yMax - yMin) / (height - 1) : 0.0;

            for (int y = 0; y < height; y++) {
                double ci = yMin + y * dy;
                for (int x = 0; x < width; x++) {
                    double cr = xMin + x * dx;
                    MandelbrotCore.computePoint(cr, ci, maxIter);
                }
            }
            long tSec = System.currentTimeMillis() - t0Sec;
            System.out.printf("T_sec = %d ms (%.2f s)%n%n", tSec, tSec / 1000.0);

            // Casos experimentales:
            // Tipo 0 (FixedCount): N = 2, 4, 8, 16 franjas
            // Tipo 1 (FixedTileSize): S = 32, 64, 128, 256 px
            int[][] testCases = {
                {0, 2},   // Franjas: 2
                {0, 4},   // Franjas: 4
                {0, 8},   // Franjas: 8
                {0, 16},  // Franjas: 16
                {1, 32},  // Baldosas: 32x32 px
                {1, 64},  // Baldosas: 64x64 px (grano optimo)
                {1, 128}, // Baldosas: 128x128 px
                {1, 256}  // Baldosas: 256x256 px (grano grueso)
            };

            System.out.println("| Estrategia | Parámetro | Tareas Generadas | T_sec (ms) | T_dist (ms) | Speedup (S) | Eficiencia (E) |");
            System.out.println("| :--- | :---: | :---: | :---: | :---: | :---: | :---: |");

            for (int[] tc : testCases) {
                int stType = tc[0];
                int stParam = tc[1];
                String stName = (stType == 0) ? "FixedCount (Franjas)" : "FixedTileSize (Baldosas)";

                String jobId = "exp-" + UUID.randomUUID().toString().substring(0, 8);
                RenderConfig config = new RenderConfig(
                    jobId, width, height, xMin, xMax, yMin, yMax, maxIter, stType, stParam
                );

                long t0Dist = System.currentTimeMillis();
                master.renderFractal(config);
                long tDist = System.currentTimeMillis() - t0Dist;

                double speedup = (double) tSec / (double) Math.max(1, tDist);
                double efficiency = (p > 0) ? (speedup / p) * 100.0 : 0.0;

                int tasksCount = (stType == 0) ? stParam : (int) (Math.ceil((double) width / stParam) * Math.ceil((double) height / stParam));

                System.out.printf("| %s | %d | %d | %d | %d | %.2fx | %.1f%% |%n",
                    stName, stParam, tasksCount, tSec, tDist, speedup, efficiency);
            }

            System.out.println("\n[FIN] Matriz experimental finalizada.");

        } catch (java.lang.Exception e) {
            System.err.println("[ERROR] Error en el ejecutor de experimentos: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
