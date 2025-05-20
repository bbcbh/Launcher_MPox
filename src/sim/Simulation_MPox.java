package sim;

import java.io.IOException;
import java.util.Properties;

import population.Population_Bridging;
import util.PropValUtils;

public class Simulation_MPox extends Simulation_ClusterModelTransmission {
	
	public static void main(String[] args) throws IOException, InterruptedException {
		final String USAGE_INFO = String.format(
				"Usage: java %s PROP_FILE_DIRECTORY "
						+ "<-export_skip_backup> <-printProgress> <-seedMap=SEED_MAP>\n",
						Simulation_MPox.class.getName());
		if (args.length < 1) {
			System.out.println(USAGE_INFO);
			System.exit(0);
		} else {
			Simulation_ClusterModelTransmission.launch(args);
		}
	}
	
	@Override
	public Abstract_Runnable_ClusterModel_Transmission generateDefaultRunnable(long cMap_seed, long sim_seed, 
			Properties loadedProperties) {			
		this.loadedProperties = loadedProperties;
		
		int[] pop_composition = new int[] {0, 0, 220000, 0};
		int num_snap = 1;
		int num_time_steps_per_snap = 1;
		
		if (loadedProperties.containsKey(SimulationInterface.PROP_NAME[SimulationInterface.PROP_NUM_SNAP])) {
			num_snap = Integer.parseInt(
					loadedProperties.getProperty(SimulationInterface.PROP_NAME[SimulationInterface.PROP_NUM_SNAP]));
		}
		if (loadedProperties.containsKey(SimulationInterface.PROP_NAME[SimulationInterface.PROP_SNAP_FREQ])) {
			num_time_steps_per_snap = Integer.parseInt(loadedProperties
					.getProperty(SimulationInterface.PROP_NAME[SimulationInterface.PROP_SNAP_FREQ]));
		}		
		String popCompositionKey = Simulation_ClusterModelTransmission.POP_PROP_INIT_PREFIX
				+ Integer.toString(Population_Bridging.FIELD_POP_COMPOSITION);
		if (loadedProperties.containsKey(popCompositionKey)) {
			pop_composition = (int[]) PropValUtils.propStrToObject(loadedProperties.getProperty(popCompositionKey),
					int[].class);
		}
		return new Runnable_ClusterModel_MPox(cMap_seed, sim_seed, pop_composition, 
				baseContactMapMapping.get(cMap_seed), num_time_steps_per_snap, num_snap);
	}

}
