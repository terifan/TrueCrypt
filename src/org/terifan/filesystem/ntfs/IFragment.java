package org.terifan.filesystem.ntfs;


// This is where the parts of the file are located on the volume.
public interface IFragment
{
	// Logical cluster number, location on disk.
	long lcn();


	// Virtual cluster number of next fragment.
	long nextVcn();
}
