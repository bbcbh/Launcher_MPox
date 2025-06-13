package sim;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
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

	private HashMap<Integer, ArrayList<Integer>> vaccine_record_map = new HashMap<>(); // ArrayList<Integer> = {pid,
																						// dose_days};

	private Comparator<ArrayList<Integer>> cmp_vaccine_record = new Comparator<>() {
		@Override
		public int compare(ArrayList<Integer> o1, ArrayList<Integer> o2) {
			int res = 0;
			// Last dose time
			res = Integer.compare(o1.get(o1.size() - 1), o2.get(o2.size() - 1));

			if (res == 0) {
				// PID
				res = Integer.compare(o1.get(0), o2.get(0));
			}
			return res;
		}

	};

	public Runnable_ClusterModel_MPox(long cMap_seed, long sim_seed, int[] POP_COMPOSITION, ContactMap BASE_CONTACT_MAP,
			int NUM_TIME_STEPS_PER_SNAP, int NUM_SNAP, File baseDir) {
		super(cMap_seed, sim_seed, POP_COMPOSITION, BASE_CONTACT_MAP, NUM_TIME_STEPS_PER_SNAP, NUM_SNAP);

		this.baseDir = baseDir;
		File vaccine_coverage_preset = new File(baseDir, fName_vaccine_coverage);
		if (vaccine_coverage_preset.exists()) {
			RandomGenerator RNG = new MersenneTwisterRandomGenerator(sim_seed);
			
			try {
				int maxPersonId = 220000;

				ArrayList<ArrayList<Integer>> vaccine_record_per_line_pt_ordered = new ArrayList<>();

				for (int pid = 0; pid < maxPersonId; pid++) {
					ArrayList<Integer> vacc_rec = new ArrayList<>();
					vacc_rec.add(pid);
					vacc_rec.add(Integer.MIN_VALUE);
					vaccine_record_map.put(pid, vacc_rec);
				}

				vaccine_record_per_line_pt_ordered.addAll(vaccine_record_map.values());

				String[] vacc_lines = util.Util_7Z_CSV_Entry_Extract_Callable
						.extracted_lines_from_text(vaccine_coverage_preset);
				int v_start = 730;
				int[] booster_range = (int[]) vaccine_setting_global[VACCINE_SETTING_INDEX_BOOSTER_WINDOW];

				for (int line_pt = 1; line_pt < vacc_lines.length; line_pt++) {
					String[] vaccine_ent = vacc_lines[line_pt].split(",");
					int v_range = Integer.parseInt(vaccine_ent[0]);
					for (int dose_count = 1; dose_count < vaccine_ent.length; dose_count++) {
						int num_dose = Integer.parseInt(vaccine_ent[dose_count]);
						while (num_dose > 0) {
							ArrayList<Integer> key;
							int dose_time = v_start + RNG.nextInt(v_range);
							int[] candidate_range;
							if (line_pt == 1) {
								candidate_range = new int[] { 0, vaccine_record_per_line_pt_ordered.size() };
							} else {
								key = new ArrayList<>();
								key.add(-1); // PID
								key.add(Integer.MIN_VALUE);
								key.add(dose_time - booster_range[booster_range.length - 1]); // Any has dose before
																								// that is
																								// consider as new
								int booster_pt = ~Collections.binarySearch(vaccine_record_per_line_pt_ordered, key,
										cmp_vaccine_record);
								if (dose_count == 1) {
									// New vaccine
									candidate_range = new int[] { 0, booster_pt };
								} else {
									// Pick those who already has a dose previously
									// Must have last dose more than booster_range[0]
									key = new ArrayList<>();
									key.add(-1);
									key.add(dose_time - booster_range[0]);
									int booster_end_pt = ~Collections.binarySearch(vaccine_record_per_line_pt_ordered,
											key, cmp_vaccine_record);
									candidate_range = new int[] { booster_pt, booster_end_pt };
								}
							}
							key = new ArrayList<>();
							for (int k = candidate_range[0]; k < candidate_range[1]; k++) {
								if (line_pt != 1 && dose_count > 2) {
									ArrayList<Integer> vacc_candidate = vaccine_record_per_line_pt_ordered.get(k);
									if (vacc_candidate.size() > 2) {
										// Fitler out those with less booster then specified
										// rec = {pid, -inf, booster_date0, ....}

										int last_dose_time = vacc_candidate.get(vacc_candidate.size() - 1);
										int num_previous_dose = 1;

										// Check for continuous booster chain
										for (int pt = vacc_candidate.size() - 2; pt >= 2; pt--) {
											int dose_ck = vacc_candidate.get(pt);
											int gap_time = last_dose_time - dose_ck;
											if (booster_range[0] <= gap_time && gap_time < booster_range[1]) {
												num_previous_dose++;
												last_dose_time = dose_ck;
											} else {
												break;
											}

										}

										if (num_previous_dose == dose_count - 1) {
											key.add(k);
										}
									}
								} else {
									key.add(k);
								}
							}

							int index;

							if (key.size() > 0) {
								index = key.get(RNG.nextInt(key.size()));
							} else if (candidate_range[0] < candidate_range[1]) {
								// No candidate total suitable but there is a list
								index = candidate_range[0] + (RNG.nextInt(candidate_range[1] - candidate_range[0]));
							} else {
								// Any
								index = RNG.nextInt(vaccine_record_per_line_pt_ordered.size());
							}

							ArrayList<Integer> vacc_candidate = vaccine_record_per_line_pt_ordered.remove(index);

							vacc_candidate.add(dose_time);

							if (line_pt == 1 && dose_count > 1) {
								// Special case to include backward dose count
								int last_dose = dose_time;
								int num_booster = dose_count - 1;

								while (num_booster > 0) {
									last_dose = last_dose
											- (booster_range[0] + RNG.nextInt(booster_range[1] - booster_range[0]));
									// rec = {pid, -inf, booster_date0, ....}
									vacc_candidate.add(2, last_dose);
									num_booster--;
								}
							}
							num_dose--;

						} // End of while (num_dose >= 0) {

					} // End for (int d = 1; d < vaccine_ent.length; d++) {

					// Reset vaccine_record_ordered
					vaccine_record_per_line_pt_ordered.clear();
					vaccine_record_per_line_pt_ordered.addAll(vaccine_record_map.values());
					Collections.sort(vaccine_record_per_line_pt_ordered, cmp_vaccine_record);

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
			int[] booster_range = (int[]) vaccine_setting_global[VACCINE_SETTING_INDEX_BOOSTER_WINDOW];
			double[] vaccine_eff = (double[]) vaccine_setting_global[VACCINE_SETTING_INDEX_INIT_VACCINE_EFFECT];
			int vaccine_dur = (int) vaccine_setting_global[VACCINE_SETTING_INDEX_DURATION];
			double vaccine_wane_rate = (double) vaccine_setting_global[VACCINE_SETTING_INDEX_DECAY_RATE];

			// rec = {pid, -inf, booster_date0, ....}
			if (vac_rec != null && vac_rec.size() > 2) {
				int last_dose_at = vac_rec.size() - 1;
				while (last_dose_at > 2 && vac_rec.get(last_dose_at) > currentTime) {
					last_dose_at--;
				}

				// Check for valid booster (i.e. gap time within booster window)
				if (last_dose_at >= 2 && vac_rec.get(last_dose_at) < currentTime) {
					int numDose = 1;
					int lastDoseTime = vac_rec.get(vac_rec.size() - 1);
					for (int d = last_dose_at-1; d >= 2; d--) {
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
						double vaccine_effect_init = vaccine_eff[Math.min(numDose-1, vaccine_eff.length - 1)];
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
	protected void postTimeStep(int currentTime) {
		// TODO Auto-generated method stub
		super.postTimeStep(currentTime);
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
