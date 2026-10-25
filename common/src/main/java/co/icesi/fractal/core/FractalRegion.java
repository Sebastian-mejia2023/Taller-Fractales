package co.icesi.fractal.core;

/**
 * Catalogo de regiones y presets de exploracion del Conjunto de Mandelbrot.
 * Permite alternar entre la vista panoramica clasica y zonas de alta densidad
 * de calculo (zooms profundos como Seahorse Valley) para pruebas de estres y benchmarking.
 */
public class FractalRegion {

    private final String id;
    private final String name;
    private final double xMin;
    private final double xMax;
    private final double yMin;
    private final double yMax;
    private final int defaultMaxIter;
    private final String description;

    public FractalRegion(String id, String name, double xMin, double xMax, double yMin, double yMax, int defaultMaxIter, String description) {
        this.id = id;
        this.name = name;
        this.xMin = xMin;
        this.xMax = xMax;
        this.yMin = yMin;
        this.yMax = yMax;
        this.defaultMaxIter = defaultMaxIter;
        this.description = description;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getXMin() { return xMin; }
    public double getXMax() { return xMax; }
    public double getYMin() { return yMin; }
    public double getYMax() { return yMax; }
    public int getDefaultMaxIter() { return defaultMaxIter; }
    public String getDescription() { return description; }

    /**
     * Vista Panoramica Clasica (Macro):
     * Abarca todo el cuerpo principal. El 76% de los pixeles escapan en < 50 iteraciones.
     */
    public static final FractalRegion CLASSIC = new FractalRegion(
        "classic",
        "Vista Panoramica Clasica",
        -2.0, 0.5,
        -1.25, 1.25,
        3000,
        "Vista global clasica del conjunto. Carga heterogenea con amplio espacio exterior."
    );

    /**
     * Valle de los Caballitos de Mar (Seahorse Valley) - Configuración de Estrés:
     * Zona de frontera hiperdensa donde los filamentos requieren miles de iteraciones.
     * Ideal para benchmarking de alto rendimiento a 4K y 5,000-10,000 iteraciones.
     */
    public static final FractalRegion SEAHORSE = new FractalRegion(
        "seahorse",
        "Valle de los Caballitos de Mar (Seahorse Valley)",
        -0.75, -0.74,
        0.09, 0.10,
        5000,
        "Frontera hiperdensa entre el cardioide y el bulbo izquierdo. Carga extrema para pruebas de estres del cluster."
    );

    /**
     * Valle de los Elefantes:
     * Hendidura derecha con trompas y espirales complejas.
     */
    public static final FractalRegion ELEPHANT = new FractalRegion(
        "elephant",
        "Valle de los Elefantes",
        0.25, 0.35,
        -0.05, 0.05,
        5000,
        "Espirales densas en el extremo derecho del cardioide principal."
    );

    /**
     * Mini-Mandelbrot Satelite:
     * Copia autosimilar completa en la antena oeste (-1.75).
     */
    public static final FractalRegion MINI = new FractalRegion(
        "mini",
        "Mini-Mandelbrot Satelite",
        -1.78, -1.74,
        -0.02, 0.02,
        5000,
        "Copia autosimilar completa. Concentracion maxima de pixeles interiores que agotan el maxIter."
    );

    /**
     * Resuelve un preset por su nombre o alias textual.
     */
    public static FractalRegion fromName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return SEAHORSE; // Por defecto usar la region de estres
        }
        switch (name.toLowerCase().trim()) {
            case "classic":
            case "macro":
            case "general":
                return CLASSIC;
            case "elephant":
                return ELEPHANT;
            case "mini":
            case "satellite":
                return MINI;
            case "seahorse":
            case "stress":
            case "valley":
            default:
                return SEAHORSE;
        }
    }
}
