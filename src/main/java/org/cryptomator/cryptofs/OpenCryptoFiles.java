/*******************************************************************************
 * Copyright (c) 2016 Sebastian Stenzel and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the accompanying LICENSE.txt.
 *
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *******************************************************************************/
package org.cryptomator.cryptofs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.cryptomator.cryptolib.api.Cryptor;

class OpenCryptoFiles {

	private final boolean readonly;

	private final ConcurrentMap<Path, OpenCryptoFile> openCryptoFiles = new ConcurrentHashMap<>();

	public OpenCryptoFiles(boolean readonly) {
		this.readonly = readonly;
	}

	public OpenCryptoFile get(Path path, Cryptor cryptor, EffectiveOpenOptions options) throws IOException {
		if (options.writable() && readonly) {
			throw new UnsupportedOperationException("read-only file system");
		}

		Path normalizedPath = path.toAbsolutePath().normalize();
		OpenCryptoFile.Builder builder = openCryptoFileBuilder(cryptor, normalizedPath, options);

		try {
			return openCryptoFiles.computeIfAbsent(normalizedPath, ignored -> IOExceptionWrapper.wrapIOExceptionOf(builder::build));
		} catch (IOExceptionWrapper e) {
			throw e.getCause();
		}
	}

	private OpenCryptoFile.Builder openCryptoFileBuilder(Cryptor cryptor, Path path, EffectiveOpenOptions options) {
		return OpenCryptoFile.anOpenCryptoFile() //
				.withPath(path) //
				.withOptions(options) //
				.onClosed(closed -> openCryptoFiles.remove(closed.path()));
	}

	private static class IOExceptionWrapper extends RuntimeException {

		public IOExceptionWrapper(Exception cause) {
			super(cause);
		}

		@Override
		public IOException getCause() {
			return (IOException) super.getCause();
		}

		public static <T> T wrapIOExceptionOf(SupplierThrowingException<T, IOException> supplier) {
			return supplier.wrapExceptionUsing(IOExceptionWrapper::new).get();
		}
	}

}
