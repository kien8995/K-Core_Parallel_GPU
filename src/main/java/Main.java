import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Spliterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import com.aparapi.Range;

public class Main {

	static {
		System.setProperty("com.aparapi.executionMode", "GPU");
		System.setProperty("com.aparapi.dumpProfilesOnExit", "true");
		System.setProperty("com.aparapi.enableExecutionModeReporting", "false");
		System.setProperty("com.aparapi.enableShowGeneratedOpenCL", "true");
	}

	// path to input/output file
	private static final String INPUT = "SequenceAssociation.txt";
	private static final String OUTPUT = "output.txt";

	// list to store edges
	private List<Edge> edgeList;
	// map to store k-core
	private int[] kCore;
	// map to store adjacency list
	private Map<Integer, ArrayList<Integer>> adjList;
	// map to store degree
	private int[] degrees;
	// vertex queue
	private PriorityQueue<Vertex> vertexQueue;

	// sets vertex
	private Set<String> setV;

	// convert vertex string to intId
	private Map<String, Integer> vStringToInt;

	// convert vertex intId to string
	private String[] vStringArray;

	private int numberOfVertexs;
	private int numberOfEdges;

	// main function
	public static void main(String[] args) throws Exception {
		int mb = 1024 * 1024;
		// Getting the runtime reference from system
		Runtime runtime = Runtime.getRuntime();
		System.out.println("##### Heap utilization statistics [MB] #####");

		Main main = new Main();
		main.init();
		main.readFile();
		main.loadData();
		main.compute();
		//main.writeFile();

		// Print used memory
		System.out.println("Used Memory:" + (runtime.totalMemory() - runtime.freeMemory()) / mb);
	}

	// initialize
	public void init() {
		edgeList = new ArrayList<Edge>();
		// kCore = new HashMap<String, Integer>();
		adjList = new HashMap<>();
		// degrees = new HashMap<String, Integer>();
		vertexQueue = new PriorityQueue<Vertex>();

		setV = new HashSet<>();
		vStringToInt = new HashMap<>();
	}

	// read input.txt and convert edge list to adjacency list
	public void readFile() {

		Path path = Paths.get(INPUT);

		try (Stream<String> lines = Files.lines(path)) {
			Spliterator<String> lineSpliterator = lines.spliterator();
			Spliterator<Edge> edgeSpliterator = new EdgeSpliterator(lineSpliterator);

			Stream<Edge> edgeStream = StreamSupport.stream(edgeSpliterator, false);
			edgeStream.forEach(edge -> edgeList.add(edge));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	// load data
	public void loadData() {
		for (Edge edge : edgeList) {
			setV.add(edge.getStartNode());
			setV.add(edge.getEndNode());
		}

		numberOfEdges = edgeList.size();
		encryptVertex();

		for (Edge edge : edgeList) {
			pushMap(adjList, edge.getStartNode(), edge.getEndNode());
			pushMap(adjList, edge.getEndNode(), edge.getStartNode());
		}

		degrees = new int[numberOfVertexs];
		for (Map.Entry<Integer, ArrayList<Integer>> entry : adjList.entrySet()) {
			degrees[entry.getKey()] = entry.getValue().size();

			vertexQueue.add(new Vertex(vStringArray[entry.getKey()], entry.getValue().size()));
		}
	}

	// write result to output.txt
	public void writeFile() throws Exception {

		Path path = Paths.get(OUTPUT);
		List<String> lines = new ArrayList<>();

		Map<String, Integer> kCoreResult = new HashMap<>();

		for (int i = 0; i < kCore.length; i++) {
			kCoreResult.put(vStringArray[i], kCore[i]);
		}

		// sort map by value
		Map<String, Integer> sortedMap = MapComparator.sortByValue(kCoreResult);

		for (Map.Entry<String, Integer> entry : sortedMap.entrySet()) {
			lines.add(String.format("%s\t%d", entry.getKey(), entry.getValue()));
		}

		Files.write(path, lines);

		writeXLSFile(sortedMap);
	}

	// push value to map
	public void pushMap(Map<Integer, ArrayList<Integer>> adjList, String start, String end) {
		if (!adjList.containsKey(vStringToInt.get(start))) {
			adjList.put(Integer.valueOf(vStringToInt.get(start)), new ArrayList<Integer>());
		}
		adjList.get(vStringToInt.get(start)).add(vStringToInt.get(end));
	}

	public void encryptVertex() {
		int id = 0;
		int length = setV.size();
		numberOfVertexs = length;
		kCore = new int[length];
		vStringArray = new String[length];
		for (String s : setV) {
			vStringToInt.put(s, id);
			vStringArray[id] = s;
			id++;
		}
		setV.clear();
	}

	// compute
	public void compute() {
		int k = 0;
		// BFS traverse
		while (vertexQueue.size() != 0) {
			Vertex current = vertexQueue.poll();
			String currentVertex = current.getVertex();
			if (degrees[vStringToInt.get(currentVertex)] < current.getDegree()) {
				continue;
			}

			k = Math.max(k, degrees[vStringToInt.get(currentVertex)]);

			kCore[vStringToInt.get(currentVertex)] = k;

			// sequentially
			// System.out.println("1: " +
			// adjList.get(vStringToInt.get(currentVertex)).size());
			// for (Integer vertex :
			// adjList.get(vStringToInt.get(currentVertex))) {
			//
			// if (kCore[vertex] == 0) {
			// degrees[vertex] = degrees[vertex] - 1;
			// vertexQueue.add(new Vertex(vStringArray[vertex],
			// degrees[vertex]));
			// }
			//
			// }

			//////

			int adjListV[] = convertIntegers(adjList.get(vStringToInt.get(currentVertex)));
			Range range = Range.create(adjListV.length);
			KCoreKernel kCoreKernel = new KCoreKernel(range);
			kCoreKernel.setAdjListV(adjListV);
			kCoreKernel.setkCore(kCore);
			kCoreKernel.setDegrees(degrees);

			kCoreKernel.execute(range);

			degrees = kCoreKernel.getDegrees();
			int result[] = kCoreKernel.getResult();
			kCoreKernel.dispose();

			Arrays.stream(result).forEach(x -> {
				vertexQueue.add(new Vertex(vStringArray[x], degrees[x]));
			});

			////
		}
		System.out.println("K-Core: " + k);
	}

	public int[] convertIntegers(List<Integer> integers) {
		int[] ret = new int[integers.size()];
		for (int i = 0; i < ret.length; i++) {
			ret[i] = integers.get(i).intValue();
		}
		return ret;
	}

	public void writeXLSFile(Map<String, Integer> result) throws IOException {

		// name of excel file
		String excelFileName = "result.xls";

		// name of sheet
		String sheetName = "Sheet1";

		HSSFWorkbook wb = new HSSFWorkbook();
		HSSFSheet sheet = wb.createSheet(sheetName);
		HSSFRow row;
		HSSFCell cell;

		// header
		row = sheet.createRow(0);
		cell = row.createCell(0);
		cell.setCellValue("Node");
		cell = row.createCell(1);
		cell.setCellValue("Rank");

		int index = 1;
		for (Map.Entry<String, Integer> entry : result.entrySet()) {
			row = sheet.createRow(index++);

			cell = row.createCell(0);
			cell.setCellValue(String.format("%s", entry.getKey()));

			cell = row.createCell(1);
			cell.setCellValue(String.format("%d", entry.getValue()));
		}

		FileOutputStream fileOut = new FileOutputStream(excelFileName);

		// write this workbook to an Outputstream.
		wb.write(fileOut);
		fileOut.flush();
		fileOut.close();
	}
}
