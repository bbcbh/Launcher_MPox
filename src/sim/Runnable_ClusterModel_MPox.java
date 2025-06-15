package sim;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import person.AbstractIndividualInterface;
import random.MersenneTwisterRandomGenerator;
import random.RandomGenerator;
import relationship.ContactMap;

public class Runnable_ClusterModel_MPox extends Runnable_ClusterModel_Transmission {

	public static final String trans_offset_key = "TRANSOFFSET";
	public static final Pattern sim_switch_replace = Pattern.compile("SIM_SWITCH_REPLACE_(\\d+)");

	private static int VACCINE_SETTING_INDEX_DURATION = 0;
	private static int VACCINE_SETTING_INDEX_DECAY_RATE = VACCINE_SETTING_INDEX_DURATION + 1;
	private static int VACCINE_SETTING_INDEX_BOOSTER_WINDOW = VACCINE_SETTING_INDEX_DECAY_RATE + 1;
	private static int VACCINE_SETTING_INDEX_INIT_VACCINE_EFFECT = VACCINE_SETTING_INDEX_BOOSTER_WINDOW + 1;

	private static final String fName_vaccine_coverage = "Vaccine_Coverage.csv";
	private static final String fName_vaccine_hist = "Vaccine_Hist.csv";

	private Object[] vaccine_setting_global = new Object[] {
			// Duration of 5 years (US CDC)
			5 * AbstractIndividualInterface.ONE_YEAR_INT,
			// Decay rate - 15% per year (JYNNEOS 10-20%)
			Math.log(1 - 0.15) / AbstractIndividualInterface.ONE_YEAR_INT,
			// Booster limit range (anything bigger revert to first dose)
			new int[] { 24, 35 },
			// D1 (from chat)
			new double[] { 0.76, 0.82 }, };

	private int trans_offset = 0;

	private RandomGenerator vaccine_rng = null;
	private HashMap<Integer, ArrayList<Integer>> vaccine_record_map = new HashMap<>();
	// Key = pid, V = ArrayList<Integer> of {pid, dose_days};
	private HashMap<Integer, ArrayList<Integer>> dosage_candidate = new HashMap<>();
	// Key = number of continous dose so far (or in partneship if == 0), V =
	// ArrayList<Integer> of candidate pid
	private HashMap<Integer, int[]> vaccine_schedule = new HashMap<>();
	// K = day, V = Number vacciation in terms of dosage
	private int vaccine_backward_fill = -1;

	@Override
	protected int[] updateCMap(ContactMap cMap, int currentTime, Integer[][] edges_array, int edges_array_pt,
			HashMap<Integer, ArrayList<Integer[]>> edgesToRemove, ArrayList<Integer> included_pids) {
		int[] res = super.updateCMap(cMap, currentTime, edges_array, edges_array_pt, edgesToRemove, included_pids);

		// Update vaccination
		int[] num_vaccine = vaccine_schedule.remove(currentTime);
		if (num_vaccine != null) {
			ArrayList<Integer> in_partnership = new ArrayList<>();
			for (Integer[] rel : cMap.edgeSet()) {
				for (Integer pid : new Integer[] { rel[CONTACT_MAP_EDGE_P1], rel[CONTACT_MAP_EDGE_P2] }) {
					int pt = Collections.binarySearch(in_partnership, pid);
					if (pt < 0) {
						in_partnership.add(~pt, pid);
					}
				}
			}

			int[] booster_range = (int[]) vaccine_setting_global[VACCINE_SETTING_INDEX_BOOSTER_WINDOW];

			// Clear the new dose
			if (dosage_candidate.size() > 0) {
				dosage_candidate.get(0).clear();
			} else {
				for (int i = 0; i < num_vaccine.length; i++) {
					dosage_candidate.put(i, new ArrayList<>());
				}
			}
			// Remove all potential booster that is already expired.
			for (int i = 1; i < dosage_candidate.size(); i++) {
				ArrayList<Integer> dosage_ent = dosage_candidate.get(i);
				for (int j = 0; j < dosage_ent.size(); j++) {
					int pid = dosage_ent.get(j);
					ArrayList<Integer> vacc_rec = vaccine_record_map.get(pid);
					if (vacc_rec.get(vacc_rec.size() - 1) < (currentTime - booster_range[1])) {
						dosage_ent.remove(j);
					}
				}
			}
			// Add new dose to those with partner today
			for (Integer pid : in_partnership) {
				ArrayList<Integer> vacc_rec = vaccine_record_map.get(pid);
				// vacc_rec = {pid, dose_days...};
				if (vacc_rec == null) {
					vacc_rec = new ArrayList<>();
					vacc_rec.add(pid);
					vaccine_record_map.put(pid, vacc_rec);
				}
				boolean no_booster = vacc_rec.size() == 1;
				if (vacc_rec.size() > 1) {
					no_booster = vacc_rec.get(vacc_rec.size() - 1) < (currentTime - booster_range[1]);
				}
				if (no_booster) {
					ArrayList<Integer> dosage_ent = dosage_candidate.get(0);
					if (dosage_ent == null) {
						dosage_ent = new ArrayList<>();
					}
					dosage_ent.add(pid);
				}
			}

			Collections.sort(dosage_candidate.get(0));

			for (int d = 0; d < num_vaccine.length; d++) {
				if (num_vaccine[d] > 0) {
					ArrayList<Integer> vacc_candidates = dosage_candidate.get(d);
					Integer[] vacc_today = vacc_candidates.toArray(new Integer[0]);
					if (d != 0) {
						ArrayList<Integer> vacc_rec_order_filtered = new ArrayList<>();
						for (int pid : vacc_today) {
							ArrayList<Integer> vacc_rec = vaccine_record_map.get(pid);
							if (vacc_rec.size() > 1) {
								int last_dose_at = vacc_rec.get(vacc_rec.size() - 1);
								if (last_dose_at < currentTime - booster_range[0]
										&& currentTime - booster_range[1] < last_dose_at) {
									vacc_rec_order_filtered.add(pid);
								}
							}
						}
						vacc_today = vacc_rec_order_filtered.toArray(new Integer[0]);

						if (currentTime < vaccine_backward_fill && vacc_today.length < num_vaccine[d]) {
							for (Integer pid : dosage_candidate.get(0)) {
								int pt = Collections.binarySearch(vacc_rec_order_filtered, pid);
								if (pt < 0) {
									vacc_rec_order_filtered.add(~pt, pid);
								}
							}
							vacc_today = vacc_rec_order_filtered.toArray(new Integer[0]);

						}

					}
					if (num_vaccine[d] < vacc_today.length) {
						vacc_today = util.ArrayUtilsRandomGenerator.randomSelect(vacc_today, num_vaccine[d],
								vaccine_rng);
					}
					for (int pid : vacc_today) {
						vaccine_record_map.get(pid).add(currentTime);

						// Special case for filling backward
						if (currentTime < vaccine_backward_fill && d > 0) {
							int numExtra = d;
							while (numExtra > 0) {
								int booster_time = vaccine_record_map.get(pid).get(1);
								int extra_time = booster_time - (booster_range[0]
										+ vaccine_rng.nextInt((booster_range[1] - booster_range[0])));

								vaccine_record_map.get(pid).add(1, extra_time);
								numExtra--;
							}
						}
						if (d < num_vaccine.length) {
							int pt;
							pt = Collections.binarySearch(dosage_candidate.get(d), pid);
							if (pt >= 0) {
								dosage_candidate.get(d).remove(pt);
							}

							ArrayList<Integer> vacc_rec = vaccine_record_map.get(pid);
							if (vacc_rec.size() > 1) {
								int v_ck = vacc_rec.size() - 1;
								int num_booster_chain = 1;
								int gapTime = vacc_rec.get(v_ck) - vacc_rec.get(v_ck - 1);
								while (v_ck > 1 && booster_range[0] <= gapTime && gapTime < booster_range[1]) {
									num_booster_chain++;
									v_ck--;
									gapTime = vacc_rec.get(v_ck) - vacc_rec.get(v_ck - 1);
								}

								num_booster_chain = Math.min(num_booster_chain, num_vaccine.length - 1);
								pt = Collections.binarySearch(dosage_candidate.get(num_booster_chain), pid);
								if (pt < 0) {
									dosage_candidate.get(num_booster_chain).add(~pt, pid);
								}
							}

						}
					}

				}
			}

		}

		return res;
	}

	public Runnable_ClusterModel_MPox(long cMap_seed, long sim_seed, int[] POP_COMPOSITION, ContactMap BASE_CONTACT_MAP,
			int NUM_TIME_STEPS_PER_SNAP, int NUM_SNAP, File baseDir) {
		super(cMap_seed, sim_seed, POP_COMPOSITION, BASE_CONTACT_MAP, NUM_TIME_STEPS_PER_SNAP, NUM_SNAP);

		this.baseDir = baseDir;
		File vaccine_coverage_preset = new File(baseDir, fName_vaccine_coverage);
		if (vaccine_coverage_preset.exists()) {

			vaccine_rng = new MersenneTwisterRandomGenerator(sim_seed);

			try {

				// Set vaccine_start, decay rate and vaccine effect
				String[] vacc_lines = util.Util_7Z_CSV_Entry_Extract_Callable
						.extracted_lines_from_text(vaccine_coverage_preset);
				String[] header_line = vacc_lines[0].split(","); // Start_time.decay_rate_per_year, dose_eff_1,
																	// dose_eff_2....,

				double first_ent = Double.parseDouble(header_line[0]);

				int v_start = (int) first_ent;
				double rate = first_ent - v_start;
				vaccine_setting_global[VACCINE_SETTING_INDEX_DECAY_RATE] = Math.log(1 - rate)
						/ AbstractIndividualInterface.ONE_YEAR_INT;

				double[] vacc_eff = new double[header_line.length - 1];
				for (int d = 0; d < vacc_eff.length; d++) {
					vacc_eff[d] = Double.parseDouble(header_line[d + 1]);
				}
				vaccine_setting_global[VACCINE_SETTING_INDEX_INIT_VACCINE_EFFECT] = vacc_eff;

				// Set daily vaccine schedule

				for (int line_pt = 1; line_pt < vacc_lines.length; line_pt++) {
					String[] vaccine_ent = vacc_lines[line_pt].split(",");
					int v_range = Integer.parseInt(vaccine_ent[0]);

					if (vaccine_backward_fill < 0) {
						vaccine_backward_fill = v_start + v_range;
					}

					for (int dose_count = 1; dose_count < vaccine_ent.length; dose_count++) {
						int num_dose = Integer.parseInt(vaccine_ent[dose_count]);
						while (num_dose > 0) {
							int dose_time = v_start + vaccine_rng.nextInt(v_range);
							int[] num_vaccine = vaccine_schedule.get(dose_time);
							if (num_vaccine == null) {
								num_vaccine = new int[vaccine_ent.length - 1];
								vaccine_schedule.put(dose_time, num_vaccine);
							}
							num_vaccine[dose_count - 1]++;
							num_dose--;
						}

					}

					v_start += v_range;

				}

			} catch (IOException e) {
				e.printStackTrace(System.err);

			}

		}

	}

	@Override
	protected float vaccine_effect(int currentTime, float[][][] vaccine_effect_global, int srcId, int gender_src,
			int site_src, int[] vaccine_expiry_src, int tarId, int gender_target, int site_target,
			int[] vaccine_expiry_target, float transProb_pre_vaccine) {
		float transProbAdj = transProb_pre_vaccine;

		if (vaccine_record_map != null) {
			// Only have protective effect
			ArrayList<Integer> vac_rec = vaccine_record_map.get(tarId);

			// rec = {pid, booster_date0, ....}
			if (vac_rec != null && vac_rec.size() > 1) {

				int[] booster_range = (int[]) vaccine_setting_global[VACCINE_SETTING_INDEX_BOOSTER_WINDOW];
				double[] vaccine_eff = (double[]) vaccine_setting_global[VACCINE_SETTING_INDEX_INIT_VACCINE_EFFECT];
				int vaccine_dur = (int) vaccine_setting_global[VACCINE_SETTING_INDEX_DURATION];
				double vaccine_wane_rate = (double) vaccine_setting_global[VACCINE_SETTING_INDEX_DECAY_RATE];

				int last_dose_at = vac_rec.size() - 1;
				while (last_dose_at > 1 && vac_rec.get(last_dose_at) > currentTime) {
					last_dose_at--;
				}

				// Check for valid booster (i.e. gap time within booster window)
				if (vac_rec.get(last_dose_at) < currentTime) {
					int numDose = 1;
					int lastDoseTime = vac_rec.get(vac_rec.size() - 1);
					for (int d = last_dose_at - 1; d > 0; d--) {
						int dose_ck = vac_rec.get(d);
						int gap_time = lastDoseTime - dose_ck;
						if (booster_range[0] <= gap_time && gap_time < booster_range[1]) {
							numDose++;
							lastDoseTime = dose_ck;
						} else {
							break;
						}
					}

					if (vac_rec.get(last_dose_at) < currentTime
							&& currentTime < vac_rec.get(last_dose_at) + vaccine_dur) {
						// Assume vaccine last for 5 years
						double vaccine_effect_init = vaccine_eff[Math.min(numDose - 1, vaccine_eff.length - 1)];
						double vacc_eff = vaccine_effect_init
								* Math.exp(vaccine_wane_rate * (currentTime - vac_rec.get(last_dose_at)));
						transProbAdj *= (1 - vacc_eff);

					}

				}

			}
		}

		return transProbAdj;
	}

	@Override
	protected void postSimulation(Object[] simulation_store) {
		super.postSimulation(simulation_store);

		// Print vaccine stat
		String filePrefix = this.getRunnableId() == null ? String.format("[%d,%d]", this.cMAP_SEED, this.sIM_SEED)
				: this.getRunnableId();

		if (!vaccine_record_map.isEmpty()) {
			try {
				PrintWriter pWri = new PrintWriter(new File(baseDir, filePrefix + fName_vaccine_hist));
				pWri.println("ID,Dose_Time...");
				Integer[] keys = vaccine_record_map.keySet().toArray(new Integer[0]);
				Arrays.sort(keys);
				for (Integer key : keys) {
					ArrayList<Integer> ent = vaccine_record_map.get(key);
					if (ent.size() > 0) { // At least one dose
						for (int i = 0; i < ent.size(); i++) {
							if (i != 0) {
								pWri.print(',');
							}
							pWri.print(ent.get(i));
						}
						pWri.println();
					}
				}

				pWri.close();
			} catch (IOException e) {
				e.printStackTrace(System.err);
			}

		}

	}

	@Override
	public ArrayList<Integer> loadOptParameter(String[] parameter_settings, double[] point, int[][] seedInfectNum,
			boolean display_only) {
		ArrayList<String> parameter_settings_filtered = new ArrayList<>();
		ArrayList<Double> point_filtered = new ArrayList<>();

		for (int i = 0; i < parameter_settings.length; i++) {
			if (trans_offset_key.equals(parameter_settings[i])) {
				trans_offset = (int) point[i];
			} else if (sim_switch_replace.matcher(parameter_settings[i]).matches()) {
				// SIM_SWITCH_REPLACE_(\\d+)
				Matcher m = sim_switch_replace.matcher(parameter_settings[i]);
				m.find();
				Integer switch_replace_index = Integer.parseInt(m.group(1));

				Integer[] key_arr = propSwitch_map.keySet().toArray(new Integer[0]);
				Arrays.sort(key_arr);

				Integer org_key = key_arr[switch_replace_index];
				Integer new_key = (int) point[i];
				propSwitch_map.put(new_key, propSwitch_map.remove(org_key));

			} else {
				parameter_settings_filtered.add(parameter_settings[i]);
				point_filtered.add(point[i]);
			}
		}

		// Update PropSwitch_map if needed
		if (trans_offset != 0) {
			HashMap<Integer, HashMap<Integer, String>> propSwitch_map_adj = new HashMap<>();
			for (Entry<Integer, HashMap<Integer, String>> ent : propSwitch_map.entrySet()) {
				Integer newKey = ent.getKey() + trans_offset;
				propSwitch_map_adj.put(newKey, ent.getValue());
			}
			super.setPropSwitch_map(propSwitch_map_adj);
		}

		double[] point_filtered_arr = new double[point_filtered.size()];
		for (int i = 0; i < point_filtered_arr.length; i++) {
			point_filtered_arr[i] = point_filtered.get(i);
		}

		return super.loadOptParameter(parameter_settings_filtered.toArray(new String[0]), point_filtered_arr,
				seedInfectNum, display_only);
	}

}
