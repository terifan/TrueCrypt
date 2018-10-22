package org.terifan.truecrypt;


interface Cipher
{
	void engineInit(SecretKey aSecretKey);


	/**
	 * Encrypts a single block of ciphertext in ECB-mode.
	 *
	 * @param in
	 *    A buffer containing the plaintext to be encrypted.
	 * @param inOffset
	 *    Index in the in buffer where plaintext should be read.
	 * @param out
	 *    A buffer where ciphertext is written.
	 * @param outOffset
	 *    Index in the out buffer where ciphertext should be written.
	 */
	void engineEncryptBlock(byte [] in, int inOffset, byte [] out, int outOffset);


	/**
	 * Decrypts a single block of ciphertext in ECB-mode.
	 *
	 * @param in
	 *    A buffer containing the ciphertext to be decrypted.
	 * @param inOffset
	 *    Index in the in buffer where ciphertext should be read.
	 * @param out
	 *    A buffer where plaintext is written.
	 * @param outOffset
	 *    Index in the out buffer where plaintext should be written.
	 */
	void engineDecryptBlock(byte [] in, int inOffset, byte [] out, int outOffset);


	/**
	 * Encrypts a single block of ciphertext in ECB-mode.
	 *
	 * @param in
	 *    A buffer containing the plaintext to be encrypted.
	 * @param inOffset
	 *    Index in the in buffer where plaintext should be read.
	 * @param out
	 *    A buffer where ciphertext is written.
	 * @param outOffset
	 *    Index in the out buffer where ciphertext should be written.
	 */
	void engineEncryptBlock(int [] in, int inOffset, int [] out, int outOffset);


	/**
	 * Decrypts a single block of ciphertext in ECB-mode.
	 *
	 * @param in
	 *    A buffer containing the ciphertext to be decrypted.
	 * @param inOffset
	 *    Index in the in buffer where ciphertext should be read.
	 * @param out
	 *    A buffer where plaintext is written.
	 * @param outOffset
	 *    Index in the out buffer where plaintext should be written.
	 */
	void engineDecryptBlock(int [] in, int inOffset, int [] out, int outOffset);


	/**
	 * Returns the block size in bytes.
	 */
	int engineGetBlockSize();


	/**
	 * Returns the key size in bytes.
	 */
	int engineGetKeySize();


	/**
	 * Resets all internal state data. This Cipher object needs to be
	 * reinitialized again before it can be used again.
	 */
	void engineReset();
}