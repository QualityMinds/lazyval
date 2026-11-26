package scenarios.edge;

import com.qualityminds.lazyval.LazyvalConfiguration;
import scenarios.shared.Points;

/** Lists a type from a shared Java module, compiled as the scenario's library, as an external type. */
@LazyvalConfiguration(externalTypes = {Points.class})
public class SharedPointsConfig {}
