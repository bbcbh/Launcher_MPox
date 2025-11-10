package sim;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import population.Population_Bridging;
import relationship.ContactMap;
import util.PropValUtils;

public class Simulation_MPox extends Simulation_ClusterModelTransmission {
	boolean load_full_map;

	public Simulation_MPox(boolean load_full_map) {
		this.load_full_map = load_full_map;
	}

	public static void main(String[] args) throws IOException, InterruptedException {
		final String USAGE_INFO = String.format(
				"Usage: java %s PROP_FILE_DIRECTORY " + "<-export_skip_backup> <-printProgress> <-seedMap=SEED_MAP>\n",
				Simulation_MPox.class.getName());
		if (args.length < 1) {
			System.out.println(USAGE_INFO);
			System.exit(0);
		} else {
			boolean load_full_map = true;
			for (String arg : args) {
				if ("-partialLoadMap".equals(arg)) {
					load_full_map = false;
				}
			}

			Simulation_ClusterModelTransmission.launch(args, new Simulation_MPox(load_full_map));

		}
	}

	@Override
	protected void loadAllContactMap(ArrayList<File> preGenClusterMap, HashMap<Long, ArrayList<File>> cmap_file_collection,
			HashMap<Long, ContactMap> cMap_Map) throws FileNotFoundException, IOException, InterruptedException {

		if (load_full_map) {
			super.loadAllContactMap(preGenClusterMap, cmap_file_collection, cMap_Map);

		} else {
			for (File element : preGenClusterMap) {
				System.out.printf("Loading on ContactMap files located at %s.\n", element.getAbsolutePath());
				Matcher m = Pattern.compile(REGEX_ALL_CMAP).matcher(element.getName());
				m.matches();
				long cMap_seed = Long.parseLong(m.group(1));
				cMap_Map.put(cMap_seed, null);
				cmap_file_collection.put(cMap_seed, new ArrayList<File>(List.of(element)));

			}
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
				baseContactMapMapping.get(cMap_seed), num_time_steps_per_snap, num_snap, baseDir);
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

		// Remove zipping due to sync issue

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
//				final Pattern pattern_include_file = Pattern
//						.compile("(\\[" + preGenSeedFile.getName() +".*\\]){0,1}" + filename_id + "_-{0,1}\\d+.*csv" );
//
//				Simulation_ClusterModelTransmission.zipSelectedOutputs(
//						baseDir,
//						String.format("%s_%s_[%s].csv.7z", filename_id, baseContactSeed_str, preGenSeedFile.getName()),
//						pattern_include_file, exportSkipBackup);
//				extra_csv = baseDir.listFiles(extra_filter);
//			}
//
//		}

	}

}
