package org.terifan.filesystem.ntfs;


// Allow one to retrieve only needed information to reduce memory footprint.
enum RetrieveMode
{
	// Includes the name, size, attributes and hierarchical information only.
	Minimal(1),
	// Retrieve the lastModified, lastAccessed and creationTime.
	StandardInformations(2),
	// Retrieve file's streams information.
	Streams(4),
	// Retrieve file's fragments information.
	Fragments(8),
	// Retrieve all information available.
	All(1 + 2 + 4 + 8);

	final int CODE;


	private RetrieveMode(int aCode)
	{
		CODE = aCode;
	}


	boolean isSet(int aMode)
	{
		return (aMode & CODE) == CODE;
	}
}
