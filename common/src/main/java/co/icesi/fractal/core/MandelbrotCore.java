package co.icesi.fractal.core;

import FractalApp.TaskTile;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * Motor matematico y de renderizado del Conjunto de Mandelbrot.
 */
public class MandelbrotCore {

    /**
     * Calcula el numero de iteraciones antes de que el punto complejo (cr, ci) escape.
     * Funcion de escape: z_{n+1} = z_n^2 + c con z_0 = 0
     * Condicion de escape: |z|^2 > 4.0
     */
    public static int computePoint(double cr, double ci, int maxIter) {
        int iter = 0;
        double zx = 0.0;
        double zy = 0.0;
        double zx2 = 0.0;
        double zy2 = 0.0;

        while (zx2 + zy2 <= 4.0 && iter < maxIter) {
            zy = 2.0 * zx * zy + ci;
            zx = zx2 - zy2 + cr;
            zx2 = zx * zx;
            zy2 = zy * zy;
            iter++;
        }
        return iter;
    }

    /**
     * Computa una baldosa o bloque de la imagen a partir de una definicion TaskTile.
     * Utiliza la rejilla global de pixeles para evitar discrepancias por redondeo flotante.
     */
    public static int[][] computeTile(TaskTile task) {
        int[][] result = new int[task.tileHeight][task.tileWidth];

        double dx = (task.totalWidth > 1) ? (task.xMax - task.xMin) / (task.totalWidth - 1) : 0.0;
        double dy = (task.totalHeight > 1) ? (task.yMax - task.yMin) / (task.totalHeight - 1) : 0.0;

        for (int r = 0; r < task.tileHeight; r++) {
            int globalY = task.startY + r;
            double ci = task.yMin + globalY * dy;
            for (int c = 0; c < task.tileWidth; c++) {
                int globalX = task.startX + c;
                double cr = task.xMin + globalX * dx;
                result[r][c] = computePoint(cr, ci, task.maxIterations);
            }
        }
        return result;
    }

    /**
     * Genera y guarda la imagen en formato PNG aplicando una paleta de colores continua HSB.
     */
    public static void saveImage(int[][] matrix, int width, int height, int maxIter, String outputPath) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int iter = matrix[y][x];
                int rgbColor;
                if (iter >= maxIter) {
                    rgbColor = Color.BLACK.getRGB(); // Puntos dentro del conjunto
                } else {
                    float hue = (float) iter / 256.0f;
                    float saturation = 0.85f;
                    float brightness = (iter > 0) ? 0.95f : 0.0f;
                    rgbColor = Color.HSBtoRGB(hue, saturation, brightness);
                }
                image.setRGB(x, y, rgbColor);
            }
        }

        File outputFile = new File(outputPath);
        if (outputFile.getParentFile() != null) {
            outputFile.getParentFile().mkdirs();
        }
        ImageIO.write(image, "PNG", outputFile);
        System.out.println("[MandelbrotCore] Imagen guardada satisfactoriamente en: " + outputFile.getAbsolutePath());
    }
}
