package org.crossminer.similaritycalculator.RepoPal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.math3.linear.OpenMapRealVector;
import org.apache.commons.math3.linear.RealVectorFormat;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.util.BytesRef;

public class TextSimilarity {
	public static final String FIELD_CONTENT = "contents";
	
	private String luceneIndex;
	
	public TextSimilarity(String ludeneIndex) {
		this.luceneIndex = ludeneIndex;
	}

	private void createIndex(List<String> prjs) throws LockObtainFailedException, IOException {

		File indexDirectory = new File(luceneIndex);
		org.apache.lucene.store.Directory dir = FSDirectory.open(Paths.get(indexDirectory.getAbsolutePath()));
		Analyzer analyzer = new EnglishAnalyzer(StandardAnalyzer.STOP_WORDS_SET); // using
																					// stop
																					// words
		
		IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

		if (indexDirectory.exists()) {
			iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
		} else {
			// Add new documents to an existing index:
			iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
		}

		IndexWriter writer = new IndexWriter(dir, iwc);
		for (String project : prjs) {
			Document doc = new Document();
			FieldType fieldType = new FieldType();

			fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
			fieldType.setStored(true);
			fieldType.setStoreTermVectors(true);
			fieldType.setTokenized(true);
			Field contentField = new Field(FIELD_CONTENT, project, fieldType);
			doc.add(contentField);
			writer.addDocument(doc);
		}

		writer.close();
	}
	

	private IndexReader getIndexReader() throws IOException {
		return DirectoryReader.open(FSDirectory.open(Paths.get(new File(luceneIndex).getAbsolutePath())));

	}

	/**
	 * Returns the total number of documents in the index
	 * 
	 * @return
	 * @throws IOException
	 */
	private int getTotalDocumentInIndex() throws IOException {
		Integer maxDoc = getIndexReader().maxDoc();
		getIndexReader().close();
		return maxDoc;
	}

	private HashMap<String, Integer> getAllTerms() throws IOException {
		HashMap<String, Integer> allTerms = new HashMap<>();
		int pos = 0;
		for (int docId = 0; docId < getTotalDocumentInIndex(); docId++) {
			Terms vector = getIndexReader().getTermVector(docId, FIELD_CONTENT);
			TermsEnum termsEnum = null;
			termsEnum = vector.iterator();
			BytesRef text = null;
			while ((text = termsEnum.next()) != null) {
				String term = text.utf8ToString();
				allTerms.put(term, pos++);
			}
		}

		// Update postition
		pos = 0;
		for (Entry<String, Integer> s : allTerms.entrySet()) {
			s.setValue(pos++);
		}
		return allTerms;
	}

	private DocVector[] getDocumentVectors() throws IOException {
		DocVector[] docVector = new DocVector[getTotalDocumentInIndex()];
		for (int docId = 0; docId < getTotalDocumentInIndex(); docId++) {
			Terms vector = getIndexReader().getTermVector(docId, FIELD_CONTENT);
			TermsEnum termsEnum = null;
			termsEnum = vector.iterator();
			BytesRef text = null;
			docVector[docId] = new DocVector(getAllTerms());
			while ((text = termsEnum.next()) != null) {
				String term = text.utf8ToString();
				int freq = (int) termsEnum.totalTermFreq();
				docVector[docId].setEntry(term, freq);
			}
			docVector[docId].normalize();
		}
		getIndexReader().close();
		return docVector;
	}

	@SuppressWarnings("deprecation")
	private double cosineSimilarity(DocVector d1, DocVector d2) {
		double cosinesimilarity;
		try {
			
			cosinesimilarity = (d1.getVector().dotProduct(d2.getVector()))
					/ (d1.getVector().getNorm() * d2.getVector().getNorm());
		} catch (Exception e) {
			return 0.0;
		}
		return cosinesimilarity;
	}

	/**
	 * Method to create Lucene Index Keep in mind that always index text value to
	 * Lucene for calculating Cosine Similarity. You have to generate tokens, terms
	 * and their frequencies and store them in the Lucene Index.
	 * 
	 * @throws CorruptIndexException
	 * @throws LockObtainFailedException
	 * @throws IOException
	 */

	public double calculateSimilarity(String prj1, String prj2) {
		try {
			String[] prjs = new String[] { prj1, prj2 };
			createIndex(Arrays.asList(prjs));
			getAllTerms();
			DocVector[] docVector = getDocumentVectors(); // getting
			return cosineSimilarity(docVector[0], docVector[1]);
		} catch (IOException e) {
			System.out.println(e.getMessage());
		}
		return 0;

	}

}

@SuppressWarnings("deprecation")
class DocVector {

	@SuppressWarnings("rawtypes")
	public Map terms;
	private OpenMapRealVector vector;

	public OpenMapRealVector getVector() {
		return vector;
	}

	public void setVector(OpenMapRealVector vector) {
		this.vector = vector;
	}

	@SuppressWarnings("rawtypes")
	public DocVector(Map terms) {
		this.terms = terms;
		this.vector = new OpenMapRealVector(terms.size());
	}

	public void setEntry(String term, int freq) {
		if (terms.containsKey(term)) {
			int pos = (int) terms.get(term);
			vector.setEntry(pos, (double) freq);
		}
	}

	public void normalize() {
		double sum = vector.getL1Norm();
		vector = (OpenMapRealVector) vector.mapDivide(sum);
	}

	@Override
	public String toString() {
		RealVectorFormat formatter = new RealVectorFormat();
		return formatter.format(vector);
	}
}
