/*******************************************************************************
 * Copyright (c) 2016 Sebastian Stenzel and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the accompanying LICENSE.txt.
 *
 * Contributors:
 *     Sebastian Stenzel - initial API and implementation
 *******************************************************************************/
package org.cryptomator.cryptofs;

import com.google.common.io.MoreFiles;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.cryptomator.cryptofs.ch.CleartextFileChannel;
import org.cryptomator.cryptolib.api.Masterkey;
import org.cryptomator.cryptolib.api.MasterkeyLoader;
import org.cryptomator.cryptolib.api.MasterkeyLoadingFailedException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.DosFileAttributeView;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;

import static java.nio.file.Files.readAllBytes;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.cryptomator.cryptofs.CryptoFileSystemProperties.cryptoFileSystemProperties;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


public class CryptoFileSystemProviderIntegrationTest {

	@Nested
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	public class WithLimitedPaths {

		private MasterkeyLoader keyLoader = Mockito.mock(MasterkeyLoader.class);
		private CryptoFileSystem fs;
		private Path shortFilePath;
		private Path shortSymlinkPath;
		private Path shortDirPath;

		@BeforeAll
		public void setup(@TempDir Path tmpDir) throws IOException, MasterkeyLoadingFailedException {
			Mockito.when(keyLoader.loadKey(Mockito.any())).thenAnswer(ignored -> new Masterkey(new byte[64]));
			CryptoFileSystemProperties properties = cryptoFileSystemProperties() //
					.withFlags() //
					.withMasterkeyFilename("masterkey.cryptomator") //
					.withKeyLoader(keyLoader) //
					.withMaxCleartextNameLength(50) //
					.build();
			CryptoFileSystemProvider.initialize(tmpDir, properties, URI.create("test:key"));
			fs = CryptoFileSystemProvider.newFileSystem(tmpDir, properties);
		}

		@BeforeEach
		public void setupEach() throws IOException {
			shortFilePath = fs.getPath("/short-enough.txt");
			shortDirPath = fs.getPath("/short-enough-dir");
			shortSymlinkPath = fs.getPath("/symlink.txt");
			Files.createFile(shortFilePath);
			Files.createDirectory(shortDirPath);
			Files.createSymbolicLink(shortSymlinkPath, shortFilePath);
		}

		@AfterEach
		public void tearDownEach() throws IOException {
			Files.deleteIfExists(shortFilePath);
			Files.deleteIfExists(shortDirPath);
			Files.deleteIfExists(shortSymlinkPath);
		}

		@DisplayName("expect create file to fail with FileNameTooLongException")
		@Test
		public void testCreateFileExceedingPathLengthLimit() {
			Path p = fs.getPath("/this-cleartext-filename-is-longer-than-50-characters");
			assertThrows(FileNameTooLongException.class, () -> {
				Files.createFile(p);
			});
		}

		@DisplayName("expect create directory to fail with FileNameTooLongException")
		@Test
		public void testCreateDirExceedingPathLengthLimit() {
			Path p = fs.getPath("/this-cleartext-filename-is-longer-than-50-characters");
			assertThrows(FileNameTooLongException.class, () -> {
				Files.createDirectory(p);
			});
		}

		@DisplayName("expect create symlink to fail with FileNameTooLongException")
		@Test
		public void testCreateSymlinkExceedingPathLengthLimit() {
			Path p = fs.getPath("/this-cleartext-filename-is-longer-than-50-characters");
			assertThrows(FileNameTooLongException.class, () -> {
				Files.createSymbolicLink(p, shortFilePath);
			});
		}

		@DisplayName("expect move to fail with FileNameTooLongException")
		@ParameterizedTest(name = "move {0} -> this-cleartext-filename-is-longer-than-50-characters")
		@ValueSource(strings = {"/short-enough.txt", "/short-enough-dir", "/symlink.txt"})
		public void testMoveExceedingPathLengthLimit(String path) {
			Path src = fs.getPath(path);
			Path dst = fs.getPath("/this-cleartext-filename-is-longer-than-50-characters");
			assertThrows(FileNameTooLongException.class, () -> {
				Files.move(src, dst);
			});
			assertTrue(Files.exists(src));
			assertTrue(Files.notExists(dst));
		}

		@DisplayName("expect copy to fail with FileNameTooLongException")
		@ParameterizedTest(name = "copy {0} -> this-cleartext-filename-is-longer-than-50-characters")
		@ValueSource(strings = {"/short-enough.txt", "/short-enough-dir", "/symlink.txt"})
		public void testCopyExceedingPathLengthLimit(String path) {
			Path src = fs.getPath(path);
			Path dst = fs.getPath("/this-cleartext-filename-is-longer-than-50-characters");
			assertThrows(FileNameTooLongException.class, () -> {
				Files.copy(src, dst, LinkOption.NOFOLLOW_LINKS);
			});
			assertTrue(Files.exists(src));
			assertTrue(Files.notExists(dst));
		}

	}

	@Nested
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
	public class InMemoryOrdered {

		private FileSystem tmpFs;
		private MasterkeyLoader keyLoader1;
		private MasterkeyLoader keyLoader2;
		private Path pathToVault1;
		private Path pathToVault2;
		private Path vaultConfigFile1;
		private Path vaultConfigFile2;
		private FileSystem fs1;
		private FileSystem fs2;

		@BeforeAll
		public void setup() throws IOException, MasterkeyLoadingFailedException {
			tmpFs = Jimfs.newFileSystem(Configuration.unix());
			byte[] key1 = new byte[64];
			byte[] key2 = new byte[64];
			Arrays.fill(key1, (byte) 0x55);
			Arrays.fill(key2, (byte) 0x77);
			keyLoader1 = Mockito.mock(MasterkeyLoader.class);
			keyLoader2 = Mockito.mock(MasterkeyLoader.class);
			Mockito.when(keyLoader1.loadKey(Mockito.any())).thenAnswer(ignored -> new Masterkey(key1));
			Mockito.when(keyLoader2.loadKey(Mockito.any())).thenAnswer(ignored -> new Masterkey(key2));
			pathToVault1 = tmpFs.getPath("/vaultDir1");
			pathToVault2 = tmpFs.getPath("/vaultDir2");
			Files.createDirectory(pathToVault1);
			Files.createDirectory(pathToVault2);
			vaultConfigFile1 = pathToVault1.resolve("vault.cryptomator");
			vaultConfigFile2 = pathToVault2.resolve("vault.cryptomator");
		}

		@AfterAll
		public void teardown() throws IOException {
			tmpFs.close();
		}

		@Test
		@Order(1)
		@DisplayName("initialize vaults")
		public void initializeVaults() {
			assertAll(() -> {
				var properties = CryptoFileSystemProperties.cryptoFileSystemProperties().withKeyLoader(keyLoader1).build();
				CryptoFileSystemProvider.initialize(pathToVault1, properties, URI.create("test:key"));
				assertTrue(Files.isDirectory(pathToVault1.resolve("d")));
				assertTrue(Files.isRegularFile(vaultConfigFile1));
			}, () -> {
				var properties = CryptoFileSystemProperties.cryptoFileSystemProperties().withKeyLoader(keyLoader2).build();
				CryptoFileSystemProvider.initialize(pathToVault2, properties, URI.create("test:key"));
				assertTrue(Files.isDirectory(pathToVault2.resolve("d")));
				assertTrue(Files.isRegularFile(vaultConfigFile2));
			});
		}

		@Test
		@Order(2)
		@DisplayName("get filesystem with incorrect credentials")
		public void testGetFsWithWrongCredentials() throws IOException {
			assumeTrue(CryptoFileSystemProvider.checkDirStructureForVault(pathToVault1, "vault.cryptomator", "masterkey.cryptomator") == DirStructure.VAULT);
			assumeTrue(CryptoFileSystemProvider.checkDirStructureForVault(pathToVault2, "vault.cryptomator", "masterkey.cryptomator") == DirStructure.VAULT);
			assertAll(() -> {
				URI fsUri = CryptoFileSystemUri.create(pathToVault1);
				CryptoFileSystemProperties properties = cryptoFileSystemProperties() //
						.withFlags() //
						.withMasterkeyFilename("masterkey.cryptomator") //
						.withKeyLoader(keyLoader2) //
						.build();
				assertThrows(VaultKeyInvalidException.class, () -> {
					FileSystems.newFileSystem(fsUri, properties);
				});
			}, () -> {
				URI fsUri = CryptoFileSystemUri.create(pathToVault2);
				CryptoFileSystemProperties properties = cryptoFileSystemProperties() //
						.withFlags() //
						.withMasterkeyFilename("masterkey.cryptomator") //
						.withKeyLoader(keyLoader1) //
						.build();
				assertThrows(VaultKeyInvalidException.class, () -> {
					FileSystems.newFileSystem(fsUri, properties);
				});
			});
		}

		@Test
		@Order(4)
		@DisplayName("get filesystem with correct credentials")
		public void testGetFsViaNioApi() {
			assumeTrue(Files.exists(vaultConfigFile1));
			assumeTrue(Files.exists(vaultConfigFile2));
			assertAll(() -> {
				URI fsUri = CryptoFileSystemUri.create(pathToVault1);
				fs1 = FileSystems.newFileSystem(fsUri, cryptoFileSystemProperties().withKeyLoader(keyLoader1).build());
				assertTrue(fs1 instanceof CryptoFileSystemImpl);

				FileSystem sameFs = FileSystems.getFileSystem(fsUri);
				assertSame(fs1, sameFs);
			}, () -> {
				URI fsUri = CryptoFileSystemUri.create(pathToVault2);
				fs2 = FileSystems.newFileSystem(fsUri, cryptoFileSystemProperties().withKeyLoader(keyLoader2).build());
				assertTrue(fs2 instanceof CryptoFileSystemImpl);

				FileSystem sameFs = FileSystems.getFileSystem(fsUri);
				assertSame(fs2, sameFs);
			});
		}

		@Test
		@Order(5)
		@DisplayName("touch /foo")
		public void testOpenAndCloseFileChannel() throws IOException {
			assumeTrue(fs1.isOpen());

			try (FileChannel ch = FileChannel.open(fs1.getPath("/foo"), EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW))) {
				assertTrue(ch instanceof CleartextFileChannel);
			}
		}

		@Test
		@Order(6)
		@DisplayName("ln -s foo /link")
		public void testCreateSymlink() {
			Path target = fs1.getPath("/foo");
			assumeTrue(Files.isRegularFile(target));
			Path link = fs1.getPath("/link");

			assertDoesNotThrow(() -> {
				Files.createSymbolicLink(link, target);
			});
		}

		@Test
		@Order(7)
		@DisplayName("echo 'hello world' > /link")
		public void testWriteToSymlink() throws IOException {
			Path link = fs1.getPath("/link");
			assumeTrue(Files.isSymbolicLink(link));

			assertDoesNotThrow(() -> {
				try (WritableByteChannel ch = Files.newByteChannel(link, StandardOpenOption.WRITE)) {
					ch.write(StandardCharsets.US_ASCII.encode("hello world"));
				}
			});
		}

		@Test
		@Order(7)
		@DisplayName("cat `readlink -f /link`")
		public void testReadFromSymlink() throws IOException {
			Path link = fs1.getPath("/link");
			assumeTrue(Files.isSymbolicLink(link));
			Path target = Files.readSymbolicLink(link);

			try (ReadableByteChannel ch = Files.newByteChannel(target, StandardOpenOption.READ)) {
				ByteBuffer buf = ByteBuffer.allocate(100);
				ch.read(buf);
				buf.flip();
				String str = StandardCharsets.US_ASCII.decode(buf).toString();
				assertEquals("hello world", str);
			}
		}

		@Test
		@Order(7)
		@DisplayName("cp /link /otherlink")
		public void testCopySymlinkSymlink() throws IOException {
			Path src = fs1.getPath("/link");
			Path dst = fs1.getPath("/otherlink");
			assumeTrue(Files.isSymbolicLink(src));
			assumeTrue(Files.notExists(dst));
			Files.copy(src, dst, LinkOption.NOFOLLOW_LINKS);
			assertTrue(Files.isSymbolicLink(src));
			assertTrue(Files.isSymbolicLink(dst));
		}

		@Test
		@Order(8)
		@DisplayName("rm /link")
		public void testRemoveSymlink() throws IOException {
			Path link = fs1.getPath("/link");
			assumeTrue(Files.isSymbolicLink(link));

			assertDoesNotThrow(() -> {
				Files.delete(link);
			});
		}

		@Test
		@Order(8)
		@DisplayName("rm /otherlink")
		public void testRemoveOtherSymlink() throws IOException {
			Path link = fs1.getPath("/otherlink");
			assumeTrue(Files.isSymbolicLink(link));

			assertDoesNotThrow(() -> {
				Files.delete(link);
			});
		}

		@Test
		@Order(9)
		@DisplayName("ln -s foo '/Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet'")
		public void testCreateSymlinkWithLongName() throws IOException {
			Path target = fs1.getPath("/foo");
			assumeTrue(Files.isRegularFile(target));
			Path longNameLink = fs1.getPath("/Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet");
			Files.createSymbolicLink(longNameLink, target);
			MatcherAssert.assertThat(MoreFiles.listFiles(fs1.getPath("/")), Matchers.hasItem(longNameLink));
			assertTrue(Files.exists(longNameLink));
		}

		@Test
		@Order(10)
		@DisplayName("mv '/Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet' '/Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat")
		public void testMoveSymlinkWithLongNameToAnotherLongName() throws IOException {
			Path longNameSource = fs1.getPath("/Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet");
			assumeTrue(Files.isSymbolicLink(longNameSource));
			Path longNameTarget = longNameSource.resolveSibling("/Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat");
			Files.move(longNameSource, longNameTarget);
			assertTrue(Files.exists(longNameTarget));
			assertTrue(Files.notExists(longNameSource));
		}

		@Test
		@Order(11)
		@DisplayName("rm -r '/Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat'")
		public void testRemoveSymlinkWithLongName() throws IOException {
			Path longNamePath = fs1.getPath("/Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat");
			Files.delete(longNamePath);
			assertTrue(Files.notExists(longNamePath));
		}

		@Test
		@Order(12)
		@DisplayName("mkdir '/Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet'")
		public void testCreateDirWithLongName() throws IOException {
			Path longNamePath = fs1.getPath("/Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet");
			Files.createDirectory(longNamePath);
			assertTrue(Files.isDirectory(longNamePath));
			MatcherAssert.assertThat(MoreFiles.listFiles(fs1.getPath("/")), Matchers.hasItem(longNamePath));
		}

		@Test
		@Order(13)
		@DisplayName("mv '/Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet' '/Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat")
		public void testMoveDirWithLongNameToAnotherLongName() throws IOException {
			Path longNameSource = fs1.getPath("/Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet");
			Path longNameTarget = longNameSource.resolveSibling("/Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat");
			Files.move(longNameSource, longNameTarget);
			assertTrue(Files.exists(longNameTarget));
			assertTrue(Files.notExists(longNameSource));
		}

		@Test
		@Order(14)
		@DisplayName("rm -r '/Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat'")
		public void testRemoveDirWithLongName() throws IOException {
			Path longNamePath = fs1.getPath("/Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat");
			Files.delete(longNamePath);
			assertTrue(Files.notExists(longNamePath));
		}

		@Test
		@Order(15)
		@DisplayName("touch '/Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet'")
		public void testCreateFileWithLongName() throws IOException {
			Path longNamePath = fs1.getPath("/Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet");
			Files.createFile(longNamePath);
			assertTrue(Files.isRegularFile(longNamePath));
			MatcherAssert.assertThat(MoreFiles.listFiles(fs1.getPath("/")), Matchers.hasItem(longNamePath));
		}

		@Test
		@Order(16)
		@DisplayName("mv '/Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet' '/Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat")
		public void testMoveFileWithLongNameToAnotherLongName() throws IOException {
			Path longNameSource = fs1.getPath("/Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet Telefon Energie Wasser Webseitengeraffel Bus Bahn Mietwagen Internet");
			Path longNameTarget = longNameSource.resolveSibling("/Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat");
			Files.move(longNameSource, longNameTarget);
			assertTrue(Files.exists(longNameTarget));
			assertTrue(Files.notExists(longNameSource));
		}

		@Test
		@Order(17)
		@DisplayName("rm -r '/Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat'")
		public void testRemoveFileWithLongName() throws IOException {
			Path longNamePath = fs1.getPath("/Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat Talafan Anargaa Wassar Wabsaatangaraffal Bas Bahn Maatwagan Antarnat");
			Files.delete(longNamePath);
			assertTrue(Files.notExists(longNamePath));
		}

		@Test
		@Order(18)
		@DisplayName("cp fs1:/foo fs2:/bar")
		public void testCopyFileAcrossFilesystem() throws IOException {
			Path file1 = fs1.getPath("/foo");
			Path file2 = fs2.getPath("/bar");
			assumeTrue(Files.isRegularFile(file1));
			assumeTrue(Files.notExists(file2));

			Files.copy(file1, file2);

			assertArrayEquals(readAllBytes(file1), readAllBytes(file2));
		}

		@Test
		@Order(19)
		@DisplayName("echo 'goodbye world' > /foo")
		public void testWriteToFile() throws IOException {
			Path file1 = fs1.getPath("/foo");
			assumeTrue(Files.isRegularFile(file1));

			assertDoesNotThrow(() -> {
				Files.write(file1, "goodbye world".getBytes());
			});
		}

		@Test
		@Order(20)
		@DisplayName("cp -f fs1:/foo fs2:/bar")
		public void testCopyFileAcrossFilesystemReplaceExisting() throws IOException {
			Path file1 = fs1.getPath("/foo");
			Path file2 = fs2.getPath("/bar");
			assumeTrue(Files.isRegularFile(file1));
			assumeTrue(Files.isRegularFile(file2));

			Files.copy(file1, file2, REPLACE_EXISTING);

			assertArrayEquals(readAllBytes(file1), readAllBytes(file2));
		}

		@Test
		@Order(21)
		@DisplayName("readattr /attributes.txt")
		public void testLazinessOfFileAttributeViews() throws IOException {
			Path file = fs1.getPath("/attributes.txt");
			assumeTrue(Files.notExists(file));

			BasicFileAttributeView attrView = Files.getFileAttributeView(file, BasicFileAttributeView.class);
			assertNotNull(attrView);
			assertThrows(NoSuchFileException.class, () -> {
				attrView.readAttributes();
			});

			Files.write(file, new byte[3], StandardOpenOption.CREATE_NEW);
			BasicFileAttributes attrs = attrView.readAttributes();
			assertNotNull(attrs);
			assertEquals(3, attrs.size());

			Files.delete(file);
			assertThrows(NoSuchFileException.class, () -> {
				attrView.readAttributes();
			});
			assertEquals(3, attrs.size()); // attrs should be immutable once they are read.
		}

		@Test
		@Order(22)
		@DisplayName("ln -s /linked/targetY /links/linkX")
		public void testSymbolicLinks() throws IOException {
			Path linksDir = fs1.getPath("/links");
			assumeTrue(Files.notExists(linksDir));
			Files.createDirectories(linksDir);

			assertAll(() -> {
				Path link = linksDir.resolve("link1");
				Files.createDirectories(link.getParent());
				Files.createSymbolicLink(link, fs1.getPath("/linked/target1"));
				Path target = Files.readSymbolicLink(link);
				MatcherAssert.assertThat(target.getFileSystem(), is(link.getFileSystem())); // as per contract of readSymbolicLink
				MatcherAssert.assertThat(target.toString(), Matchers.equalTo("/linked/target1"));
				MatcherAssert.assertThat(link.resolveSibling(target).toString(), Matchers.equalTo("/linked/target1"));
			}, () -> {
				Path link = linksDir.resolve("link2");
				Files.createDirectories(link.getParent());
				Files.createSymbolicLink(link, fs1.getPath("./target2"));
				Path target = Files.readSymbolicLink(link);
				MatcherAssert.assertThat(target.getFileSystem(), is(link.getFileSystem()));
				MatcherAssert.assertThat(target.toString(), Matchers.equalTo("./target2"));
				MatcherAssert.assertThat(link.resolveSibling(target).normalize().toString(), Matchers.equalTo("/links/target2"));
			}, () -> {
				Path link = linksDir.resolve("link3");
				Files.createDirectories(link.getParent());
				Files.createSymbolicLink(link, fs1.getPath("../target3"));
				Path target = Files.readSymbolicLink(link);
				MatcherAssert.assertThat(target.getFileSystem(), is(link.getFileSystem()));
				MatcherAssert.assertThat(target.toString(), Matchers.equalTo("../target3"));
				MatcherAssert.assertThat(link.resolveSibling(target).normalize().toString(), Matchers.equalTo("/target3"));
			});
		}

		@Test
		@Order(22)
		@DisplayName("mv -f fs1:/foo fs2:/baz")
		public void testMoveFileFromOneCryptoFileSystemToAnother() throws IOException {
			Path file1 = fs1.getPath("/foo");
			Path file2 = fs2.getPath("/baz");
			assumeTrue(Files.isRegularFile(file1));
			assumeTrue(Files.notExists(file2));
			byte[] contents = readAllBytes(file1);

			Files.move(file1, file2);

			assertTrue(Files.notExists(file1));
			assertTrue(Files.isRegularFile(file2));
			assertArrayEquals(contents, readAllBytes(file2));
		}

	}

	@Nested
	@EnabledOnOs({OS.MAC, OS.LINUX})
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	@DisplayName("On POSIX Systems")
	public class PosixTests {

		private FileSystem fs;

		@BeforeAll
		public void setup(@TempDir Path tmpDir) throws IOException, MasterkeyLoadingFailedException {
			Path pathToVault = tmpDir.resolve("vaultDir1");
			Files.createDirectories(pathToVault);
			MasterkeyLoader keyLoader = Mockito.mock(MasterkeyLoader.class);
			Mockito.when(keyLoader.loadKey(Mockito.any())).thenAnswer(ignored -> new Masterkey(new byte[64]));
			var properties = CryptoFileSystemProperties.cryptoFileSystemProperties().withKeyLoader(keyLoader).build();
			CryptoFileSystemProvider.initialize(pathToVault, properties, URI.create("test:key"));
			fs = CryptoFileSystemProvider.newFileSystem(pathToVault, properties);
		}

		@Nested
		@DisplayName("File Locks")
		public class FileLockTests {

			private Path file = fs.getPath("/lock.txt");

			@BeforeEach
			public void setup() throws IOException {
				Files.write(file, new byte[100000]); // > 3 * 32k
			}

			@Test
			@DisplayName("get shared lock on non-readable channel fails")
			public void testGetSharedLockOnNonReadableChannel() throws IOException {
				try (FileChannel ch = FileChannel.open(file, StandardOpenOption.WRITE)) {
					assertThrows(NonReadableChannelException.class, () -> {
						ch.lock(0, 50000, true);
					});
				}
			}

			@Test
			@DisplayName("locking a closed channel fails")
			public void testLockClosedChannel() throws IOException {
				FileChannel ch = FileChannel.open(file, StandardOpenOption.WRITE);
				ch.close();
				assertThrows(ClosedChannelException.class, () -> {
					ch.lock();
				});
			}

			@Test
			@DisplayName("get exclusive lock on non-writable channel fails")
			public void testGetSharedLockOnNonWritableChannel() throws IOException {
				try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
					assertThrows(NonWritableChannelException.class, () -> {
						ch.lock(0, 50000, false);
					});
				}
			}

			@ParameterizedTest(name = "shared = {0}")
			@CsvSource({"true", "false"})
			@DisplayName("create non-overlapping locks")
			public void testNonOverlappingLocks(boolean shared) throws IOException {
				try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
					try (FileLock lock1 = ch.lock(0, 10000, shared)) {
						try (FileLock lock2 = ch.lock(90000, 10000, shared)) {
							assertNotSame(lock1, lock2);
						}
					}
				}
			}

			@ParameterizedTest(name = "shared = {0}")
			@CsvSource({"true", "false"})
			@DisplayName("create overlapping locks")
			public void testOverlappingLocks(boolean shared) throws IOException {
				try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
					try (FileLock lock1 = ch.lock(0, 10000, shared)) {
						// while bock locks cover different cleartext byte ranges, it is necessary to lock the same ciphertext block
						assertThrows(OverlappingFileLockException.class, () -> {
							ch.lock(10000, 10000, shared);
						});
					}
				}
			}

		}


	}

	@Nested
	@EnabledOnOs(OS.WINDOWS)
	@TestInstance(TestInstance.Lifecycle.PER_CLASS)
	@DisplayName("On Windows Systems")
	public class WindowsTests {

		private FileSystem fs;

		@BeforeAll
		public void setup(@TempDir Path tmpDir) throws IOException, MasterkeyLoadingFailedException {
			Path pathToVault = tmpDir.resolve("vaultDir1");
			Files.createDirectories(pathToVault);
			MasterkeyLoader keyLoader = Mockito.mock(MasterkeyLoader.class);
			Mockito.when(keyLoader.loadKey(Mockito.any())).thenAnswer(ignored -> new Masterkey(new byte[64]));
			var properties = CryptoFileSystemProperties.cryptoFileSystemProperties().withKeyLoader(keyLoader).build();
			CryptoFileSystemProvider.initialize(pathToVault, properties, URI.create("test:key"));
			fs = CryptoFileSystemProvider.newFileSystem(pathToVault, properties);
		}

		@Test
		@DisplayName("set dos attributes")
		public void testDosFileAttributes() throws IOException {
			Path file = fs.getPath("/msDosAttributes.txt");
			assumeTrue(Files.notExists(file));

			Files.write(file, new byte[1]);

			Files.setAttribute(file, "dos:hidden", true);
			Files.setAttribute(file, "dos:system", true);
			Files.setAttribute(file, "dos:archive", true);
			Files.setAttribute(file, "dos:readOnly", true);

			assertEquals(true, Files.getAttribute(file, "dos:hidden"));
			assertEquals(true, Files.getAttribute(file, "dos:system"));
			assertEquals(true, Files.getAttribute(file, "dos:archive"));
			assertEquals(true, Files.getAttribute(file, "dos:readOnly"));

			Files.setAttribute(file, "dos:hidden", false);
			Files.setAttribute(file, "dos:system", false);
			Files.setAttribute(file, "dos:archive", false);
			Files.setAttribute(file, "dos:readOnly", false);

			assertEquals(false, Files.getAttribute(file, "dos:hidden"));
			assertEquals(false, Files.getAttribute(file, "dos:system"));
			assertEquals(false, Files.getAttribute(file, "dos:archive"));
			assertEquals(false, Files.getAttribute(file, "dos:readOnly"));
		}

		@Nested
		@DisplayName("read-only file")
		public class OnReadOnlyFile {

			private Path file = fs.getPath("/readonly.txt");
			private DosFileAttributeView attrView;

			@BeforeEach
			public void setup() throws IOException {
				Files.write(file, new byte[1]);

				attrView = Files.getFileAttributeView(file, DosFileAttributeView.class);
				attrView.setReadOnly(true);
			}

			@AfterEach
			public void tearDown() throws IOException {
				attrView.setReadOnly(false);
			}

			@Test
			@DisplayName("is not writable")
			public void testNotWritable() {
				assertThrows(AccessDeniedException.class, () -> {
					FileChannel.open(file, StandardOpenOption.WRITE);
				});
			}

			@Test
			@DisplayName("is readable")
			public void testReadable() throws IOException {
				try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
					assertEquals(1, ch.size());
				}
			}

			@Test
			@DisplayName("can be made read-write accessible")
			public void testFoo() throws IOException {
				attrView.setReadOnly(false);
				try (FileChannel ch = FileChannel.open(file, StandardOpenOption.WRITE)) {
					assertEquals(1, ch.size());
				}
			}

		}


	}

}
