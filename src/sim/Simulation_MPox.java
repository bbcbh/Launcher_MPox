package sim;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import population.Population_Bridging;
import util.PropValUtils;

public class Simulation_MPox extends Simulation_ClusterModelTransmission {

	public static void main(String[] args) throws IOException, InterruptedException {
		final String USAGE_INFO = String.format(
				"Usage: java %s PROP_FILE_DIRECTORY " + "<-export_skip_backup> <-printProgress> <-seedMap=SEED_MAP>\n",
				Simulation_MPox.class.getName());
		if (args.length < 1) {
			System.out.println(USAGE_INFO);
			System.exit(0);
		} else {
			Simulation_MPox.launch(args, new Simulation_MPox());

		}
	}

	@Override
	public Abstract_Runnable_ClusterModel_Transmission generateDefaultRunnable(long cMap_seed, long sim_seed,
			Properties loadedProperties) {
		this.loadedProperties = loadedProperties;

		int[] pop_composition = new int[] { 0, 0, 220000, 0 };
		int num_snap = 1;
		int num_time_steps_per_snap = 1;

		if (loadedProperties.containsKey(SimulationInterface.PROP_NAME[SimulationInterface.PROP_NUM_SNAP])) {
			num_snap = Integer.parseInt(
					loadedProperties.getProperty(SimulationInterface.PROP_NAME[SimulationInterface.PROP_NUM_SNAP]));
		}
		if (loadedProperties.containsKey(SimulationInterface.PROP_NAME[SimulationInterface.PROP_SNAP_FREQ])) {
			num_time_steps_per_snap = Integer.parseInt(
					loadedProperties.getProperty(SimulationInterface.PROP_NAME[SimulationInterface.PROP_SNAP_FREQ]));
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

	@Override
	protected void zipSelectedOutputs(String file_name, String zip_file_name)
			throws IOException, FileNotFoundException {
		// Do nothing 
		
//		Pattern pattern_include_file;
//		String zip_file_name_mod = zip_file_name;
//		if (preGenSeedFile != null) {
//			zip_file_name_mod = preGenSeedFile.getName().replace('.', '_') + "_" + zip_file_name;
//			pattern_include_file = Pattern.compile("(\\[" + preGenSeedFile.getName() + ".*\\]){0,1}"
//					+ file_name.replaceAll("%d", "(-{0,1}(?!0)\\\\d+)"));
//
//		} else {
//			pattern_include_file = Pattern
//					.compile("(\\[.*\\]){0,1}" + file_name.replaceAll("%d", "(-{0,1}(?!0)\\\\d+)"));
//		}
//		zipSelectedOutputs(baseDir, zip_file_name_mod, pattern_include_file, exportSkipBackup);
	}

	@Override
	protected void finalise_simulations() throws IOException, FileNotFoundException {
		// Do nothing 
		
//		if (preGenSeedFile != null) {
//			Pattern pattern_csv_extra = Pattern
//					.compile("(?:\\[" + preGenSeedFile.getName() + ".*\\]){0,1}(.*)_(-{0,1}\\d+)_-{0,1}\\d+.csv");
//
//			Pattern pattern_csv_cMap = Pattern
//					.compile(FILENAME_FORMAT_ALL_CMAP.replaceAll("%d", "(-{0,1}(?!0)\\\\d+)"));
//
//			FileFilter extra_filter = new FileFilter() {
//				@Override
//				public boolean accept(File pathname) {
//					return !pattern_csv_cMap.matcher(pathname.getName()).matches()
//							&& pattern_csv_extra.matcher(pathname.getName()).matches();
//				}
//			};
//
//			File[] extra_csv = baseDir.listFiles(extra_filter);
//
//			while (extra_csv != null && extra_csv.length > 0) {
//				Matcher m = pattern_csv_extra.matcher(extra_csv[0].getName());
//				m.matches();
//				String filename_id = m.group(1);
//				String baseContactSeed_str = m.group(2);
//				zipSelectedOutputs(String.format("%s_%s_%%d.csv", filename_id, baseContactSeed_str),
//						String.format("%s_%s.csv.7z", filename_id, baseContactSeed_str));
//				extra_csv = baseDir.listFiles(extra_filter);
//			}
//
//		}

	}

}
