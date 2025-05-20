package sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;

import relationship.ContactMap;

public class Runnable_ClusterModel_MPox extends Runnable_ClusterModel_Transmission {

	public static final String trans_offset_key = "TRANS_OFFSET";

	private int trans_offset = 0;

	public Runnable_ClusterModel_MPox(long cMap_seed, long sim_seed, int[] POP_COMPOSITION, ContactMap BASE_CONTACT_MAP,
			int NUM_TIME_STEPS_PER_SNAP, int NUM_SNAP) {
		super(cMap_seed, sim_seed, POP_COMPOSITION, BASE_CONTACT_MAP, NUM_TIME_STEPS_PER_SNAP, NUM_SNAP);

	}

	@Override
	public void setPropSwitch_map(HashMap<Integer, HashMap<Integer, String>> propSwitch_map) {		
		// Add trans_offset
		HashMap<Integer, HashMap<Integer, String>> propSwitch_map_adj = new HashMap<>();		
		for(Entry<Integer, HashMap<Integer, String>> ent : propSwitch_map.entrySet()) {
			Integer newKey = ent.getKey() + trans_offset;			
			propSwitch_map_adj.put(newKey, ent.getValue());
		}						
		super.setPropSwitch_map(propSwitch_map_adj);
	}

	@Override
	public ArrayList<Integer> loadOptParameter(String[] parameter_settings, double[] point, int[][] seedInfectNum,
			boolean display_only) {
		ArrayList<String> parameter_settings_filtered = new ArrayList<>();
		ArrayList<Double> point_filtered = new ArrayList<>();

		for (int i = 0; i < parameter_settings.length; i++) {
			if (trans_offset_key.equals(parameter_settings[i])) {
				trans_offset = (int) point[i];

			} else {
				parameter_settings_filtered.add(parameter_settings[i]);
				point_filtered.add(point[i]);
			}
		}

		double[] point_filtered_arr = new double[point_filtered.size()];
		for (int i = 0; i < point_filtered_arr.length; i++) {
			point_filtered_arr[i] = point_filtered.get(i);
		}

		return super.loadOptParameter(parameter_settings_filtered.toArray(new String[0]), point_filtered_arr,
				seedInfectNum, display_only);
	}

}
