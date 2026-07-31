package net.submedia.android.uqmlivewallpaper;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.quality.Strictness;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public abstract class BaseTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule().strictness(Strictness.LENIENT);

    @Mock
    protected Context mockContext;
    @Mock
    protected Resources mockResources;
    @Mock
    protected AssetManager mockAssetManager;

    protected final Random rand = new Random();
    protected MockedStatic<Log> mockedStaticLog;
    public record Coord(int x, int y) {}

    protected MockedStatic<BitmapFactory> mockedStaticBitmapFactory;
    private static final String alphanum = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_";

    @Before
    public void setUp() throws Exception {
        // Mock Log to prevent crashes from Android's Log class
        mockedStaticLog = mockStatic(Log.class);
        mockedStaticLog.when(() -> Log.d(anyString(), anyString())).thenReturn(0);
        mockedStaticLog.when(() -> Log.i(anyString(), anyString())).thenReturn(0);
        mockedStaticLog.when(() -> Log.w(anyString(), anyString())).thenReturn(0);
        mockedStaticLog.when(() -> Log.e(anyString(), anyString())).thenReturn(0);
        mockedStaticLog.when(() -> Log.v(anyString(), anyString())).thenReturn(0);
        mockedStaticLog.when(() -> Log.isLoggable(anyString(), anyInt())).thenReturn(true);

        lenient().when(mockContext.getAssets()).thenReturn(mockAssetManager);
        lenient().when(mockContext.getResources()).thenReturn(mockResources);
    }

    @After
    public void tearDown() throws Exception {
        if (mockedStaticLog != null) {
            mockedStaticLog.close();
            mockedStaticLog = null;
        }
        if (mockedStaticBitmapFactory != null) {
            mockedStaticBitmapFactory.close();
            mockedStaticBitmapFactory = null;
        }
    }

    protected String createString(int length, String charset) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int index = rand.nextInt(charset.length());
            char randomChar = charset.charAt(index);
            sb.append(randomChar);
        }
        return sb.toString();
    }

    protected String createString(int length) {
        return createString(length, alphanum);
    }

    protected String createString() {
        return createString(10, alphanum);
    }

    protected byte[] createZipArchiveBytes(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream archive = new ZipArchiveOutputStream(bos)) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                ZipArchiveEntry zipEntry = new ZipArchiveEntry(entry.getKey());
                archive.putArchiveEntry(zipEntry);
                archive.write(entry.getValue());
                archive.closeArchiveEntry();
            }
            archive.finish();
        }
        return bos.toByteArray();
    }

    protected byte[] createAniContent(List<String> fileNames) {
        StringBuilder sb = new StringBuilder();
        for (String fileName : fileNames) sb.append(fileName).append(System.lineSeparator());
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    protected byte[] createPngContent() {
        return new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    }

    public static boolean isMethodStubbed(Object mock, String methodName) {
        var details = Mockito.mockingDetails(mock);
        return details.isMock() &&
                details.getStubbings().stream().anyMatch(
                        s -> s.getInvocation().getMethod().getName().equals(methodName)
                );

    }

    /**
     * A helper fixture for setting up common mocks and creating a Content object.
     * This encapsulates the boilerplate required to instantiate Content for testing.
     */
    protected class ContentFixture {
        public final String uqmZipFileName;
        private String contentBaseDir;

        public record Params(BaseTest testInstance, String alienRace, String pngFilename, String aniFilename, Coord hotspot, Coord size) {}
        public Params params;

        public String alienRace;
        public String pngFilename;
        public String aniFilename;
        public Coord hotspot;
        public Coord size;
        private int frameCount = 1;
        private final Map<String, byte[]> zipContents = new HashMap<>();
        private boolean skipDefaultContent = false;
        protected AssetFileDescriptor mockAfd;
        private byte[] zipData;

        public ContentFixture() {
            this.uqmZipFileName = createString() + ".uqm";
        }

        public ContentFixture setAlienRace(String alienRace) {
            this.alienRace = alienRace;
            return this;
        }

        public ContentFixture setPngFilename(String pngFilename) {
            this.pngFilename = pngFilename;
            return this;
        }

        public ContentFixture setAniFilename(String aniFilename) {
            this.aniFilename = aniFilename;
            return this;
        }

        public ContentFixture setHotspot(Coord hotspot) {
            this.hotspot = hotspot;
            return this;
        }

        public ContentFixture setSize(Coord size) {
            this.size = size;
            return this;
        }

        public ContentFixture setFrameCount(int frameCount) {
            this.frameCount = frameCount;
            return this;
        }

        public ContentFixture skipDefaultContent() {
            this.skipDefaultContent = true;
            return this;
        }

        public void createAndSetZipFile(Map<String, byte[]> zipContents) {
            this.zipContents.clear();
            this.zipContents.putAll(zipContents);
        }

        // NOTE(nic): previous versions did not assume a relationship between the alienRace and
        //  the aniFilename, the current code expects them to match; check the commit history if
        //  you'd like to decouple them, and want the fixture to match
        public void setup(BaseTest testInstance) throws IOException {
            if (alienRace == null) alienRace = createString();
            if (aniFilename == null) aniFilename = alienRace + ".ani";
            if (pngFilename == null) pngFilename = createString() + ".png";
            if (hotspot == null) hotspot = new Coord(rand.nextInt(-100, 100), rand.nextInt(-100, 100));
            if (size == null) size = new Coord(rand.nextInt(320), rand.nextInt(240));

            contentBaseDir = "base/comm/%s/".formatted(alienRace);
            StringBuilder aniFileContentBuilder = new StringBuilder();

            if (!skipDefaultContent) {
                // Add default frames to zipContents if not already present
                for (int i = 0; i < frameCount; i++) {
                    String currentPng = frameCount == 1 ? pngFilename : pngFilename.replace(".png", "_" + i + ".png");
                    String line = "%s %d %d %d %d".formatted(currentPng, testInstance.rand.nextInt(-100, 100), testInstance.rand.nextInt(-100, 100), hotspot.x(), hotspot.y());
                    aniFileContentBuilder.append(line).append("\n");
                    zipContents.putIfAbsent(contentBaseDir + currentPng, testInstance.createPngContent());
                }
                zipContents.putIfAbsent(contentBaseDir + aniFilename, aniFileContentBuilder.toString().getBytes(StandardCharsets.UTF_8));
            }

            this.zipData = createZipArchiveBytes(zipContents);
            this.mockAfd = mock(AssetFileDescriptor.class);
            lenient().when(this.mockAfd.getFileDescriptor()).thenReturn(new FileDescriptor());
            lenient().when(this.mockAfd.getStartOffset()).thenReturn(0L);
            lenient().when(this.mockAfd.getLength()).thenReturn((long) zipData.length);

            if (mockedStaticBitmapFactory == null) {
                mockedStaticBitmapFactory = Mockito.mockStatic(BitmapFactory.class);
                Bitmap mockBitmap = mock(Bitmap.class);
                lenient().when(mockBitmap.getWidth()).thenReturn(size.x());
                lenient().when(mockBitmap.getHeight()).thenReturn(size.y());
                lenient().when(mockBitmap.getConfig()).thenReturn(Bitmap.Config.RGB_565);
                lenient().when(mockBitmap.copy(Mockito.any(Bitmap.Config.class), Mockito.any(Boolean.class))).thenReturn(mock(Bitmap.class));

                mockedStaticBitmapFactory.when(() -> BitmapFactory.decodeStream(Mockito.any(InputStream.class), isNull(), Mockito.any(BitmapFactory.Options.class))).thenReturn(mockBitmap);
            }

            lenient().when(testInstance.mockAssetManager.list(eq(""))).thenReturn(new String[]{uqmZipFileName});
            if (!isMethodStubbed(mockAssetManager, "list"))
                lenient().when(testInstance.mockAssetManager.list(anyString())).thenReturn(new String[]{uqmZipFileName});

            lenient().when(testInstance.mockAssetManager.openFd(uqmZipFileName)).thenReturn(mockAfd);

            this.params = new Params(testInstance, alienRace, pngFilename, aniFilename, hotspot, size);
        }

        public Content build(BaseTest testInstance) throws IOException {
            String race = (alienRace != null) ? alienRace : ((params != null) ? params.alienRace() : "unknown");
            final byte[] data = this.zipData;
            return new Content(new String[]{race}, testInstance.mockContext, () -> false) {
                @Override
                protected ZipFile setupContent(String zipfile, Context c) throws IOException {
                    return ZipFile.builder()
                            .setSeekableByteChannel(new SeekableInMemoryByteChannel(data))
                            .get();
                }
            };
        }

        public Content buildContent(BaseTest testInstance) throws IOException {
            setup(testInstance);
            return build(testInstance);
        }

        public String getContentBaseDir() {
            return contentBaseDir;
        }
    }
}
