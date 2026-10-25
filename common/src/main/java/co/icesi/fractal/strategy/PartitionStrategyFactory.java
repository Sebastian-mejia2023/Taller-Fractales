package co.icesi.fractal.strategy;

/**
 * Fabrica para instanciar la estrategia de particion correspondiente.
 */
public class PartitionStrategyFactory {

    public static final int STRATEGY_FIXED_COUNT = 0;
    public static final int STRATEGY_FIXED_TILE_SIZE = 1;

    public static TaskPartitionStrategy createStrategy(int strategyType, int parameter) {
        switch (strategyType) {
            case STRATEGY_FIXED_TILE_SIZE:
                return new FixedTileSizePartitionStrategy(parameter > 0 ? parameter : 64);
            case STRATEGY_FIXED_COUNT:
            default:
                return new FixedCountPartitionStrategy(parameter > 0 ? parameter : 4);
        }
    }
}
