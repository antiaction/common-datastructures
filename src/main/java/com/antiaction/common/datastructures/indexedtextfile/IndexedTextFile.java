package com.antiaction.common.datastructures.indexedtextfile;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.List;

import com.antiaction.common.datastructures.CloseUtils;

/**
 * Paged text reader using a separate line feed index file.
 * The index file is just a file of long index values pointing in to the text file
 *
 * @author nicl
 */
public class IndexedTextFile implements Pageable, Closeable {

	/** Text file. */
	public File textFile;

	/** Text random access file. */
	public RandomAccessFile textRaf;

	/** Index file. */
	public File idxFile;

	/** Search result index random access file. */
	public RandomAccessFile idxRaf;

	/** Last position in the original text file which has been searched. */
	public long lastIndexedTextPosition;

	/** Indexed text lines. */
	public long indexedTextLines;

	/**
	 * Creates a line indexed text file for pagination.
	 * @param dir location to store search result files
	 * @param baseFilename base filename to use when creating the text and index files
	 * @throws IOException if an I/O exception occurs while creating cache files
	 */
	public IndexedTextFile(File dir, String baseFilename) throws IOException {
		textFile = new File(dir, baseFilename + ".log");
		idxFile = new File(dir, baseFilename + ".idx");
	}

	/**
	 * Initialise text and index files.
	 * @throws IOException if an I/O exception occurs while initialising text and index files
	 */
	public synchronized void init() throws IOException {
		textRaf = new RandomAccessFile(textFile, "rw");
		idxRaf = new RandomAccessFile(idxFile, "rw");
		if (idxRaf.length() == 0) {
			idxRaf.writeLong(0);
			lastIndexedTextPosition = 0;
			indexedTextLines = 0;
		}
		else {
			idxRaf.seek(idxRaf.length() - 8);
			lastIndexedTextPosition = idxRaf.readLong();
			indexedTextLines = (idxRaf.length() / 8) - 1;
		}
		idxRaf.seek(idxRaf.length());
		textRaf.seek(textRaf.length());
	}

	@Override
	public void close() {
		indexedTextLines = 0;
		CloseUtils.closeQuietly(textRaf);
		CloseUtils.closeQuietly(idxRaf);
	}

	/**
	 * Add the used files for this object to the supplied list.
	 * @param oldFilesList list of <code>File</code> objects
	 */
	public void addFilesToOldFilesList(List<File> oldFilesList) {
		oldFilesList.add(textFile);
		oldFilesList.add(idxFile);
	}

	@Override
	public long getTextFilesize() {
		return textFile.length();
	}

	@Override
	public long getIndexFilesize() {
		return idxFile.length();
	}

	@Override
	public long getLastIndexedTextPosition() {
		return lastIndexedTextPosition;
	}

	@Override
	public long getIndexedTextLines() {
		return indexedTextLines;
	}

	@Override
	public synchronized byte[] readPage(long page, long itemsPerPage, boolean descending) throws IOException {
		return IndexedTextFile.readPage(idxRaf, textRaf, page, itemsPerPage, descending);
	}

	/**
	 * Add lines to the text file and positions to the index file.
	 * @param in input stream with bytes to append text file
	 * @param tmpBuf byte buffer used to transfer bytes from input stream to text file
	 * @param to counter to increment with the amount of bytes appended
	 * @return the to value incremented with the bytes appended to the text file
	 * @throws IOException if an I/O exception occurs while append bytes and indexing text 
	 */
	public synchronized long write(InputStream in, byte[] tmpBuf, long to) throws IOException {
		int read;
		int idx;
		long pos = textRaf.length();
		textRaf.seek(pos);
		idxRaf.seek(idxRaf.length());
		while ((read = in.read(tmpBuf)) != -1) {
			textRaf.write(tmpBuf, 0, read);
			to += read;
			idx = 0;
			while (read > 0) {
				++pos;
				--read;
				if (tmpBuf[idx++] == '\n') {
					idxRaf.writeLong(pos);
					lastIndexedTextPosition = pos;
					++indexedTextLines;
				}
			}
		}
		return to;
	}

	/**
	 * Uses an index file to read a page from a text file.
	 * @param idxRaf index file with pointers to all the lines in the text file
	 * @param textRaf indexed text file 
	 * @param page page to return
	 * @param itemsPerPage item per page
	 * @param descending start from the beginning or end of the index/text file
	 * @return a byte array containing a page of lines as read based on the index, page and items per page
	 * @throws IOException if an I/O exception occurs while reading a page
	 */
	public static byte[] readPage(RandomAccessFile idxRaf, RandomAccessFile textRaf, long page, long itemsPerPage, boolean descending) throws IOException {
		byte[] bytes = null;;
		if (page < 1) {
			throw new IllegalArgumentException();
		}
		if (itemsPerPage < 25) {
			throw new IllegalArgumentException();
		}
		long length = idxRaf.length();
		if (length > 8) {
			if (!descending) {
				// Forwards.
				long fromIdx = (page - 1) * (itemsPerPage * 8);
				long toIdx = fromIdx + (itemsPerPage * 8);
				if (toIdx > length) {
					toIdx = length;
				}
				idxRaf.seek(fromIdx);
				fromIdx = idxRaf.readLong();
				idxRaf.seek(toIdx);
				toIdx = idxRaf.readLong();
				textRaf.seek(fromIdx);
				bytes = new byte[(int)(toIdx - fromIdx)];
				textRaf.readFully(bytes, 0, (int)(toIdx - fromIdx));
			}
			else {
				// Backwards.
				long toIdx = length - ((page - 1) * itemsPerPage * 8);
				long fromIdx = toIdx - (itemsPerPage * 8) - 8;
				if (fromIdx < 0) {
					fromIdx = 0;
				}
				// Read line indexes for page.
				int pageIdxArrLen = (int)(toIdx - fromIdx);
				byte[] pageIdxArr = new byte[pageIdxArrLen];
				idxRaf.seek(fromIdx);
				int pos = 0;
				int limit = pageIdxArrLen;
				int read = 0;
				while (limit > 0 && read != -1) {
					read = idxRaf.read(pageIdxArr, pos, limit);
					if (read != -1) {
						pos += read;
						limit -= read;
					}
				}
				// Convert line indexes for page.
				limit = pos;
				pos = 0;
				long[] idxArr = new long[limit / 8];
				long l;
				int dstIdx = 0;
				while (pos < limit) {
					l = (pageIdxArr[pos++] & 255) << 56 | (pageIdxArr[pos++] & 255) << 48 | (pageIdxArr[pos++] & 255) << 40 | (pageIdxArr[pos++] & 255) << 32
							| (pageIdxArr[pos++] & 255) << 24 | (pageIdxArr[pos++] & 255) << 16 | (pageIdxArr[pos++] & 255) << 8 | (pageIdxArr[pos++] & 255);
					idxArr[dstIdx++] = l;
				}
				// Load the text lines for page.
				pos = 0;
				limit /= 8;
				fromIdx = idxArr[pos];
				toIdx = idxArr[limit - 1];
				textRaf.seek(fromIdx);
				byte[] tmpBytes = new byte[(int)(toIdx - fromIdx)];
				textRaf.readFully(tmpBytes, 0, (int)(toIdx - fromIdx));
				// Reverse text lines for page.
				bytes = new byte[tmpBytes.length];
				long base = idxArr[pos++];
				fromIdx = base;
				int len;
				dstIdx = bytes.length;
				while (pos < limit) {
					toIdx = idxArr[pos++];
					len = (int)(toIdx - fromIdx);
					dstIdx -= len;
					System.arraycopy(tmpBytes, (int)(fromIdx - base), bytes, dstIdx, len);
					fromIdx = toIdx;
				}
			}
		}
		return bytes;
	}

}
