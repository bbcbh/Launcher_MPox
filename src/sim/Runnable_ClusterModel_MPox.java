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
	public static final Pattern vacc_prop_replace = Pattern.compile("VACC_PROP_(\\d+)");

	private static int VACCINE_SETTING_INDEX_DURATION = 0;
	private static int VACCINE_SETTING_INDEX_DECAY_RATE = VACCINE_SETTING_INDEX_DURATION + 1;
	private static int VACCINE_SETTING_INDEX_BOOSTER_WINDOW = VACCINE_SETTING_INDEX_DECAY_RATE + 1;
	private static int VACCINE_SETTING_INDEX_INIT_VACCINE_EFFECT = VACCINE_SETTING_INDEX_BOOSTER_WINDOW + 1;

	private static final String fName_vaccine_coverage = "Vaccine_Coverage.csv";
	private static final String fName_vaccine_hist = "Vaccine_Hist.csv";
	private static final String fName_vaccine_stat = "Vaccine_Stat.csv";

	private Object[] vaccine_setting_global = new Object[] {
			// Duration of 5 years (US CDC)
			5 * AbstractIndividualInterface.ONE_YEAR_INT,
			// Decay rate - 15% per year (JYNNEOS 10-20%)
			Math.log(1 - 0.15) / AbstractIndividualInterface.ONE_YEAR_INT,
			// Booster limit range (anything bigger revert to first dose)
			new int[] { 24, 35 },
			// D1 (from chat)
			new double[] { 0.76, 0.82 },

	};

	private int trans_offset = 0;

	private RandomGenerator vaccine_rng = null;
	private HashMap<Integer, ArrayList<Integer>> vaccine_record_map = new HashMap<>();
	// Key = pid, V = ArrayList<Integer> of {pid, dose_days};

	private HashMap<Integer, ArrayList<Integer>> vaccine_candidate_by_booster_count = new HashMap<>();
	// Key = number of continuous booster (or in partnership if == 0), V =
	// ArrayList<Integer> of candidate pid

	private HashMap<Integer, int[]> vaccine_schedule = new HashMap<>();
	// K = day, V = Number vaccination in terms of dosage

	private ArrayList<String[]> vaccine_pre_outbreak_coverage = new ArrayList<String[]>();
	private int time_outbreak_start = -1;

	private HashMap<Integer, int[]> vaccine_pop_stats = new HashMap<>();
	// K = day, V = int[]{num_ever_vaccinated, num_within_booster_range,
	// num_vaccine_effected_transmission};
	private static final int VACC_STAT_EVER_VACC = 0;
	private static final int VACC_STAT_IN_BOOSTER_RANGE = VACC_STAT_EVER_VACC + 1;
	private static final int VACC_STAT_TRANSMISSON_EFFCTED = VACC_STAT_IN_BOOSTER_RANGE + 1;
	private static final int LENGTH_VACC_STAT = VACC_STAT_TRANSMISSON_EFFCTED + 1;

	public Runnable_ClusterModel_MPox(long cMap_seed, long sim_seed, int[] POP_COMPOSITION, ContactMap BASE_CONTACT_MAP,
			int NUM_TIME_STEPS_PER_SNAP, int NUM_SNAP, File baseDir) {
		super(cMap_seed, sim_seed, POP_COMPOSITION, BASE_CONTACT_MAP, NUM_TIME_STEPS_PER_SNAP, NUM_SNAP);

		this.baseDir = baseDir;
		File vaccine_coverage_post_outbreak = new File(baseDir, fName_vaccine_coverage);
		if (vaccine_coverage_post_outbreak.exists()) {

			vaccine_rng = new MersenneTwisterRandomGenerator(sim_seed);

			try {

				// Set vaccine_start, decay rate and vaccine effect
				String[] vacc_lines = util.Util_7Z_CSV_Entry_Extract_Callable
						.extracted_lines_from_text(vaccine_coverage_post_outbreak);
				String[] header_line = vacc_lines[0].split(","); // Start_time.decay_rate_per_year, dose_eff_1,
																	// dose_eff_2....,

				double first_ent = Double.parseDouble(header_line[0]);

				int v_start = (int) first_ent;
				double rate = first_ent - v_start;
				vaccine_setting_global[VACCINE_SETTING_INDEX_DECAY_RATE] = Math.log(1 - rate)
						/ AbstractIndividualInterface.ONE_YEAR_INT;

				time_outbreak_start = v_start;

				double[] vacc_eff = new double[header_line.length - 1];
				for (int d = 0; d < vacc_eff.length; d++) {
					vacc_eff[d] = Double.parseDouble(header_line[d + 1]);
				}
				vaccine_setting_global[VACCINE_SETTING_INDEX_INIT_VACCINE_EFFECT] = vacc_eff;

				// Set daily vaccine schedule

				for (int line_pt = 1; line_pt < vacc_lines.length; line_pt++) {
					String[] vaccine_ent = vacc_lines[line_pt].split(",");
					int v_range = Integer.parseInt(vaccine_ent[0]);

					if (v_range > 0) {

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
					} else {
						vaccine_pre_outbreak_coverage.add(vaccine_ent);
					}
				}

			} catch (IOException e) {
				e.printStackTrace(System.err);

			}

		}

	}

	@Override
	protected int[] updateCMap(ContactMap cMap, int currentTime, Integer[][] edges_array, int edges_array_pt,
			HashMap<Integer, ArrayList<Integer[]>> edgesToRemove, ArrayList<Integer> included_pids) {
		int[] res = super.updateCMap(cMap, currentTime, edges_array, edges_array_pt, edgesToRemove, included_pids);

		// Update vaccination
		int[] booster_range = (int[]) vaccine_setting_global[VACCINE_SETTING_INDEX_BOOSTER_WINDOW];

		// Pre-outbreak vaccination

		if (time_outbreak_start == currentTime && vaccine_pre_outbreak_coverage.size() > 0) {
			int v_start = time_outbreak_start;
			Integer[] pid_all = cMap.vertexSet().toArray(new Integer[0]);
			Arrays.sort(pid_all);

			Integer[] pid_vaccinated_last = new Integer[0];
			for (int i = 0; i < vaccine_pre_outbreak_coverage.size(); i++) {
				String[] ent = vaccine_pre_outbreak_coverage.get(i);
				v_start += Integer.parseInt(ent[0]);
			}
			Integer[][] pid_vacc = new Integer[2][0];
			for (int i = 0; i < vaccine_pre_outbreak_coverage.size(); i++) {
				String[] ent = vaccine_pre_outbreak_coverage.get(i);
				Integer time_range = -Integer.parseInt(ent[0]);
				ArrayList<Integer> d1_candidate = new ArrayList<Integer>(Arrays.asList(pid_all));

				for (Integer p : pid_vaccinated_last) {
					int pt = Collections.binarySearch(d1_candidate, p);
					if (pt >= 0) {
						d1_candidate.remove(pt);
					}
				}

				// First dose

				int num_vacc_d1 = Integer.parseInt(ent[1]);
				pid_vacc[0] = util.ArrayUtilsRandomGenerator.randomSelect(d1_candidate.toArray(new Integer[0]),
						Math.min(d1_candidate.size(), num_vacc_d1), vaccine_rng);

				// Second dose +
				int num_vacc_d2 = 0;
				for (int dosePt = 2; dosePt < ent.length; dosePt++) {
					num_vacc_d2 += Integer.parseInt(ent[dosePt]);
				}

				if (num_vacc_d2 > 0) {
					pid_vacc[1] = util.ArrayUtilsRandomGenerator.randomSelect(pid_vaccinated_last,
							Math.min(pid_vaccinated_last.length, num_vacc_d2), vaccine_rng);
				} else {
					pid_vacc[1] = new Integer[0];
				}

				for (Integer[] vacc : pid_vacc) {
					for (int pid : vacc) {
						ArrayList<Integer> vacc_rec = vaccine_record_map.get(pid);
						if (vacc_rec == null) {
							vacc_rec = new ArrayList<>();
							vacc_rec.add(pid);
							vaccine_record_map.put(pid, vacc_rec);
						}
						vacc_rec.add(v_start + vaccine_rng.nextInt(time_range));
					}
				}
				pid_vaccinated_last = pid_vacc[0];
				v_start += time_range;
			}

			int dose_count = vaccine_schedule.values().iterator().next().length;

			for (int i = 0; i < 5; i++) {
				vaccine_candidate_by_booster_count.put(i, new ArrayList<>());
			}

			for (Integer[] vacc : pid_vacc) {
				for (int pid : vacc) {
					int num_continious_booster = getNumContinuousBooster(currentTime, vaccine_record_map.get(pid));
					ArrayList<Integer> dosage_ent = vaccine_candidate_by_booster_count.get(num_continious_booster);

					int pt = Collections.binarySearch(dosage_ent, pid);
					if (pt < 0) {
						dosage_ent.add(~pt, pid);
					}

				}
			}
		} // End of if (time_outbreak_start == currentTime ...

		int[] num_vaccine = vaccine_schedule.remove(currentTime);
		if (num_vaccine != null) {
			ArrayList<Integer> in_partnership = new ArrayList<>();
			for (Integer[] rel : cMap.edgeSet()) {
				if (rel[CONTACT_MAP_EDGE_START_TIME] <= currentTime
						&& currentTime <= rel[CONTACT_MAP_EDGE_START_TIME] + rel[CONTACT_MAP_EDGE_DURATION]) {
					for (Integer pid : new Integer[] { rel[CONTACT_MAP_EDGE_P1], rel[CONTACT_MAP_EDGE_P2] }) {
						int pt = Collections.binarySearch(in_partnership, pid);
						if (pt < 0) {
							in_partnership.add(~pt, pid);
						}
					}
				}
			}

			// Clear the Dose 1 candidate
			if (vaccine_candidate_by_booster_count.size() > 0) {
				vaccine_candidate_by_booster_count.get(0).clear();
			}
			// Remove all potential booster that is already expired.
			for (int i = 1; i < vaccine_candidate_by_booster_count.size(); i++) {
				ArrayList<Integer> dosage_ent = vaccine_candidate_by_booster_count.get(i);
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
				int num_continious_booster = getNumContinuousBooster(currentTime, vacc_rec);
				num_continious_booster = Math.min(num_continious_booster, num_vaccine.length - 1);
				ArrayList<Integer> dosage_ent = vaccine_candidate_by_booster_count.get(num_continious_booster);
				int pt = Collections.binarySearch(dosage_ent, pid);
				if (pt < 0) {
					dosage_ent.add(~pt, pid);
				}
			}

			Collections.sort(vaccine_candidate_by_booster_count.get(0));

			ArrayList<Integer> vacc_today_all_groups = new ArrayList<>();
			for (int dose_pt = 0; dose_pt < num_vaccine.length; dose_pt++) {
				if (num_vaccine[dose_pt] > 0) {
					ArrayList<Integer> vacc_candidates = vaccine_candidate_by_booster_count.get(dose_pt);
					Integer[] vacc_today = vacc_candidates.toArray(new Integer[0]);
					if (dose_pt != 0) {
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
					}
					if (num_vaccine[dose_pt] < vacc_today.length) {
						vacc_today = util.ArrayUtilsRandomGenerator.randomSelect(vacc_today, num_vaccine[dose_pt],
								vaccine_rng);
					} else if (num_vaccine[dose_pt] > vacc_today.length) {
						// Not enough candidate - fill the rest with 'artificial' vaccination record
						int fill_index = vacc_today.length;
						int pt;
						Arrays.sort(vacc_today);
						vacc_today = Arrays.copyOf(vacc_today, num_vaccine[dose_pt]);
						Integer[] all_pids = cMap.vertexSet().toArray(new Integer[0]);

						while (fill_index < vacc_today.length) {
							Integer pid_a = all_pids[vaccine_rng.nextInt(all_pids.length)];
							ArrayList<Integer> vacc_rec = vaccine_record_map.get(pid_a);

							// Make sure the person is not a booster candidate
							if (getNumContinuousBooster(currentTime, vacc_rec) == 0) {

								// Make sure chosen not are not already vaccinated today
								pt = Arrays.binarySearch(vacc_today, 0, fill_index, pid_a);
								if (pt < 0) {
									pt = Collections.binarySearch(vacc_today_all_groups, pid_a);
									if (pt < 0) {
										vacc_today[fill_index] = pid_a;
										if (vacc_rec == null) {
											vacc_rec = new ArrayList<>();
											vacc_rec.add(pid_a);
											vaccine_record_map.put(pid_a, vacc_rec);
										}

										int boost_time = currentTime;
										for (int b = 0; b < dose_pt; b++) {
											boost_time = boost_time - (booster_range[0]
													+ vaccine_rng.nextInt(booster_range[1] - booster_range[0]));
											vacc_rec.add(1, boost_time);

										}
										vacc_today_all_groups.add(~pt, pid_a);
										fill_index++;
									}
								}
							}
						}
					}
					for (int pid : vacc_today) {
						int pt;
						// Add to vacc_today_all_group collection (if needed)
						pt = Collections.binarySearch(vacc_today_all_groups, pid);
						if (pt < 0) {
							vacc_today_all_groups.add(~pt, pid);
						}
						// Remove non-Dose 1 candidates (as they will be move to different booster count
						if (dose_pt > 0) {
							pt = Collections.binarySearch(vaccine_candidate_by_booster_count.get(dose_pt), pid);
							if (pt >= 0) {
								vaccine_candidate_by_booster_count.get(dose_pt).remove(pt);
							}
						}

					} // End of for (int pid : vacc_today) {
				} // End of if (num_vaccine[dose_pt] > 0) {
			} // End of for (int dose_pt = 0; dose_pt < num_vaccine.length; dose_pt++) {

			for (int pid : vacc_today_all_groups) {
				int pt;
				ArrayList<Integer> vacc_rec = vaccine_record_map.get(pid);
				if (vacc_rec == null) {
					// vacc_rec = {pid, dose_days...};
					vacc_rec = new ArrayList<>();
					vacc_rec.add(pid);
					vaccine_record_map.put(pid, vacc_rec);
				}
				vacc_rec.add(currentTime);
				int num_continious_booster = getNumContinuousBooster(currentTime, vacc_rec);
				num_continious_booster = Math.min(num_continious_booster, num_vaccine.length - 1);
				pt = Collections.binarySearch(vaccine_candidate_by_booster_count.get(num_continious_booster), pid);
				if (pt < 0) {
					vaccine_candidate_by_booster_count.get(num_continious_booster).add(~pt, pid);
				}
			}

		}

		if (vaccine_record_map.size() > 0) {

			// Update vaccined_protected_tranmission stat
			int[] vacc_stat = vaccine_pop_stats.get(currentTime);

			if (vacc_stat == null) {
				vacc_stat = new int[LENGTH_VACC_STAT];
				vacc_stat[VACC_STAT_EVER_VACC] = vaccine_record_map.size();
				for (Entry<Integer, ArrayList<Integer>> vacc_rec_ent : vaccine_record_map.entrySet()) {
					ArrayList<Integer> vacc_rec = vacc_rec_ent.getValue();
					int gapTime = currentTime - vacc_rec.get(vacc_rec.size() - 1);
					if (booster_range[0] <= gapTime && gapTime < booster_range[1]) {
						vacc_stat[VACC_STAT_IN_BOOSTER_RANGE]++;
					}
				}
				vaccine_pop_stats.put(currentTime, vacc_stat);
			}
		}

		return res;
	}

	private int getNumContinuousBooster(int propose_vaccination_time, ArrayList<Integer> vacc_rec) {
		int[] booster_range = (int[]) vaccine_setting_global[VACCINE_SETTING_INDEX_BOOSTER_WINDOW];
		int num_continious_booster = 0;
		if (vacc_rec != null) {
			if (vacc_rec.size() > 1) {
				int v_ck = vacc_rec.size() - 1;
				int last_dose_time = vacc_rec.get(v_ck);
				if (last_dose_time > propose_vaccination_time - booster_range[1]) {
					num_continious_booster = 1;
					if (vacc_rec.size() > 2) {
						int gapTime = vacc_rec.get(v_ck) - vacc_rec.get(v_ck - 1);
						while (v_ck > 1 && booster_range[0] <= gapTime && gapTime < booster_range[1]) {
							num_continious_booster++;
							v_ck--;
							gapTime = vacc_rec.get(v_ck) - vacc_rec.get(v_ck - 1);
						}
					}
				}
			}

		}
		return num_continious_booster;
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

						// Update vaccined_protected_tranmission
						int[] vacc_stat = vaccine_pop_stats.get(currentTime);
						vacc_stat[VACC_STAT_TRANSMISSON_EFFCTED]++;
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
					if (ent.size() > 1) { // At least one dose
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
		if (!vaccine_pop_stats.isEmpty()) {
			try {
				PrintWriter pWri = new PrintWriter(new File(baseDir, filePrefix + fName_vaccine_stat));
				pWri.println("Time,Ever_Vaccinated,In_Booster_Window,Transmission_Effected");
				Integer[] times = vaccine_pop_stats.keySet().toArray(new Integer[0]);
				Arrays.sort(times);
				for (Integer t : times) {
					int[] vacc_stat = vaccine_pop_stats.get(t);
					pWri.print(t);
					for (int c = 0; c < vacc_stat.length; c++) {
						pWri.print(',');
						pWri.print(vacc_stat[c]);
					}
					pWri.println();
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
			} else if (vacc_prop_replace.matcher(parameter_settings[i]).matches()) {
				Matcher m = vacc_prop_replace.matcher(parameter_settings[i]);
				m.find();
				Integer vacc_prop_index = Integer.parseInt(m.group(1));
				switch (vacc_prop_index) {
				case 0:
					vaccine_setting_global[VACCINE_SETTING_INDEX_DECAY_RATE] = Math.log(1 - point[i])
							/ AbstractIndividualInterface.ONE_YEAR_INT;
					break;
				default:
					((double[]) vaccine_setting_global[VACCINE_SETTING_INDEX_INIT_VACCINE_EFFECT])[vacc_prop_index
							- 1] = point[i];

				}

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
