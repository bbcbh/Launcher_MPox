package analysis;

import java.io.File;
import java.io.FileFilter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import util.Util_7Z_CSV_Entry_Extract_Callable;

public class PostSim_Analysis_MPoxCII {

	public static void main(String[] args) throws FileNotFoundException, IOException {
		File baseDir = new File("C:\\Users\\bhui\\OneDrive - UNSW\\Results_Mpox_CII");
		// generateDataDumpFromDir(baseDir);
		//generateParamList(baseDir);		
		
		File file_sort_param = new File(baseDir, "param_list.csv");
		
		
		

	}

	public static void generateParamList(File baseDir) throws FileNotFoundException, IOException {
		// Input
		File file_dataDump = new File(baseDir, "data_dump.csv");
		File file_sortDataIndex = new File(baseDir, "sorted_index.csv");
		File file_rowStart = new File(baseDir, "row_start.csv");
		File file_sqSum = new File(baseDir, "square_sum.csv");

		// Output
		File file_sort_param = new File(baseDir, "param_list.csv");

		String[] dataDump = Util_7Z_CSV_Entry_Extract_Callable.extracted_lines_from_text(file_dataDump);

		String[] seed_line = dataDump[1].split(",");
		String[] beta_line = dataDump[2].split(",");
		String[] index_line = util.Util_7Z_CSV_Entry_Extract_Callable.extracted_lines_from_text(file_sortDataIndex)[0]
				.split(",");
		String[] rowStart = util.Util_7Z_CSV_Entry_Extract_Callable.extracted_lines_from_text(file_rowStart)[0]
				.split(",");
		String[] sqSum = util.Util_7Z_CSV_Entry_Extract_Callable.extracted_lines_from_text(file_sqSum)[0].split(",");

		HashMap<Integer, String[]> seedBetaRowStartMap = new HashMap<>();
		for (int i = 0; i < index_line.length; i++) {			
			String[] ent = new String[] { seed_line[i], beta_line[i], rowStart[i], sqSum[i] };
			seedBetaRowStartMap.put(i, ent);

		}

		PrintWriter pwri = new PrintWriter(file_sort_param);
		pwri.println("Seed,Beta,Row_Start,SqSum");

		for (String key : index_line) {
			String[] entry = seedBetaRowStartMap.get(Integer.parseInt(key)-1);
			String rawStr = Arrays.toString(entry);
			pwri.println(rawStr.substring(1, rawStr.length() - 1)); // Trim [ ]
		}

		pwri.close();
	}

	public static void generateDataDumpFromDir(File baseDir) throws FileNotFoundException, IOException {

		File dataDump = new File(baseDir, "data_dump.csv");

		String format_dirName = "MP_(\\d+)";
		String format_entfName = "\\[Seed_List_\\d+.csv,(\\d+)\\].*.csv";
		String format_zipName = "Incidence_Person_-5060704338552416757.csv.7z";

		final int SEED_LIST_SIMSEED = 1;
		final int SEED_LIST_BETA = SEED_LIST_SIMSEED + 1;
		final int OFFSET_SEED_LIST = SEED_LIST_BETA;
		final int COL_SEL = 3;

		Pattern pattern_dirName = Pattern.compile(format_dirName);
		Pattern pattern_entry_name = Pattern.compile(format_entfName);
		Pattern pattern_zip = Pattern.compile(format_zipName);
		Pattern pattern_zip_backup = Pattern.compile(format_dirName + "_(\\d+).7z");

		int colPt = 0;
		ArrayList<StringBuilder> line_collections = new ArrayList<>();
		HashMap<Integer, Integer> lastVal = new HashMap<>(); // Key = col;

		File[] resDirs = baseDir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				return pathname.isDirectory() && pattern_dirName.matcher(pathname.getName()).matches();
			}
		});

		StringBuilder hearderRow = new StringBuilder();
		StringBuilder seedRow = new StringBuilder();
		StringBuilder paramRow = new StringBuilder();

		line_collections.add(hearderRow);
		line_collections.add(seedRow);
		line_collections.add(paramRow);

		hearderRow.append("Time");
		seedRow.append(Float.NaN);
		paramRow.append(Float.NaN);

		for (File resDir : resDirs) {
			Matcher m = pattern_dirName.matcher(resDir.getName());
			if (m.matches()) {
				int dirNum = Integer.parseInt(m.group(1));
				File seedFile = new File(resDir, String.format("Seed_List_%d.csv", dirNum));
				String[] seedList = util.Util_7Z_CSV_Entry_Extract_Callable.extracted_lines_from_text(seedFile);

				File[] resFiles = resDir.listFiles(new FileFilter() {
					@Override
					public boolean accept(File pathname) {
						Matcher m1 = pattern_entry_name.matcher(pathname.getName());
						Matcher m2 = pattern_zip.matcher(pathname.getName());
						return m1.matches() || m2.matches();
					}
				});

				HashMap<String, ArrayList<String[]>> entMap = new HashMap<>();
				ArrayList<String[]> ent;
				for (File resFile : resFiles) {
					if (resFile.getName().endsWith("7z")) {
						try {
							entMap = util.Util_7Z_CSV_Entry_Extract_Callable.extractedLinesFrom7Zip(resFile, entMap);
						} catch (Exception e) {
							e.printStackTrace(System.err);
							File[] backupZips = resDir.listFiles(new FileFilter() {
								@Override
								public boolean accept(File pathname) {
									return pathname.getName().startsWith(format_zipName)
											&& !pathname.getName().equals(format_zipName);
								}
							});

							Arrays.sort(backupZips, new Comparator<File>() {
								@Override
								public int compare(File o1, File o2) {
									Matcher m1 = pattern_zip_backup.matcher(o1.getName());
									Matcher m2 = pattern_zip_backup.matcher(o2.getName());
									m1.matches();
									m2.matches();
									return Long.compare(Long.parseLong(m1.group(1)), Long.parseLong(m2.group(1)));
								}
							});

							boolean loadSuc = false;
							for (int i = backupZips.length - 1; i >= 0 && !loadSuc; i--) {
								File backup = backupZips[i];
								try {
									entMap = util.Util_7Z_CSV_Entry_Extract_Callable.extractedLinesFrom7Zip(backup,
											entMap);
									loadSuc = true;
								} catch (Exception ex) {
									loadSuc = false;
								}
							}
							System.out.printf("Try loading backup zip (%d found)... %s\n", backupZips.length,
									loadSuc ? "Success" : "FAILED");

						}
					} else {
						String[] lines = util.Util_7Z_CSV_Entry_Extract_Callable.extracted_lines_from_text(resFile);
						ent = new ArrayList<>();
						for (String line : lines) {
							if (line.length() > 0) {
								ent.add(line.split(","));
							}
						}
						entMap.put(resFile.getName(), ent);
					}
				}

				for (String fName : entMap.keySet()) {
					ent = entMap.get(fName);
					Matcher mf = pattern_entry_name.matcher(fName);
					mf.matches();
					int rowNum = Integer.parseInt(mf.group(1));

					hearderRow.append(String.format(",MP_%d_Seed_%d", dirNum, rowNum));

					// Seed
					Long sim_seed = Long.parseLong(seedList[rowNum + 1].split(",")[SEED_LIST_SIMSEED]);
					seedRow.append(',');
					seedRow.append(sim_seed);

					// Beta
					double beta = Double.parseDouble(seedList[rowNum + 1].split(",")[SEED_LIST_BETA]);
					paramRow.append(',');
					paramRow.append(beta);

					// Last ent
					int lastEnt = Integer.parseInt(ent.get(ent.size() - 1)[COL_SEL]);
					lastVal.put(colPt, lastEnt);

					StringBuilder lineBuilder;
					for (int i = 1; i < ent.size(); i++) { // Skip header
						String[] lineSp = ent.get(i);
						if ((i + OFFSET_SEED_LIST) < line_collections.size()) { // Offset by Header line and beta line
							lineBuilder = line_collections.get(i + OFFSET_SEED_LIST);
						} else {
							lineBuilder = new StringBuilder();
							line_collections.add(lineBuilder);
							// Time
							lineBuilder.append(lineSp[0]);
							// Other columns if needed
							for (int pCol = 0; pCol < colPt; pCol++) {
								lineBuilder.append(',');
								lineBuilder.append(lastVal.get(pCol));
							}
						}
						lineBuilder.append(',');
						lineBuilder.append(lineSp[COL_SEL]);
					}
					// Extra lines
					for (int eLPt = ent.size() + OFFSET_SEED_LIST; eLPt < line_collections.size(); eLPt++) {
						lineBuilder = line_collections.get(eLPt);
						lineBuilder.append(',');
						lineBuilder.append(lastVal.get(colPt));
					}
					colPt++;
				}
			}

			System.out.printf("Extracting result from %s completed.\n", resDir.getName());

		}

		PrintWriter pWri = new PrintWriter(dataDump);
		for (StringBuilder line : line_collections) {
			pWri.println(line.toString());
		}
		pWri.close();

		System.out.printf("Data matrix generated at %s. # Col = %d.\n", dataDump.getAbsolutePath(), colPt);

	}

}
