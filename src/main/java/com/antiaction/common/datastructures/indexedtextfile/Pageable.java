package com.antiaction.common.datastructures.indexedtextfile;

import java.io.IOException;

/**
 * Interface for paginating text files that have line indexing.
 *
 * @author nicl
 */
public interface Pageable {

	/**
	 * Returns the size of the text file.
	 * @return the size of the text file
	 */
	public long getTextFilesize();

	/**
	 * Returns the size of the index file.
	 * @return the size of the index file
	 */
	public long getIndexFilesize();

	/**
	 * Returns the position of the last indexed line feed.
	 * @return the position of the last indexed line feed
	 */
	public long getLastIndexedTextPosition();

	/**
	 * Returns the number of indexed text lines.
	 * @return the number of indexed text lines
	 */
	public long getIndexedTextLines();

	/**
	 * Use the index to return a byte array containing the requested page.
	 * @param page page to load
	 * @param itemsPerPage items per page used to locate the first and last line of a requested page
	 * @param descending descending or ascending
	 * @return byte array containing the lines of the requested page
	 * @throws IOException if an I/O exception occurs while reading a page from the index and text file
	 */
	public byte[] readPage(long page, long itemsPerPage, boolean descending) throws IOException;

}
