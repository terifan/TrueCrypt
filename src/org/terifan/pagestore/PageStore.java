package org.terifan.pagestore;

import java.io.Closeable;
import java.io.IOException;


/**
 * A PageStore is a random access storage of pages. All pages are the same
 * size and accessed by their index in the PageStore.
 */
public interface PageStore extends Closeable
{
	/**
	 * Reads one or more pages from the PageStore.
	 *
	 * @param aPageIndex
	 *   the first page to read.
	 * @param aBuffer
	 *   the destination buffer. Length must be a multiple of the page size.
	 */
	void read(long aPageIndex, byte [] aBuffer) throws IOException;


	/**
	 * Reads one or more pages from the PageStore.
	 *
	 * @param aPageIndex
	 *   the first page to read.
	 * @param aBuffer
	 *   the destination buffer.
	 * @param aOffset
	 *   the start offset in the destination buffer.
	 * @param aLength
	 *   number of bytes to write. Must be a multiple of the page size.
	 */
	void read(long aPageIndex, byte [] aBuffer, int aOffset, int aLength) throws IOException;


	/**
	 * Writes one or more pages to the PageStore.
	 *
	 * @param aPageIndex
	 *   the start page index.
	 * @param aBuffer
	 *   the content to be written. Length must be a multiple of the page size.
	 */
	void write(long aPageIndex, byte [] aBuffer) throws IOException;


	/**
	 * Writes one or more pages to the PageStore.
	 *
	 * @param aPageIndex
	 *   the start page index.
	 * @param aBuffer
	 *   the content to be written
	 * @param aOffset
	 *   the start offset in the buffer provided.
	 * @param aLength
	 *   number of bytes to be written. Must be a multiple of the page size.
	 */
	void write(long aPageIndex, byte [] aBuffer, int aOffset, int aLength) throws IOException;


	/**
	 * Returns number of pages in this PageStore.
	 *
	 * @return
	 *   number of pages
	 */
	long getPageCount() throws IOException;


	/**
	 * Gets the size of a page.
	 *
	 * @return
	 *   the size of a page.
	 */
	int getPageSize() throws IOException;


	/**
	 * Close the PageStore and clears all internal data. A PageStore must be
	 * explicitly closed.
	 */
	@Override
	void close() throws IOException;


	/**
	 * Optional flushing operation that may write unwritten data to disk.
	 */
	void flush() throws IOException;


	void resize(long aPageCount) throws IOException;
}