import java.io.FileReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeMap;

public class SentenceGenerator {
	private static final String SCRIPT_PATH = "./script.txt";
	private static final String OUTPUT_PATH = "./output.txt";
	private static final String BIGRAM_PATH = "./bigram.txt";
	private static final int SENTENCE_LENGTH = 100;

	public static void main(String[] args) {
		HashMap<String, Integer> c1 = new HashMap<String, Integer>();
		HashMap<String, Integer> c2 = new HashMap<String, Integer>();
		HashMap<String, Integer> c3 = new HashMap<String, Integer>();
		HashMap<String, Double> pX = new HashMap<String, Double>();
		HashMap<String, Double> pYX = new HashMap<String, Double>();
		HashMap<String, Double> pZXY = new HashMap<String, Double>();
		HashMap<String, TreeMap<Double, String>> uniCDF = new HashMap<String, TreeMap<Double, String>>();
		HashMap<String, TreeMap<Double, String>> biCDF = new HashMap<String, TreeMap<Double, String>>();
		HashMap<String, TreeMap<Double, String>> triCDF = new HashMap<String, TreeMap<Double, String>>();

		try {
			System.out.println("reading script");
			char[] script = readScript(SCRIPT_PATH);
			System.out.println("counting unigrams");
			countNGrams(1, c1, script);
			System.out.println("counting bigrams");
			countNGrams(2, c2, script);
			System.out.println("counting trigrams");
			countNGrams(3, c3, script);
			System.out.println("calculating unigram transitional probabilities");
			calcUnigramP(c1, pX, script.length);
			System.out.println("calculating bigram transitional probabilities");
			calcMultigramP(2, c2, c1, pYX);
			System.out.println("calculating trigram transitional probabilities");
			calcMultigramP(3, c3, c2, pZXY);
			System.out.println("calculating unigram cdf");
			calcCDF(1, pX, uniCDF);
			System.out.println("calculating bigram cdf");
			calcCDF(2, pYX, biCDF);
			System.out.println("calculating trigram cdf");
			calcCDF(3, pZXY, triCDF);

			// generate outputs
			System.out.println("generating sentences");
			// first generate sentences
			BufferedWriter bw_output = new BufferedWriter(new FileWriter(OUTPUT_PATH));
			for (char c = 'a'; c <= 'z'; c++) {
				for (int i = 0; i < 10; i++) {
					bw_output.write(generateSentence(c, SENTENCE_LENGTH, pYX, pZXY, biCDF, triCDF));
					bw_output.newLine();
				}
			}
			bw_output.close();

			// generate bigram transition matrix
			System.out.println("writing bigram transition matrix");
			BufferedWriter bw_bigram = new BufferedWriter(new FileWriter(BIGRAM_PATH));
			for (char x = 'a'; x <= 'z'; x++) {
				for (char y = 'a'; y <= 'z'; y++) {
					bw_bigram.write("" + pYX.get("" + x + y));
					bw_bigram.write(",");
				}
				bw_bigram.write("" + pYX.get("" + x + " "));
				bw_bigram.newLine();
			}
			for (char y = 'a'; y <= 'z'; y++) {
				bw_bigram.write("" + pYX.get(" " + y));
				bw_bigram.write(",");
			}
			bw_bigram.write("" + pYX.get("  "));
			bw_bigram.close();
			System.out.println("done");

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public static char[] readScript(String path) throws FileNotFoundException, IOException {
		char[] script = new char[200000];
		try (FileReader fr = new FileReader(path)) {
			fr.read(script);
		}
		int j = 0;
		for (int i = 0; i < script.length; i++) {
			// convert to lowercase
			if (script[i] >= 'A' && script[i] <= 'Z') {
				script[j] = (char) (script[i] + 32);
				j++;
			} else if (script[i] >= 'a' && script[i] <= 'z') {
				script[j] = script[i];
				j++;
			} else if (j > 0 && script[j - 1] != ' ' && script[i] == ' ') {
				script[j] = script[i];
				j++;
			}
		}
		char[] scriptOut = new char[j];
		for (int i = 0; i < j; i++) {
			scriptOut[i] = script[i];
		}
		return scriptOut;
	}

	public static void countNGrams(int n, HashMap<String, Integer> count, char[] script) {
		// add all possible keys
		ArrayList<String> keys = new ArrayList<String>();
		nGramKeyList(n, "", keys);
		for (int i = 0; i < keys.size(); i++)
			if (!count.containsKey(keys.get(i)))
				count.put(keys.get(i), 0);

		// count n-grams and insert
		for (int i = 0; i <= script.length - n; i++) {
			char[] tmp = new char[n];
			for (int j = 0; j < n; j++)
				tmp[j] = script[i + j];
			String tmp2 = new String(tmp);
			count.put(tmp2, count.get(tmp2) + 1);
		}

	}

	// N = number characters in script total
	public static void calcUnigramP(HashMap<String, Integer> count, HashMap<String, Double> p, int N) {
		for (String k : count.keySet())
			p.put(k, (count.get(k) + 1.) / (27. + N));
	}

	public static void calcMultigramP(int n, Map<String, Integer> countN, Map<String, Integer> countNminus1,
			Map<String, Double> p) {
		for (String k : countN.keySet())
			p.put(k, (countN.get(k) + 1.) / (27. + countNminus1.get(k.substring(0, n - 1))));
	}

	// generate list of possible keys length n for adding 0s to hashmap

	private static void nGramKeyList(int n, String pref, ArrayList<String> keys) {
		if (n == 0) {
			keys.add(pref);
			return;
		}
		for (char c = 'a'; c <= 'z'; c++) {
			String newPref = pref + c;
			nGramKeyList(n - 1, newPref, keys);
		}
		String newPref = pref + ' ';
		nGramKeyList(n - 1, newPref, keys);
	}

	public static void calcCDF(int n, HashMap<String, Double> p, HashMap<String, TreeMap<Double, String>> cdf) {
		// update the cumulative probability for each (n-1)-gram as they occur, and put
		// current value into TreeMap
		HashMap<String, Double> cumulative = new HashMap<String, Double>();
		Iterator<String> iter = p.keySet().iterator();
		for (String k = iter.next(); iter.hasNext(); k = iter.next()) {
			String sub = k.substring(0, n - 1);
			if (!cumulative.containsKey(sub))
				cumulative.put(sub, 0.);
			if (!cdf.containsKey(sub))
				cdf.put(sub, new TreeMap<Double, String>());
			cdf.get(sub).put(cumulative.get(sub), k);
			cumulative.put(sub, cumulative.get(sub) + p.get(k));
		}
	}

	public static String generateNGram(String sub, HashMap<String, Double> p,
			HashMap<String, TreeMap<Double, String>> cdf) {
		Random rng = new Random();
		Double u = rng.nextDouble();
		Entry<Double, String> e = cdf.get(sub).floorEntry(u);
		return e.getValue();
	}

	public static String generateSentence(char c, int N, HashMap<String, Double> pYX, HashMap<String, Double> pZXY,
			HashMap<String, TreeMap<Double, String>> cdfBi, HashMap<String, TreeMap<Double, String>> cdfTri) {
		String sentence = String.valueOf(c);
		if (N == 1)
			return sentence; //unigram
		sentence = generateNGram(sentence, pYX, cdfBi);
		if (N == 2)
			return sentence; //bigram
		sentence = generateNGram(sentence, pZXY, cdfTri);
		if (N == 3)
			return sentence; //trigram
		for (int i = 3; i < N; i++) {
			sentence += generateNGram(sentence.substring(i - 2, i), pZXY, cdfTri).charAt(2);
		}
		return sentence; //trigram-appended trigram
	}
}
