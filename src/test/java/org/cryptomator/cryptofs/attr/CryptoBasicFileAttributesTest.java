/*******************************************************************************
 * Copyright (c) 2016 Sebastian Stenzel and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the accompanying LICENSE.txt.
 *
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *******************************************************************************/
package org.cryptomator.cryptofs.attr;

import org.cryptomator.cryptolib.api.Cryptor;
import org.cryptomator.cryptolib.api.FileContentCryptor;
import org.cryptomator.cryptolib.api.FileHeaderCryptor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;

import static org.cryptomator.cryptofs.common.CiphertextFileType.DIRECTORY;
import static org.cryptomator.cryptofs.common.CiphertextFileType.FILE;
import static org.cryptomator.cryptofs.common.CiphertextFileType.SYMLINK;

public class CryptoBasicFileAttributesTest {

	private Cryptor cryptor;
	private Path ciphertextFilePath;
	private BasicFileAttributes delegateAttr;

	@BeforeEach
	public void setup() {
		cryptor = Mockito.mock(Cryptor.class);
		FileHeaderCryptor headerCryptor = Mockito.mock(FileHeaderCryptor.class);
		FileContentCryptor contentCryptor = Mockito.mock(FileContentCryptor.class);
		Mockito.when(cryptor.fileHeaderCryptor()).thenReturn(headerCryptor);
		Mockito.when(headerCryptor.headerSize()).thenReturn(88);
		Mockito.when(cryptor.fileContentCryptor()).thenReturn(contentCryptor);
		Mockito.when(contentCryptor.cleartextChunkSize()).thenReturn(32 * 1024);
		Mockito.when(contentCryptor.ciphertextChunkSize()).thenReturn(16 + 32 * 1024 + 32);
		Mockito.doCallRealMethod().when(contentCryptor).cleartextSize(Mockito.anyLong());
		ciphertextFilePath = Mockito.mock(Path.class, "ciphertextFile");
		delegateAttr = Mockito.mock(BasicFileAttributes.class);

		Mockito.when(delegateAttr.size()).thenReturn(88l + 16 + 1337 + 32);
	}

	@Test
	public void testIsDirectory() {
		BasicFileAttributes attr = new CryptoBasicFileAttributes(delegateAttr, DIRECTORY, ciphertextFilePath, cryptor, Optional.empty());
		Assertions.assertFalse(attr.isRegularFile());
		Assertions.assertTrue(attr.isDirectory());
		Assertions.assertFalse(attr.isSymbolicLink());
		Assertions.assertFalse(attr.isOther());
	}

	@Test
	public void testIsRegularFile() {
		BasicFileAttributes attr = new CryptoBasicFileAttributes(delegateAttr, FILE, ciphertextFilePath, cryptor, Optional.empty());
		Assertions.assertTrue(attr.isRegularFile());
		Assertions.assertFalse(attr.isDirectory());
		Assertions.assertFalse(attr.isSymbolicLink());
		Assertions.assertFalse(attr.isOther());
	}

	@Test
	public void testIsSymbolicLink() {
		BasicFileAttributes attr = new CryptoBasicFileAttributes(delegateAttr, SYMLINK, ciphertextFilePath, cryptor, Optional.empty());
		Assertions.assertFalse(attr.isRegularFile());
		Assertions.assertFalse(attr.isDirectory());
		Assertions.assertTrue(attr.isSymbolicLink());
		Assertions.assertFalse(attr.isOther());
	}

	@Test
	public void testSizeOfFile() {
		BasicFileAttributes attr = new CryptoBasicFileAttributes(delegateAttr, FILE, ciphertextFilePath, cryptor, Optional.empty());
		Assertions.assertEquals(1337l, attr.size());
	}

	@Test
	public void testSizeOfDirectory() {
		Mockito.when(delegateAttr.size()).thenReturn(4096l);
		BasicFileAttributes attr = new CryptoBasicFileAttributes(delegateAttr, DIRECTORY, ciphertextFilePath, cryptor, Optional.empty());
		Assertions.assertEquals(4096l, attr.size());
	}

	@Test
	public void testSizeOfSymlink() {
		BasicFileAttributes attr = new CryptoBasicFileAttributes(delegateAttr, SYMLINK, ciphertextFilePath, cryptor, Optional.empty());
		Assertions.assertEquals(1337l, attr.size());
	}

	@Test
	public void testSizeSetToZeroIfCryptoHeaderToSmall() {
		Mockito.when(delegateAttr.size()).thenReturn(88l + 20l);
		BasicFileAttributes attr = new CryptoBasicFileAttributes(delegateAttr, FILE, ciphertextFilePath, cryptor, Optional.empty());
		Assertions.assertEquals(0, attr.size());
	}

}
