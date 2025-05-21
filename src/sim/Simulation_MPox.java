package sim;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import population.Population_Bridging;
import relationship.ContactMap;
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
			// Simulation_MPox.launch(args);
			File baseDir = null;
			File propFile = null;
			File[] preGenClusterMap = new File[0];

			baseDir = new File(args[0]);

			if (baseDir != null) {
				boolean flag_exportSkipBackup = false;
				boolean flag_setPrintProgress = false;
				File seed_map = null;

				if (args.length > 1) {
					for (int ai = 1; ai < args.length; ai++) {
						if (LAUNCH_ARGS_SKIP_BACKUP.equals(args[ai])) {
							flag_exportSkipBackup = true;
						}
						if (LAUNCH_ARGS_PRINT_PROGRESS.equals(args[ai])) {
							flag_setPrintProgress = true;
						}
						if (args[ai].startsWith(LAUNCH_ARGS_SEED_MAP)) {
							seed_map = new File(baseDir, args[ai].substring(LAUNCH_ARGS_SEED_MAP.length()));
						}
					}

				}

				if (baseDir.isDirectory()) {
					Simulation_MPox sim = new Simulation_MPox();
					sim.setBaseDir(baseDir);
					if (flag_exportSkipBackup) {
						sim.setExportSkipBackup(true);
					}
					if (flag_setPrintProgress) {
						sim.setPrintProgress(true);
					}

					if (seed_map != null && seed_map.isFile()) {
						sim.loadPreGenSimSeed(seed_map);
					}
					// Reading of PROP file
					propFile = new File(baseDir, SimulationInterface.FILENAME_PROP);
					FileInputStream fIS = new FileInputStream(propFile);
					Properties prop = new Properties();
					prop.loadFromXML(fIS);
					fIS.close();
					sim.loadProperties(prop);
					System.out.println(String.format("Properties file < %s > loaded.", propFile.getAbsolutePath()));

					// Seed map
					ArrayList<Long> cMapSeeds = null;
					if (seed_map != null) {
						try {
							BufferedReader reader = new BufferedReader(new FileReader(seed_map));
							cMapSeeds = new ArrayList<>();
							String ent;
							reader.readLine(); // Skip first line
							while ((ent = reader.readLine()) != null) {
								long val = Long.parseLong(ent.split(",")[0]);
								int k = Collections.binarySearch(cMapSeeds, val);
								if (k < 0) {
									cMapSeeds.add(~k, val);
								}
							}
							reader.close();
						} catch (IOException e) {
							e.printStackTrace(System.err);
						}
					}

					Collections.sort(cMapSeeds);

					// Check for contact cluster generated
					final String REGEX_STR = FILENAME_FORMAT_ALL_CMAP.replaceAll("%d", "(-{0,1}(?!0)\\\\d+)");
					File contactMapDir = baseDir;

					if (prop.getProperty(PROP_CONTACT_MAP_LOC) != null) {
						contactMapDir = new File(prop.getProperty(PROP_CONTACT_MAP_LOC));
						if (!contactMapDir.exists() || !contactMapDir.isDirectory()) {
							contactMapDir = baseDir;
						}
					}
					preGenClusterMap = contactMapDir.listFiles(new FileFilter() {
						@Override
						public boolean accept(File pathname) {
							return pathname.isFile() && Pattern.matches(REGEX_STR, pathname.getName());

						}
					});

					long tic = System.currentTimeMillis();
					HashMap<Long, ContactMap> cMap_Map = new HashMap<>();
					HashMap<Long, File[]> cmap_file_collection = new HashMap<Long, File[]>();

					// Single contact map version
					if (cMapSeeds != null) {
						ArrayList<File> preGenClusterMapArr = new ArrayList<>();
						Pattern p = Pattern.compile(REGEX_STR);
						for (File f : preGenClusterMap) {
							Matcher m = p.matcher(f.getName());
							m.matches();
							Long mSeed = Long.parseLong(m.group(1));
							int k = Collections.binarySearch(cMapSeeds, mSeed);
							if (k >= 0) {
								preGenClusterMapArr.add(f);
								cMapSeeds.remove(k);
							}
						}
						preGenClusterMap = preGenClusterMapArr.toArray(new File[preGenClusterMapArr.size()]);

					}

					Arrays.sort(preGenClusterMap);
					for (File element : preGenClusterMap) {
						System.out.printf("Loading (in series) on ContactMap located at %s.\n",
								element.getAbsolutePath());
						Matcher m = Pattern.compile(REGEX_STR).matcher(element.getName());
						m.matches();
						long cMap_seed = Long.parseLong(m.group(1));
						ContactMap cMap = extractedCMapfromFile(element);
						cMap_Map.put(cMap_seed, cMap);
						cmap_file_collection.put(cMap_seed, new File[] { element });

					}

					System.out.printf("%d ContactMap loaded. Time required = %.3fs\n", cMap_Map.size(),
							(System.currentTimeMillis() - tic) / 1000.0f);

					sim.setBaseContactMap(cMap_Map);
					sim.setMultiContactMapStrMapping(cmap_file_collection);
					sim.generateOneResultSet();
					System.out.println(String.format("All simulation(s) completed. Runtime (total)= %.2fs",
							(System.currentTimeMillis() - tic) / 1000f));

				}
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
				baseContactMapMapping.get(cMap_seed), num_time_steps_per_snap, num_snap);
	}

}
