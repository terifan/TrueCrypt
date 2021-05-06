package org.terifan.filesystem.ntfs;


// Allow one to retrieve only needed information to reduce memory footprint.
public enum RetrieveMode
{
	// Includes the name, size, attributes and hierarchical information only.
	Minimal,
	// Retrieve the lastModified, lastAccessed and creationTime.
	StandardInformations,
	// Retrieve file's streams information.
	Streams,
	// Retrieve file's fragments information.
	Fragments,
	// Retrieve all information available.
	All
}
