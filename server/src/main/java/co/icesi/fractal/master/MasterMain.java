package co.icesi.fractal.master;

import com.zeroc.Ice.*;

/**
 * Punto de entrada del Servidor Maestro (Master).
 * Inicializa el Communicator de Ice con pool de hilos multiconcurrente y publica el MasterService.
 */
public class MasterMain {

    public static void main(String[] args) {
        int port = (args.length >= 1) ? Integer.parseInt(args[0]) : 10000;

        // Configuracion de propiedades de Ice para alta concurrencia y envio de matrices grandes
        InitializationData initData = new InitializationData();
        initData.properties = Util.createProperties(args);
        initData.properties.setProperty("Ice.ThreadPool.Server.Size", "16");
        initData.properties.setProperty("Ice.ThreadPool.Server.SizeMax", "64");
        initData.properties.setProperty("MasterAdapter.ThreadPool.Size", "16");
        initData.properties.setProperty("MasterAdapter.ThreadPool.SizeMax", "64");
        initData.properties.setProperty("Ice.MessageSizeMax", "204800"); // 200 MB max

        try (Communicator communicator = Util.initialize(initData)) {
            String endpoint = "tcp -h 0.0.0.0 -p " + port;
            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints("MasterAdapter", endpoint);

            MasterServiceI masterService = new MasterServiceI();
            adapter.add(masterService, Util.stringToIdentity("MasterService"));
            adapter.activate();

            System.out.println("=================================================");
            System.out.println("        NODO MAESTRO (MASTER) INICIADO");
            System.out.println("=================================================");
            System.out.printf("Escuchando en: %s%n", endpoint);
            System.out.printf("ThreadPool Server: 16 hilos base (max 64)%n");
            System.out.println("Esperando conexion y registro de Workers...");
            System.out.println("Presione Ctrl+C para finalizar.");
            System.out.println("=================================================");

            communicator.waitForShutdown();
        }
    }
}
