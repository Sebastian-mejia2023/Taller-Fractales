module FractalApp {

    // Secuencias para matrices bidimensionales de iteraciones
    sequence<int> IntSeq;
    sequence<IntSeq> IterationMatrix;

    // Configuracion de renderizado enviada por el Cliente al Master
    struct RenderConfig {
        string jobId;
        int width;
        int height;
        double xMin;
        double xMax;
        double yMin;
        double yMax;
        int maxIterations;
        int strategyType;   // 0 = FixedCount (N tareas), 1 = FixedTileSize (tamano baldosa)
        int strategyParam;  // Valor de N o del tamano en pixeles (ej. 4 tareas o 64 px)
    };

    // Unidad de trabajo (baldosa) encolada en el Master y obtenida por los Workers
    struct TaskTile {
        bool hasTask;       // true si contiene una tarea valida; false si la cola esta vacia
        string jobId;
        int taskId;
        int startX;
        int startY;
        int tileWidth;
        int tileHeight;
        int totalWidth;
        int totalHeight;
        double xMin;
        double xMax;
        double yMin;
        double yMax;
        int maxIterations;
    };

    // Resultado parcial devuelto por un Worker al Master
    struct TileResult {
        string jobId;
        int taskId;
        int startX;
        int startY;
        int tileWidth;
        int tileHeight;
        IterationMatrix pixels;
        long computeTimeMs;
        string workerId;
    };

    exception FractalException {
        string reason;
    };

    // Interfaz de callback que expone cada Worker para recibir avisos asincronos
    interface WorkerObserver {
        void notifyTasksAvailable(string jobId);
    };

    // Servicio central provisto por el Maestro
    interface MasterService {
        // Invocado por el Cliente para solicitar el renderizado completo
        IterationMatrix renderFractal(RenderConfig config) throws FractalException;

        // Registro y desregistro de Workers con su respectivo observador
        void registerWorker(string workerId, WorkerObserver* observer);
        void unregisterWorker(string workerId);

        // Metodo de "pull": el Worker solicita una tarea pendiente de la cola
        TaskTile pollTask(string workerId);

        // Metodo asincrono: el Worker entrega el resultado de una baldosa computada
        void reportResult(TileResult result) throws FractalException;

        // Consultas de estado
        idempotent int getWorkerCount();
        idempotent int getPendingTasksCount();
    };

};
